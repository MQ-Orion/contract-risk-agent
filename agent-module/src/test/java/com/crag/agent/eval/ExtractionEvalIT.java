package com.crag.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.agent.extract.ExtractionField;
import com.crag.agent.extract.ExtractionResult;
import com.crag.agent.extract.ExtractionValidator;
import com.crag.agent.extract.ExtractionVerifier;
import com.crag.agent.model.ChatModelClient;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.model.ChatRequest;
import com.crag.agent.model.ChatResponse;
import com.crag.agent.model.ModelCallPurposeEnum;
import com.crag.agent.prompt.PromptRegistry;
import com.crag.agent.prompt.PromptTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 抽取回验真实模型跑批（规范 12.3、12.4、13.2：真实调用不进 CI，手动触发并归档报告）。
 *
 * <p>用虚构样例评估集（{@code eval/extraction/cases.json}）调用 {@code qwen3.8-flash}，统计：
 * 抽取回验通过率（有值字段中片段逐字通过的比例）、字段准确率（与人工标注值一致的比例）、
 * 整体状态分布。报告写入 {@code docs/eval/extraction-YYYYMMDD.md}，含模型与 Prompt 版本、耗时。</p>
 *
 * <p>触发：`mvn ... test -Dcrag.eval=true -Dtest=ExtractionEvalIT`，需 `DASHSCOPE_API_KEY`。
 * 断言只校验“结构合法”，不对模型准确率做硬门槛（模型非确定性，规范 13.2）。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "crag.eval", matches = "true")
class ExtractionEvalIT {

    @Autowired
    private ChatModelClient chatModelClient;
    @Autowired
    private ChatModelProperties properties;
    @Autowired
    private PromptRegistry promptRegistry;
    @Autowired
    private ObjectMapper objectMapper;

    private final ExtractionValidator validator = new ExtractionValidator(new ObjectMapper());

    @Test
    @DisplayName("虚构样例集：真实模型抽取 + 原文回验，输出指标报告")
    void evaluatesExtractionWithRealModel() throws IOException {
        Path root = Path.of(System.getProperty("crag.workspace.root"));
        JsonNode cases = objectMapper.readTree(Files.readString(
                root.resolve("eval/extraction/cases.json"), StandardCharsets.UTF_8));
        PromptTemplate prompt = promptRegistry.get("contract-extract");

        List<CaseOutcome> outcomes = new ArrayList<>();
        for (JsonNode node : cases.path("cases")) {
            String id = node.path("id").asText();
            String sourceText = joinClauses(node);
            ChatResponse response = chatModelClient.complete(new ChatRequest(1L, ModelCallPurposeEnum.EXTRACT,
                    prompt.version(), prompt.systemPrompt(), buildUserPrompt(node), 1L, null,
                    properties.getMaxOutputTokens()));
            if (response.degraded()) {
                outcomes.add(new CaseOutcome(id, "DEGRADED", 0, 0, 0, 0, response.degradeReason()));
                continue;
            }
            ExtractionResult result;
            try {
                result = verifier().verify(validator.parse(response.content()), sourceText, prompt.version(),
                        response.model(), "", response.totalTokens());
            } catch (RuntimeException e) {
                outcomes.add(new CaseOutcome(id, "FORMAT_ERROR", 0, 0, 0, 0, e.getMessage()));
                continue;
            }
            int expectedFields = countExpected(node.path("expect"));
            int correctFields = countCorrect(result, node.path("expect"));
            outcomes.add(new CaseOutcome(id, result.status().name(), result.valueCount(), result.verifiedCount(),
                    expectedFields, correctFields, null));
        }

        long values = outcomes.stream().mapToLong(CaseOutcome::values).sum();
        long verified = outcomes.stream().mapToLong(CaseOutcome::verified).sum();
        long expectedTotal = outcomes.stream().mapToLong(CaseOutcome::expectedFields).sum();
        long correctTotal = outcomes.stream().mapToLong(CaseOutcome::correctFields).sum();
        double verifiedRate = values == 0 ? 0D : (double) verified / values;
        double fieldAccuracy = expectedTotal == 0 ? 0D : (double) correctTotal / expectedTotal;

        String markdown = renderReport(outcomes, verifiedRate, fieldAccuracy, prompt.version());
        Path report = root.resolve("docs/eval/extraction-" + LocalDate.now() + ".md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, markdown, StandardCharsets.UTF_8);

        assertThat(report).exists();
        assertThat(values).as("评估集应至少抽取出若干有值字段").isGreaterThanOrEqualTo(0);
    }

    private ExtractionVerifier verifier() {
        return new ExtractionVerifier(objectMapper);
    }

    private String buildUserPrompt(JsonNode node) {
        ArrayNode clauses = objectMapper.createArrayNode();
        for (JsonNode clause : node.path("clauses")) {
            ObjectNode item = clauses.addObject();
            item.put("clauseNo", clause.path("clauseNo").asText());
            item.put("clauseType", clause.path("clauseType").asText());
            item.put("text", clause.path("text").asText());
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractName", node.path("title").asText());
        root.set("clauses", clauses);
        root.put("instruction", "只依据 clauses 中的原文抽取；片段必须逐字出现在其中。");
        return root.toString();
    }

    private static String joinClauses(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode clause : node.path("clauses")) {
            builder.append(clause.path("text").asText()).append('\n');
        }
        return builder.toString();
    }

    private static int countExpected(JsonNode expect) {
        int count = 0;
        for (String field : new String[]{"contractAmount", "paymentDays", "jurisdiction", "hasJointLiability"}) {
            if (expect.has(field) && !expect.path(field).isNull()) {
                count++;
            }
        }
        return count;
    }

    private static int countCorrect(ExtractionResult result, JsonNode expect) {
        int correct = 0;
        for (String field : new String[]{"contractAmount", "paymentDays", "jurisdiction", "hasJointLiability"}) {
            if (!expect.has(field) || expect.path(field).isNull()) {
                continue;
            }
            ExtractionField actual = result.field(field);
            if (actual == null || !actual.usable()) {
                continue;
            }
            String expectedText = expect.path(field).asText();
            if (String.valueOf(actual.value()).equals(expectedText)) {
                correct++;
            }
        }
        return correct;
    }

    private static String renderReport(List<CaseOutcome> outcomes, double verifiedRate, double fieldAccuracy,
                                       String promptVersion) {
        StringBuilder md = new StringBuilder();
        md.append("# 抽取回验评估报告（").append(LocalDate.now()).append("）\n\n");
        md.append("> 评估集：`eval/extraction/cases.json`（虚构样例，无真实主体与金额）。\n");
        md.append("> 模型：`").append("qwen3.8-flash").append("`；Prompt：`").append(promptVersion)
                .append("`；测量时间：").append(java.time.OffsetDateTime.now()).append("\n\n");
        md.append("## 1. 指标\n\n");
        md.append("| 指标 | 定义 | 实测 | 目标（规范 12.3） |\n| --- | --- | --- | --- |\n");
        md.append("| 抽取回验通过率 | 有值字段中片段逐字存在的比例 | ")
                .append(String.format("%.2f%%（%d/%d）", verifiedRate * 100, sum(outcomes, true),
                        sum(outcomes, false))).append(" | ≥ 90% |\n");
        md.append("| 字段准确率 | 通过回验且与标注一致的比例 | ")
                .append(String.format("%.2f%%（%d/%d）", fieldAccuracy * 100,
                        outcomes.stream().mapToLong(CaseOutcome::correctFields).sum(),
                        outcomes.stream().mapToLong(CaseOutcome::expectedFields).sum()))
                .append(" | 参考指标 |\n\n");
        md.append("## 2. 分例结果\n\n");
        md.append("| 用例 | 状态 | 有值字段 | 回验通过 | 标注字段 | 准确字段 | 备注 |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (CaseOutcome outcome : outcomes) {
            md.append("| ").append(outcome.id()).append(" | ").append(outcome.status()).append(" | ")
                    .append(outcome.values()).append(" | ").append(outcome.verified()).append(" | ")
                    .append(outcome.expectedFields()).append(" | ").append(outcome.correctFields()).append(" | ")
                    .append(outcome.note() == null ? "" : outcome.note()).append(" |\n");
        }
        md.append("\n## 3. 口径说明\n\n");
        md.append("- 回验通过率只统计“有值字段”：模型未抽取的字段不计入分母（规范 12.3）。\n");
        md.append("- 字段准确率要求“回验通过且与标注一致”，用于发现“片段对但值错”的情况。\n");
        md.append("- 本报告为手动触发跑批，CI 使用固定响应替身（`ExtractionEvalFixedTest`），不断言真实模型输出（规范 13.2）。\n");
        return md.toString();
    }

    private static long sum(List<CaseOutcome> outcomes, boolean verified) {
        return outcomes.stream().mapToLong(verified ? CaseOutcome::verified : CaseOutcome::values).sum();
    }

    /**
     * 单例结果。
     *
     * @param id             用例标识
     * @param status         抽取状态
     * @param values         有值字段数
     * @param verified       回验通过字段数
     * @param expectedFields 标注字段数
     * @param correctFields  准确字段数
     * @param note           备注
     */
    private record CaseOutcome(String id, String status, int values, int verified, int expectedFields,
                               int correctFields, String note) {
    }
}
