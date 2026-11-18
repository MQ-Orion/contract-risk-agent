package com.crag.agent.review;

import com.crag.agent.extract.ContractExtractionService;
import com.crag.agent.extract.ExtractionResult;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.rule.application.RuleCache;
import com.crag.agent.rule.application.RuleEvaluationResult;
import com.crag.common.domain.RiskLevelEnum;
import com.crag.agent.rule.domain.RuleContext;
import com.crag.agent.rule.domain.RuleMatch;
import com.crag.agent.tool.ReportExportResult;
import com.crag.agent.tool.RuleCheckTool;
import com.crag.agent.tool.ToolOutcome;
import com.crag.agent.tool.ToolRegistry;
import com.crag.agent.tool.RiskRetrievalTool;
import com.crag.agent.tool.ReportExportTool;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.kb.domain.model.RetrievedChunk;
import com.crag.retrieval.domain.model.RetrievalResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 初审编排（规范 7.2、8.4、9.2、9.3）。
 *
 * <p>执行顺序固定：规则校验 → 语义类条款检索风控依据 → 模型判断 → 汇总 → 报告导出。模型不能跳过规则；
 * 规则结论必须进入最终清单，不允许被模型覆盖（规范 7.2、8.4）。</p>
 *
 * <p>上限：单条款最多 3 次工具调用（规则、检索、模型各一次）；单合同 ≤ 条款数×3+5；整体超过 20 分钟转人工。
 * 达到上限时强制收敛并输出已有结论，禁止无限循环（规范 7.2）。</p>
 *
 * <p>冲突处理：规则与模型结论不一致时以规则为准，但报告必须同时呈现双方结论与差异说明；
 * 模型单独判定的高风险一律降级为中风险并标注“待人工复核”，因为高风险不得仅由模型判定（规范 7.5、8.4）。</p>
 *
 * <p>本编排不开启事务：模型调用在事务外，落库由各仓储在短事务内完成（规范 3.8）。</p>
 */
public class ContractReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ContractReviewOrchestrator.class);

    /** 单条款工具调用上限（规范 7.2）。 */
    private static final int MAX_TOOL_CALLS_PER_CLAUSE = 3;
    /** 单合同整体超时（分钟），超时转人工（规范 7.2）。 */
    private static final int DEADLINE_MINUTES = 20;

    private final ContractRepository contractRepository;
    private final ContractChunkRepository chunkRepository;
    private final ContractExtractionService extractionService;
    private final ToolRegistry toolRegistry;
    private final ClauseRiskService clauseRiskService;
    private final RuleCache ruleCache;
    private final ChatModelProperties modelProperties;
    private final Clock clock;
    private final int maxModelClauses;
    private final ObjectMapper objectMapper;

    /**
     * 构造编排器。
     *
     * @param contractRepository 合同只读端口
     * @param chunkRepository    切片只读端口
     * @param extractionService  抽取服务
     * @param toolRegistry       工具注册中心
     * @param clauseRiskService  条款风险判断
     * @param ruleCache          规则缓存（取版本）
     * @param modelProperties    模型配置（令牌预算）
     * @param clock              统一时钟
     * @param maxModelClauses    单合同最多调用模型的条款数（控制成本与延迟）
     */
    public ContractReviewOrchestrator(ContractRepository contractRepository,
                                      ContractChunkRepository chunkRepository,
                                      ContractExtractionService extractionService,
                                      ToolRegistry toolRegistry,
                                      ClauseRiskService clauseRiskService,
                                      RuleCache ruleCache,
                                      ChatModelProperties modelProperties,
                                      Clock clock,
                                      int maxModelClauses,
                                      ObjectMapper objectMapper) {
        this.contractRepository = contractRepository;
        this.chunkRepository = chunkRepository;
        this.extractionService = extractionService;
        this.toolRegistry = toolRegistry;
        this.clauseRiskService = clauseRiskService;
        this.ruleCache = ruleCache;
        this.modelProperties = modelProperties;
        this.clock = clock;
        this.maxModelClauses = maxModelClauses;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行初审预演。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param operatorId 操作人
     * @return 预演结果
     */
    public ReviewOutcome review(long tenantId, long contractId, long operatorId) {
        return review(tenantId, contractId, operatorId, false);
    }

    /**
     * 执行初审预演。
     *
     * <p>{@code rulesOnly=true} 等价于“熔断期间的纯规则模式”（规范 7.6）：跳过检索与模型，只跑规则并出报告。
     * 该模式也用于规则热更新的快速验证，避免每次都消耗模型额度。</p>
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param operatorId 操作人
     * @param rulesOnly  是否仅规则模式
     * @return 预演结果
     */
    public ReviewOutcome review(long tenantId, long contractId, long operatorId, boolean rulesOnly) {
        Instant startedAt = clock.instant();
        Instant deadline = startedAt.plus(Duration.ofMinutes(DEADLINE_MINUTES));
        ContractRecord contract = contractRepository.findById(tenantId, contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "contract " + contractId));
        List<ContractChunkRecord> chunks = chunkRepository.findByContractId(tenantId, contractId);
        if (chunks.isEmpty()) {
            throw BizException.of(ErrorCode.PARSE_FAILED, "no clause parsed for contract " + contractId);
        }
        Set<String> presentTypes = chunks.stream().map(chunk -> chunk.clauseType().name())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ExtractionResult extraction = rulesOnly
                ? persistedExtraction(tenantId, contractId)
                : extractionService.extract(tenantId, contractId, operatorId);
        int tokensUsed = extraction.totalTokens();
        boolean budgetExhausted = tokensUsed >= modelProperties.getTokenBudgetPerContract();

        RuleCheckTool ruleTool = toolRegistry.ruleCheck();
        RiskRetrievalTool retrievalTool = toolRegistry.retrieval();
        ReportExportTool reportTool = toolRegistry.reportExport();

        List<RiskItem> risks = new ArrayList<>();
        List<String> degradeReasons = new ArrayList<>();
        int toolCalls = 0;
        int modelCalls = 0;
        int ruleFailures = 0;
        boolean retrievalDegraded = false;
        boolean modelDegraded = false;
        boolean converged = false;
        int callBudget = MAX_TOOL_CALLS_PER_CLAUSE * chunks.size() + 5;

        // 合同级规则：必备条款缺失这类判断无法在单条款内完成
        ToolOutcome<RuleEvaluationResult> contractRules = ruleTool.checkContract(tenantId, presentTypes);
        toolCalls++;
        if (contractRules.ok()) {
            for (RuleMatch match : contractRules.data().matches()) {
                risks.add(contractLevelRisk(match, contract));
            }
            ruleFailures += contractRules.data().failedRuleCodes().size();
        } else {
            degradeReasons.add(contractRules.message());
        }

        for (ContractChunkRecord chunk : chunks) {
            if (clock.instant().isAfter(deadline)) {
                converged = true;
                degradeReasons.add("deadline_exceeded");
                break;
            }
            if (toolCalls + MAX_TOOL_CALLS_PER_CLAUSE > callBudget) {
                converged = true;
                degradeReasons.add("tool_call_budget_exhausted");
                break;
            }
            RuleContext context = new RuleContext(chunk.chunkText(), chunk.clauseType().name(),
                    extraction.contractAmount(), extraction.paymentDays(), extraction.jurisdiction(),
                    extraction.hasJointLiability(), presentTypes);

            ToolOutcome<RuleEvaluationResult> clauseRules = ruleTool.checkClause(tenantId, context);
            toolCalls++;
            List<RuleMatch> matched = List.of();
            if (clauseRules.ok()) {
                matched = clauseRules.data().matches();
                ruleFailures += clauseRules.data().failedRuleCodes().size();
            } else {
                degradeReasons.add(clauseRules.message());
            }

            ToolOutcome<RetrievalResult> retrieval = null;
            List<RetrievedChunk> recalled = List.of();
            if (!rulesOnly) {
                retrieval = retrievalTool.retrieve(tenantId, chunk.chunkText(), chunk.clauseType().name());
                toolCalls++;
                if (retrieval.ok()) {
                    recalled = retrieval.data().chunks();
                    retrievalDegraded = retrievalDegraded || retrieval.data().degraded();
                } else {
                    retrievalDegraded = true;
                    degradeReasons.add(retrieval.message());
                }
            }

            ModelJudgement judgement = null;
            boolean shouldJudge = !rulesOnly && modelCalls < maxModelClauses && !budgetExhausted
                    && !recalled.isEmpty();
            if (shouldJudge) {
                judgement = clauseRiskService.judge(tenantId, contractId, chunk.clauseNo(),
                        chunk.clauseType().name(), chunk.chunkText(), recalled);
                toolCalls++;
                modelCalls++;
                if (judgement == null) {
                    modelDegraded = true;
                    degradeReasons.add("model_unavailable");
                } else {
                    tokensUsed += judgement.tokensUsed();
                    if (tokensUsed >= modelProperties.getTokenBudgetPerContract()) {
                        budgetExhausted = true;
                        degradeReasons.add("token_budget_exhausted");
                    }
                }
            }

            risks.addAll(buildClauseRisks(contract, chunk, matched, judgement, recalled));
        }

        risks.sort(Comparator.comparingInt((RiskItem risk) -> risk.level().severity())
                .thenComparing(RiskItem::clauseNo));
        List<RiskItem> deduped = dedupe(risks);
        boolean manualRequired = converged || modelDegraded || budgetExhausted
                || deduped.stream().anyMatch(RiskItem::needsReview);

        ReportMeta meta = new ReportMeta(contract.id(), contract.name(), clock.instant(),
                modelProperties.effectiveModelVersion(), clauseRiskService.promptVersion(),
                ruleCache.snapshot().version(), retrievalDegraded, modelDegraded,
                extraction.status().name(), extraction.verifiedRate(),
                rulesOnly ? "纯规则模式（未调用模型与检索）" : "初审预演：未纳入评估集统计，不提供未实测指标",
                chunks.size());

        ToolOutcome<ReportExportResult> export = reportTool.export(tenantId, contractId, deduped, meta);
        String reportPath = null;
        if (export.ok()) {
            reportPath = export.data().filePath();
        } else {
            degradeReasons.add(export.message());
            manualRequired = true;
        }

        long elapsed = clock.millis() - startedAt.toEpochMilli();
        ReviewStats stats = new ReviewStats(chunks.size(), toolCalls, modelCalls, ruleFailures, retrievalDegraded,
                modelDegraded, budgetExhausted, converged, manualRequired, elapsed,
                clauseRiskService.promptVersion(), modelProperties.effectiveModelVersion(), degradeReasons);
        log.info("初审预演完成 contractId={} clauses={} risks={} toolCalls={} modelCalls={} tokens={} report={}",
                contractId, chunks.size(), deduped.size(), toolCalls, modelCalls, tokensUsed, reportPath);
        return new ReviewOutcome(contractId, extraction, deduped, reportPath, stats);
    }

    /**
     * 纯规则模式复用上一次的抽取结果：该模式的目的是验证规则口径，不应再消耗模型额度。
     * 没有历史抽取时按“无抽取字段”处理（数值缺失按 0，阈值规则不会误触发）。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 抽取结果
     */
    private ExtractionResult persistedExtraction(long tenantId, long contractId) {
        return extractionService.find(tenantId, contractId)
                .map(record -> new ExtractionResult(record.contractAmount(), record.paymentDays(),
                        record.jurisdiction(), record.hasJointLiability(), record.status(), List.of(),
                        record.sourceSpanJson(), record.modelRawResponse(), record.promptVersion(),
                        record.modelName(), false, null, 0))
                .orElseGet(() -> new ExtractionResult(null, null, null, null,
                        com.crag.doc.domain.model.ExtractStatusEnum.DEGRADED, List.of(), "{}", null, null, null,
                        true, "no_extraction", 0));
    }

    /**
     * 去重：同一规则命中同一条款的不同分片（超长条款二次切分）只保留一条，
     * 否则报告会把同一条风险列多次，复核人员会重复处理。
     *
     * @param risks 已排序风险
     * @return 去重后的列表，保持原顺序
     */
    private static List<RiskItem> dedupe(List<RiskItem> risks) {
        java.util.LinkedHashMap<String, RiskItem> unique = new java.util.LinkedHashMap<>();
        for (RiskItem risk : risks) {
            // 键不含 chunkKey：同一规则命中同一条款的不同分片，对复核人员是同一条风险
            String key = risk.level().name() + "|" + risk.clauseNo() + "|" + risk.source() + "|"
                    + (risk.ruleCode() == null ? risk.title() : risk.ruleCode());
            unique.putIfAbsent(key, risk);
        }
        return List.copyOf(unique.values());
    }

    private List<RiskItem> buildClauseRisks(ContractRecord contract, ContractChunkRecord chunk,
                                            List<RuleMatch> matched, ModelJudgement judgement,
                                            List<RetrievedChunk> recalled) {
        List<RiskItem> items = new ArrayList<>();
        String chunkKey = "contract:" + contract.id() + "#" + chunk.fullTextIndex();
        String excerpt = truncate(chunk.chunkText(), 120);
        String conflictNote = conflictNote(judgement, matched);
        String snapshot = snapshotJson(recalled);
        for (RuleMatch match : matched) {
            items.add(new RiskItem(match.riskLevel(), chunk.clauseNo(), chunk.clauseType().name(), match.ruleName(),
                    "命中规则：" + match.ruleName() + conflictNote, match.suggestion(), contract.name(), chunkKey,
                    excerpt, conflictNote.isEmpty() ? "RULE" : "RULE_CONFLICT", match.ruleCode(),
                    match.ruleVersion(), match.riskLevel().requiresManualReview(), true,
                    chunk.chunkText(), snapshot));
        }
        if (judgement == null || !judgement.hasRisk()) {
            return items;
        }
        // 规范 7.5：高风险不得仅由模型判定。模型单独判定的高风险降级为中风险并强制人工复核
        RiskLevelEnum level = judgement.level();
        boolean downgraded = level == RiskLevelEnum.HIGH && matched.isEmpty();
        if (downgraded) {
            level = RiskLevelEnum.MEDIUM;
        }
        ModelJudgement.Citation citation = judgement.citations().isEmpty() ? null : judgement.citations().get(0);
        RetrievedChunk source = citation == null ? null : recalled.stream()
                .filter(item -> item.chunkKey().equals(citation.chunkKey())).findFirst().orElse(null);
        StringBuilder content = new StringBuilder("模型判断：").append(judgement.reason());
        if (downgraded) {
            content.append("（模型单独判定的高风险已降级为中风险，须人工复核）");
        }
        if (!judgement.citationValid()) {
            content.append("（引用校验未通过：").append(judgement.validationError())
                    .append("，结论转人工确认）");
        }
        items.add(new RiskItem(level, chunk.clauseNo(), chunk.clauseType().name(), "模型判定风险",
                content.toString(), judgement.suggestion(),
                source == null ? contract.name() : source.sourceDocTitle(),
                citation == null ? chunkKey : citation.chunkKey(),
                citation == null ? excerpt : citation.excerpt(),
                "MODEL", null, null, true, judgement.citationValid() && citation != null,
                chunk.chunkText(), snapshot));
        return items;
    }

    /**
     * 召回片段快照（hard negatives 回流用）：只保留切片键与文本，文本截断到上下文上限。
     *
     * @param recalled 召回片段
     * @return JSON 数组文本
     */
    private String snapshotJson(List<RetrievedChunk> recalled) {
        ArrayNode array = objectMapper.createArrayNode();
        for (RetrievedChunk chunk : recalled) {
            ObjectNode node = array.addObject();
            node.put("chunkKey", chunk.chunkKey());
            node.put("text", truncate(chunk.text(), modelProperties.getMaxContextChars()));
        }
        return array.toString();
    }

    private static String conflictNote(ModelJudgement judgement, List<RuleMatch> matched) {
        if (matched.isEmpty() || judgement == null) {
            return "";
        }
        if (!judgement.hasRisk()) {
            return "（模型结论：未发现风险；与规则不一致，差异需人工确认）";
        }
        RiskLevelEnum ruleLevel = matched.get(0).riskLevel();
        if (judgement.level() != ruleLevel) {
            return "（模型结论：" + judgement.level().label() + "；与规则等级不一致，以规则为准，差异需人工确认）";
        }
        return "";
    }

    private RiskItem contractLevelRisk(RuleMatch match, ContractRecord contract) {
        return new RiskItem(match.riskLevel(), "全篇", "CONTRACT", match.ruleName(),
                "命中合同级规则：" + match.ruleName(), match.suggestion(), contract.name(),
                "contract:" + contract.id(), "（合同级规则，无单条款引用）", "RULE", match.ruleCode(),
                match.ruleVersion(), match.riskLevel().requiresManualReview(), true, null, "[]");
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    /**
     * 工具调用统计口径，供测试与文档引用。
     *
     * @return 单条款工具调用上限
     */
    public static int maxToolCallsPerClause() {
        return MAX_TOOL_CALLS_PER_CLAUSE;
    }

    /**
     * 整体超时口径（分钟）。
     *
     * @return 分钟数
     */
    public static int deadlineMinutes() {
        return DEADLINE_MINUTES;
    }
}
