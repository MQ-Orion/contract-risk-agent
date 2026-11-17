package com.crag.agent.review;

import com.crag.agent.model.ChatModelClient;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.model.ChatRequest;
import com.crag.agent.model.ChatResponse;
import com.crag.agent.model.ModelCallPurposeEnum;
import com.crag.agent.prompt.PromptRegistry;
import com.crag.agent.prompt.PromptTemplate;
import com.crag.common.domain.RiskLevelEnum;
import com.crag.common.text.TextNormalizer;
import com.crag.kb.domain.model.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 条款风险判断（规范 7.2、7.4、7.5）：把条款与召回片段送模型，逐条校验引用。
 *
 * <p>三级防线在这里落地：引用必须属于本次召回（第二级），引用必须逐字存在于对应切片（第三级），
 * 校验前统一归一化（规范 7.5）。校验失败不是“丢掉结论”，而是保留结论并标记待人工确认，同时计入统计。</p>
 *
 * <p>模型调用的失败与降级由 {@link ChatModelClient} 负责；本类只处理“内容层面的不合法”（JSON 结构、枚举、引用）。</p>
 */
@Service
public class ClauseRiskService {

    private static final Logger log = LoggerFactory.getLogger(ClauseRiskService.class);

    private static final String PROMPT_NAME = "clause-risk";

    private final ChatModelClient chatModelClient;
    private final ChatModelProperties properties;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 构造服务。
     *
     * @param chatModelClient 模型客户端
     * @param properties      模型配置
     * @param promptRegistry  Prompt 注册中心
     * @param objectMapper    JSON 解析
     */
    public ClauseRiskService(ChatModelClient chatModelClient, ChatModelProperties properties,
                             PromptRegistry promptRegistry, ObjectMapper objectMapper) {
        this.chatModelClient = chatModelClient;
        this.properties = properties;
        this.promptRegistry = promptRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Prompt 版本，供报告标注。
     *
     * @return 版本
     */
    public String promptVersion() {
        return promptRegistry.get(PROMPT_NAME).version();
    }

    /**
     * 判断单条款风险。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param clauseNo   条款序号
     * @param clauseType 条款类型
     * @param clauseText 条款文本
     * @param recalled   本次召回片段
     * @return 判断结果；模型降级时返回 null（调用方按“模型不可用”处理）
     */
    public ModelJudgement judge(long tenantId, long contractId, String clauseNo, String clauseType,
                                String clauseText, List<RetrievedChunk> recalled) {
        if (recalled == null || recalled.isEmpty()) {
            return new ModelJudgement(null, "无召回依据", "", List.of(), false, "no_recall", 0);
        }
        PromptTemplate prompt = promptRegistry.get(PROMPT_NAME);
        ChatResponse response = chatModelClient.complete(new ChatRequest(tenantId,
                ModelCallPurposeEnum.RISK_JUDGEMENT, prompt.version(), prompt.systemPrompt(),
                buildUserPrompt(clauseNo, clauseType, clauseText, recalled), contractId, clauseNo,
                properties.getMaxOutputTokens()));
        if (response.degraded()) {
            log.warn("条款风险判断降级 contractId={} clauseNo={} reason={}", contractId, clauseNo,
                    response.degradeReason());
            return null;
        }
        return parse(response.content(), recalled, response.totalTokens());
    }

    private String buildUserPrompt(String clauseNo, String clauseType, String clauseText,
                                   List<RetrievedChunk> recalled) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode clause = root.putObject("clause");
        clause.put("clauseNo", clauseNo);
        clause.put("clauseType", clauseType);
        clause.put("text", truncate(clauseText, properties.getMaxContextChars()));
        ArrayNode evidences = root.putArray("evidence");
        int limit = Math.min(recalled.size(), properties.getMaxContextChunks());
        for (int i = 0; i < limit; i++) {
            RetrievedChunk chunk = recalled.get(i);
            ObjectNode node = evidences.addObject();
            node.put("chunkKey", chunk.chunkKey());
            node.put("sourceTitle", chunk.sourceDocTitle());
            node.put("text", truncate(chunk.text(), properties.getMaxContextChars()));
        }
        root.put("instruction", "只依据 clause 与 evidence 判断；citations 的 chunkKey 必须来自 evidence。");
        return root.toString();
    }

    private ModelJudgement parse(String raw, List<RetrievedChunk> recalled, int responseTokens) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            return new ModelJudgement(null, "模型输出不是合法 JSON", "", List.of(), false, "invalid_json", responseTokens);
        }
        String levelText = root.path("riskLevel").asText("NONE");
        String reason = root.path("reason").asText("");
        String suggestion = root.path("suggestion").asText("");
        if ("NONE".equalsIgnoreCase(levelText)) {
            return new ModelJudgement(null, reason, suggestion, List.of(), true, null, responseTokens);
        }
        RiskLevelEnum level = RiskLevelEnum.fromCode(levelText);
        if (level == null) {
            return new ModelJudgement(null, reason, suggestion, List.of(), false, "unknown_risk_level", responseTokens);
        }
        Map<String, RetrievedChunk> byKey = new HashMap<>();
        for (RetrievedChunk chunk : recalled) {
            byKey.put(chunk.chunkKey(), chunk);
        }
        List<ModelJudgement.Citation> citations = new ArrayList<>();
        String error = null;
        JsonNode citationNodes = root.path("citations");
        if (!citationNodes.isArray() || citationNodes.isEmpty()) {
            error = "missing_citations";
        } else {
            for (JsonNode node : citationNodes) {
                String chunkKey = node.path("chunkKey").asText("");
                String excerpt = node.path("excerpt").asText("");
                RetrievedChunk chunk = byKey.get(chunkKey);
                if (chunk == null) {
                    error = "citation_not_recalled";
                    break;
                }
                if (!TextNormalizer.containsNormalized(chunk.text(), excerpt)) {
                    error = "citation_not_found";
                    break;
                }
                citations.add(new ModelJudgement.Citation(chunkKey, excerpt));
            }
        }
        return new ModelJudgement(level, reason, suggestion, citations, error == null, error, responseTokens);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
