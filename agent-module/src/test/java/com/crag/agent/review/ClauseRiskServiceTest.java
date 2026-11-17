package com.crag.agent.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.agent.model.ChatModelClient;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.model.ChatResponse;
import com.crag.agent.prompt.PromptRegistry;
import com.crag.common.domain.RiskLevelEnum;
import com.crag.common.text.ClauseTypeEnum;
import com.crag.kb.domain.model.RetrievedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 条款风险判断的引用校验测试（规范 7.5 第二、三级防线）。
 *
 * <p>用固定响应替身（规范 13.2）覆盖三条失败路径：引用不在召回结果、引用不是原文、缺少引用；
 * 以及一条成功路径。模型本身不参与断言。</p>
 */
class ClauseRiskServiceTest {

    private static final String CLAUSE = "接收方应对保密信息承担保密义务，期限为三年。";
    private static final RetrievedChunk CHUNK = new RetrievedChunk(11L, "kb#doc#2#0",
            "双方应对合同内容承担保密义务，保密期限为合同终止后三年。", 7L, "保密管理制度",
            ClauseTypeEnum.CONFIDENTIALITY, 0.82D);

    private static ClauseRiskService serviceReturning(String modelJson) {
        ChatModelClient stub = request -> new ChatResponse(modelJson, "qwen3.8-flash", "qwen3.8-flash",
                100, 50, 12L, false, null, List.of());
        ChatModelProperties properties = new ChatModelProperties();
        properties.setMaxOutputTokens(500);
        return new ClauseRiskService(stub, properties, new PromptRegistry("classpath:prompts/*.md"),
                new ObjectMapper());
    }

    private static ModelJudgement judge(ClauseRiskService service) {
        return service.judge(1L, 2L, "第三条", "CONFIDENTIALITY", CLAUSE, List.of(CHUNK));
    }

    @Test
    @DisplayName("引用属于召回且逐字存在：判定通过")
    void acceptsValidCitation() {
        ModelJudgement judgement = judge(serviceReturning("""
                {"riskLevel":"MEDIUM","reason":"保密期限口径需与制度一致","suggestion":"统一期限表述",
                 "citations":[{"chunkKey":"kb#doc#2#0","excerpt":"保密期限为合同终止后三年"}]}
                """));

        assertThat(judgement.level()).isEqualTo(RiskLevelEnum.MEDIUM);
        assertThat(judgement.citationValid()).isTrue();
        assertThat(judgement.validationError()).isNull();
        assertThat(judgement.tokensUsed()).isEqualTo(150);
    }

    @Test
    @DisplayName("引用不在本次召回结果：判为 citation_not_recalled")
    void rejectsCitationNotRecalled() {
        ModelJudgement judgement = judge(serviceReturning("""
                {"riskLevel":"LOW","reason":"x","suggestion":"y",
                 "citations":[{"chunkKey":"kb#other#9#0","excerpt":"保密期限为合同终止后三年"}]}
                """));

        assertThat(judgement.citationValid()).isFalse();
        assertThat(judgement.validationError()).isEqualTo("citation_not_recalled");
    }

    @Test
    @DisplayName("引用不是切片原文：判为 citation_not_found")
    void rejectsFabricatedExcerpt() {
        ModelJudgement judgement = judge(serviceReturning("""
                {"riskLevel":"LOW","reason":"x","suggestion":"y",
                 "citations":[{"chunkKey":"kb#doc#2#0","excerpt":"保密期限为五年"}]}
                """));

        assertThat(judgement.citationValid()).isFalse();
        assertThat(judgement.validationError()).isEqualTo("citation_not_found");
    }

    @Test
    @DisplayName("有风险结论却没有引用：判为 missing_citations")
    void rejectsMissingCitations() {
        ModelJudgement judgement = judge(serviceReturning(
                "{\"riskLevel\":\"HIGH\",\"reason\":\"x\",\"suggestion\":\"y\",\"citations\":[]}"));

        assertThat(judgement.hasRisk()).isTrue();
        assertThat(judgement.citationValid()).isFalse();
        assertThat(judgement.validationError()).isEqualTo("missing_citations");
    }

    @Test
    @DisplayName("NONE 结论不要求引用")
    void noneNeedsNoCitation() {
        ModelJudgement judgement = judge(serviceReturning(
                "{\"riskLevel\":\"NONE\",\"reason\":\"依据不足\",\"suggestion\":\"\",\"citations\":[]}"));

        assertThat(judgement.hasRisk()).isFalse();
        assertThat(judgement.citationValid()).isTrue();
    }

    @Test
    @DisplayName("输出不是合法 JSON 时不产生结论")
    void invalidJsonYieldsNoRisk() {
        ModelJudgement judgement = judge(serviceReturning("我认为这条没有问题"));
        assertThat(judgement.hasRisk()).isFalse();
        assertThat(judgement.validationError()).isEqualTo("invalid_json");
    }
}
