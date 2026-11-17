package com.crag.agent.clause;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.agent.model.ChatModelClient;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.model.ChatResponse;
import com.crag.agent.prompt.PromptRegistry;
import com.crag.common.domain.TenantContext;
import com.crag.common.text.ClauseTypeEnum;
import com.crag.common.text.TypeClassification;
import com.crag.common.text.TypeSourceEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 条款类型模型兜底测试（规范 5.3）：命中、低置信、降级、非法输出四条路径。
 */
class ModelClauseTypeFallbackTest {

    private ModelClauseTypeFallback fallback;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ModelClauseTypeFallback with(String content) {
        ChatModelClient stub = request -> new ChatResponse(content, "qwen3.8-flash", "qwen3.8-flash", 20, 10, 5L,
                false, null, List.of());
        return new ModelClauseTypeFallback(stub, new ChatModelProperties(),
                new PromptRegistry("classpath:prompts/*.md"), new ObjectMapper());
    }

    @Test
    @DisplayName("高置信分类：返回模型判定类型")
    void classifiesWithHighConfidence() {
        TypeClassification result = with("{\"clauseType\":\"PAYMENT\",\"confidence\":0.9,\"reason\":\"付款\"}")
                .classify("价款支付", "甲方应在三十日内支付价款。");

        assertThat(result.clauseType()).isEqualTo(ClauseTypeEnum.PAYMENT);
        assertThat(result.typeSource()).isEqualTo(TypeSourceEnum.MODEL);
    }

    @Test
    @DisplayName("低置信：保持其他 + DEFAULT")
    void keepsOtherWhenLowConfidence() {
        TypeClassification result = with("{\"clauseType\":\"PAYMENT\",\"confidence\":0.3,\"reason\":\"不确定\"}")
                .classify("条款", "双方另行约定。");

        assertThat(result.clauseType()).isEqualTo(ClauseTypeEnum.OTHER);
        assertThat(result.typeSource()).isEqualTo(TypeSourceEnum.DEFAULT);
    }

    @Test
    @DisplayName("模型降级：保持其他 + DEFAULT，不阻断解析")
    void keepsOtherWhenDegraded() {
        ChatModelClient degraded = request -> ChatResponse.degraded("qwen3.8-flash", "circuit_open", 0L);
        ModelClauseTypeFallback service = new ModelClauseTypeFallback(degraded, new ChatModelProperties(),
                new PromptRegistry("classpath:prompts/*.md"), new ObjectMapper());

        TypeClassification result = service.classify("条款", "正文");

        assertThat(result.clauseType()).isEqualTo(ClauseTypeEnum.OTHER);
        assertThat(result.typeSource()).isEqualTo(TypeSourceEnum.DEFAULT);
    }

    @Test
    @DisplayName("非法 JSON 或未知类型：保持其他 + DEFAULT")
    void keepsOtherOnInvalidOutput() {
        assertThat(with("不是 JSON").classify("条款", "正文").clauseType()).isEqualTo(ClauseTypeEnum.OTHER);
        assertThat(with("{\"clauseType\":\"UNKNOWN\",\"confidence\":0.99}").classify("条款", "正文")
                .clauseType()).isEqualTo(ClauseTypeEnum.OTHER);
    }
}
