package com.crag.agent.clause;

import com.crag.agent.model.ChatModelClient;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.model.ChatRequest;
import com.crag.agent.model.ChatResponse;
import com.crag.agent.model.ModelCallPurposeEnum;
import com.crag.agent.prompt.PromptRegistry;
import com.crag.agent.prompt.PromptTemplate;
import com.crag.common.domain.TenantContext;
import com.crag.common.text.ClauseTypeEnum;
import com.crag.common.text.TypeClassification;
import com.crag.common.text.TypeSourceEnum;
import com.crag.doc.application.port.ClauseTypeFallbackPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 条款类型模型兜底（规范 5.3）：关键词未命中时由模型分类，失败保持“其他 + DEFAULT”。
 *
 * <p>实现放在 agent 而不是 doc：doc 禁止调用 LLM（规范 2.1），端口由 doc 定义、agent 注入。</p>
 *
 * <p>成本与稳定性约束：只处理标题与正文均未命中关键词的条款（上游已过滤）；正文截断到配置长度；
 * 置信度低于阈值一律视为未命中；模型降级或输出非法时返回 OTHER，不阻断解析流水线。</p>
 *
 * <p>线程安全性：无可变状态；模型客户端自带并发上限与熔断。</p>
 */
public class ModelClauseTypeFallback implements ClauseTypeFallbackPort {

    private static final Logger log = LoggerFactory.getLogger(ModelClauseTypeFallback.class);
    private static final String PROMPT_NAME = "clause-type";

    private final ChatModelClient chatModelClient;
    private final ChatModelProperties properties;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 构造兜底分类器。
     *
     * @param chatModelClient 模型客户端
     * @param properties      模型配置
     * @param promptRegistry  Prompt 注册中心
     * @param objectMapper    JSON 解析
     */
    public ModelClauseTypeFallback(ChatModelClient chatModelClient, ChatModelProperties properties,
                                   PromptRegistry promptRegistry, ObjectMapper objectMapper) {
        this.chatModelClient = chatModelClient;
        this.properties = properties;
        this.promptRegistry = promptRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public TypeClassification classify(String headline, String body) {
        TypeClassification other = new TypeClassification(ClauseTypeEnum.OTHER, TypeSourceEnum.DEFAULT);
        Long tenantId = TenantContext.current();
        if (tenantId == null) {
            // 没有租户上下文时不发起模型调用：调用日志按租户留存，缺失会脏数据
            return other;
        }
        PromptTemplate prompt = promptRegistry.get(PROMPT_NAME);
        ChatResponse response = chatModelClient.complete(new ChatRequest(tenantId,
                ModelCallPurposeEnum.CLAUSE_TYPE, prompt.version(), prompt.systemPrompt(),
                buildUserPrompt(headline, body), null, null, 120));
        if (response.degraded()) {
            log.debug("条款类型兜底降级 reason={}", response.degradeReason());
            return other;
        }
        return parse(response.content(), other);
    }

    private String buildUserPrompt(String headline, String body) {
        String text = (headline == null ? "" : headline) + "\n" + (body == null ? "" : body);
        String truncated = text.length() > properties.getClauseTypeMaxChars()
                ? text.substring(0, properties.getClauseTypeMaxChars()) : text;
        return "条款标题与正文：\n" + truncated;
    }

    private TypeClassification parse(String content, TypeClassification fallback) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String type = root.path("clauseType").asText("OTHER");
            double confidence = root.path("confidence").asDouble(0D);
            if (confidence < properties.getClauseTypeMinConfidence()) {
                return fallback;
            }
            ClauseTypeEnum clauseType = ClauseTypeEnum.fromCode(type);
            if (clauseType == ClauseTypeEnum.OTHER) {
                return fallback;
            }
            return new TypeClassification(clauseType, TypeSourceEnum.MODEL);
        } catch (Exception e) {
            log.debug("条款类型兜底输出不可解析：{}", e.getMessage());
            return fallback;
        }
    }
}
