package com.crag.agent.extract;

import com.crag.agent.model.ChatModelClient;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.model.ChatRequest;
import com.crag.agent.model.ChatResponse;
import com.crag.agent.model.ModelCallPurposeEnum;
import com.crag.agent.prompt.PromptRegistry;
import com.crag.agent.prompt.PromptTemplate;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.common.text.ClauseTypeEnum;
import com.crag.common.text.TextNormalizer;
import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.doc.application.port.ContractExtractionRepository;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.doc.domain.model.ContractExtractionRecord;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.doc.domain.model.ExtractStatusEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 合同结构化抽取（规范 7.4、7.5、12.3）。
 *
 * <p>调用链：{@code ReviewController.extract} → 本类 → doc 只读端口（合同、切片）→ {@link ChatModelClient}
 * → {@link ExtractionValidator}（结构校验）→ 原文回验 → {@link ContractExtractionRepository}（落库，事务在 doc 适配器内）。
 * 模型调用在事务外完成（规范 3.8）。</p>
 *
 * <p>输入裁剪：只取与抽取字段相关的条款类型（付款/管辖/违约/保密/其他），最多
 * {@code crag.model.max-context-chunks} 段、每段不超过 {@code max-context-chars} 字，禁止送合同全文（规范 7.6）。</p>
 *
 * <p>失败语义：模型降级或连续两次输出格式非法 → 状态 DEGRADED，不写任何字段值，只保留原始返回供排查；
 * 字段有值但片段缺失/回验失败 → 状态 PENDING_MANUAL，该字段值不落库（规则不会用它），并计入回验统计（规范 7.4、7.5）。</p>
 */
@Service
public class ContractExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ContractExtractionService.class);

    private static final String PROMPT_NAME = "contract-extract";
    private static final int MAX_RAW_RESPONSE_CHARS = 8_000;
    private static final Set<ClauseTypeEnum> EXTRACT_TYPES = EnumSet.of(ClauseTypeEnum.PAYMENT,
            ClauseTypeEnum.JURISDICTION, ClauseTypeEnum.BREACH, ClauseTypeEnum.CONFIDENTIALITY,
            ClauseTypeEnum.OTHER);

    private final ContractRepository contractRepository;
    private final ContractChunkRepository chunkRepository;
    private final ContractExtractionRepository extractionRepository;
    private final ChatModelClient chatModelClient;
    private final ChatModelProperties modelProperties;
    private final PromptRegistry promptRegistry;
    private final ExtractionValidator validator;
    private final ExtractionVerifier verifier;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock clock;

    /**
     * 构造服务。
     *
     * @param contractRepository    合同只读端口
     * @param chunkRepository       切片只读端口
     * @param extractionRepository  抽取结果仓储
     * @param chatModelClient       模型客户端
     * @param modelProperties       模型配置
     * @param promptRegistry        Prompt 注册中心
     * @param validator             输出校验器
     * @param verifier              原文回验器
     * @param objectMapper          JSON 序列化
     * @param idGenerator           雪花标识
     * @param clock                 统一时钟
     */
    public ContractExtractionService(ContractRepository contractRepository,
                                     ContractChunkRepository chunkRepository,
                                     ContractExtractionRepository extractionRepository,
                                     ChatModelClient chatModelClient,
                                     ChatModelProperties modelProperties,
                                     PromptRegistry promptRegistry,
                                     ExtractionValidator validator,
                                     ExtractionVerifier verifier,
                                     ObjectMapper objectMapper,
                                     SnowflakeIdGenerator idGenerator,
                                     Clock clock) {
        this.contractRepository = contractRepository;
        this.chunkRepository = chunkRepository;
        this.extractionRepository = extractionRepository;
        this.chatModelClient = chatModelClient;
        this.modelProperties = modelProperties;
        this.promptRegistry = promptRegistry;
        this.validator = validator;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 抽取并落库。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param operatorId 操作人
     * @return 抽取结果
     */
    public ExtractionResult extract(long tenantId, long contractId, long operatorId) {
        ContractRecord contract = contractRepository.findById(tenantId, contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "contract " + contractId));
        List<ContractChunkRecord> context = selectContext(chunkRepository.findByContractId(tenantId, contractId));
        if (context.isEmpty()) {
            ExtractionResult empty = degradedResult(null, null, "no_clause_for_extract", "无可用条款", 0);
            persist(tenantId, contract.id(), operatorId, empty);
            return empty;
        }
        String userPrompt = buildUserPrompt(contract, context);
        PromptTemplate prompt = promptRegistry.get(PROMPT_NAME);

        ChatResponse response = null;
        ExtractionValidator.ExtractionCandidate candidate = null;
        String failure = null;
        int tokens = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            response = chatModelClient.complete(new ChatRequest(tenantId, ModelCallPurposeEnum.EXTRACT,
                    prompt.version(), prompt.systemPrompt(), userPrompt, contractId, null,
                    modelProperties.getMaxOutputTokens()));
            tokens += response.totalTokens();
            if (response.degraded()) {
                failure = response.degradeReason();
                break;
            }
            try {
                candidate = validator.parse(response.content());
                break;
            } catch (ExtractionFormatException e) {
                failure = "invalid_output";
                log.warn("抽取输出不合法，重试一次 contractId={} attempt={} reason={}", contractId, attempt + 1,
                        e.getMessage());
            }
        }

        ExtractionResult result;
        if (candidate == null) {
            result = degradedResult(prompt.version(), response == null ? null : response.model(),
                    failure == null ? "unknown" : failure, response == null ? null : response.content(), tokens);
        } else {
            result = verify(candidate, context, response, prompt.version(), tokens);
        }
        persist(tenantId, contract.id(), operatorId, result);
        log.info("抽取完成 contractId={} status={} values={} verified={} degraded={}", contractId, result.status(),
                result.valueCount(), result.verifiedCount(), result.degraded());
        return result;
    }

    /**
     * 查询已保存的抽取结果。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 记录
     */
    public java.util.Optional<ContractExtractionRecord> find(long tenantId, long contractId) {
        return extractionRepository.findByContract(tenantId, contractId);
    }

    private List<ContractChunkRecord> selectContext(List<ContractChunkRecord> chunks) {
        List<ContractChunkRecord> selected = new ArrayList<>();
        for (ContractChunkRecord chunk : chunks) {
            if (!EXTRACT_TYPES.contains(chunk.clauseType())) {
                continue;
            }
            selected.add(chunk);
            if (selected.size() >= modelProperties.getMaxContextChunks()) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private String buildUserPrompt(ContractRecord contract, List<ContractChunkRecord> context) {
        ArrayNode clauses = objectMapper.createArrayNode();
        for (ContractChunkRecord chunk : context) {
            ObjectNode node = clauses.addObject();
            node.put("clauseNo", chunk.clauseNo());
            node.put("clauseType", chunk.clauseType().name());
            node.put("text", truncate(chunk.chunkText(), modelProperties.getMaxContextChars()));
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractName", contract.name());
        root.set("clauses", clauses);
        root.put("instruction", "只依据 clauses 中的原文抽取；每段原文可能被截断，片段必须逐字出现在其中。");
        return root.toString();
    }

    private ExtractionResult verify(ExtractionValidator.ExtractionCandidate candidate,
                                    List<ContractChunkRecord> context, ChatResponse response, String promptVersion,
                                    int tokens) {
        StringBuilder corpus = new StringBuilder();
        for (ContractChunkRecord chunk : context) {
            corpus.append(chunk.chunkText()).append('\n');
        }
        return verifier.verify(candidate, corpus.toString(), promptVersion, response.model(),
                truncate(response.content(), MAX_RAW_RESPONSE_CHARS), tokens);
    }

    private ExtractionResult degradedResult(String promptVersion, String modelName, String reason, String raw,
                                            int tokens) {
        List<ExtractionField> fields = new ArrayList<>();
        for (String name : ExtractionValidator.ExtractionCandidate.fieldNames()) {
            fields.add(new ExtractionField(name, null, null, false, reason));
        }
        return new ExtractionResult(null, null, null, null, ExtractStatusEnum.DEGRADED, fields,
                verifier.toSpanJson(fields), truncate(raw, MAX_RAW_RESPONSE_CHARS), promptVersion, modelName, true,
                reason, tokens);
    }

    private void persist(long tenantId, long contractId, long operatorId, ExtractionResult result) {
        java.time.Instant now = clock.instant();
        extractionRepository.saveOrUpdate(new ContractExtractionRecord(idGenerator.nextId(), tenantId, contractId,
                result.contractAmount(), result.paymentDays(), result.jurisdiction(), result.hasJointLiability(),
                result.status(), result.modelRawResponse(), result.sourceSpanJson(), result.promptVersion(),
                result.modelName(), now, now));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
