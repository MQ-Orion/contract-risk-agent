package com.crag.agent.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.doc.domain.model.ExtractStatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 抽取回验评估集的确定性校验（规范 7.4、7.5、12.3、13.2）。
 *
 * <p>用固定响应替身跑评估集，断言每例的状态、字段值与回验通过率；真实模型跑批见
 * {@code ExtractionEvalIT}（手动触发）。CI 不断言真实模型输出。</p>
 *
 * <p>样本全部为虚构合同片段，不含真实主体与金额；扩充时只改 {@code eval/extraction/cases.json}。</p>
 */
class ExtractionEvalFixedTest {

    private final ExtractionValidator validator = new ExtractionValidator(new ObjectMapper());
    private final ExtractionVerifier verifier = new ExtractionVerifier(new ObjectMapper());

    private static JsonNode loadCases() throws IOException {
        Path path = Path.of(System.getProperty("crag.workspace.root"), "eval", "extraction", "cases.json");
        return new ObjectMapper().readTree(Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("固定响应回放：每例状态、字段与回验率符合标注")
    void replaysFixedModelOutputs() throws IOException {
        JsonNode root = loadCases();
        int checked = 0;
        for (JsonNode node : root.path("cases")) {
            String id = node.path("id").asText();
            String sourceText = joinClauses(node);
            ExtractionValidator.ExtractionCandidate candidate = validator.parse(node.path("modelOutput").asText());
            ExtractionResult result = verifier.verify(candidate, sourceText, "extract-v1", "stub", "", 0);
            JsonNode expect = node.path("expect");

            assertThat(result.status().name()).as("%s 状态", id).isEqualTo(expect.path("status").asText());
            assertThat(result.verifiedRate()).as("%s 回验率", id)
                    .isCloseTo(expect.path("verifiedRate").asDouble(), org.assertj.core.data.Offset.offset(0.0001));
            if (expect.has("contractAmount")) {
                assertThat(result.contractAmount()).as("%s 金额", id)
                        .isEqualTo(expect.path("contractAmount").isNull() ? null
                                : expect.path("contractAmount").decimalValue());
            }
            if (expect.has("paymentDays")) {
                assertThat(result.paymentDays()).as("%s 账期", id)
                        .isEqualTo(expect.path("paymentDays").isNull() ? null : expect.path("paymentDays").asInt());
            }
            if (expect.has("jurisdiction")) {
                assertThat(result.jurisdiction()).as("%s 管辖地", id)
                        .isEqualTo(expect.path("jurisdiction").isNull() ? null : expect.path("jurisdiction").asText());
            }
            if (expect.has("hasJointLiability")) {
                assertThat(result.hasJointLiability()).as("%s 连带责任", id)
                        .isEqualTo(expect.path("hasJointLiability").isNull() ? null
                                : expect.path("hasJointLiability").asBoolean());
            }
            if (expect.has("valueCount")) {
                assertThat(result.valueCount()).as("%s 有值字段数", id).isEqualTo(expect.path("valueCount").asInt());
            }
            if (expect.has("pendingField")) {
                ExtractionField field = result.field(expect.path("pendingField").asText());
                assertThat(field).as("%s 待确认字段", id).isNotNull();
                assertThat(field.pendingManual()).as("%s 应待人工确认", id).isTrue();
                assertThat(field.reason()).as("%s 待确认原因", id)
                        .isEqualTo(expect.path("pendingReason").asText());
            }
            checked++;
        }
        assertThat(checked).as("评估集样例数").isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("评估集按 7:3 划分开发集与验证集，且全部标注了期望状态")
    void evalSetIsSplitAndLabeled() throws IOException {
        JsonNode root = loadCases();
        int dev = root.path("meta").path("split").path("dev").size();
        int val = root.path("meta").path("split").path("val").size();
        assertThat(dev + val).isEqualTo(root.path("cases").size());
        assertThat((double) dev / (dev + val)).isBetween(0.6D, 0.8D);
        for (JsonNode node : root.path("cases")) {
            assertThat(node.path("expect").path("status").asText()).isIn("VERIFIED", "PENDING_MANUAL", "DEGRADED");
        }
    }

    private static String joinClauses(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode clause : node.path("clauses")) {
            builder.append(clause.path("text").asText()).append('\n');
        }
        return builder.toString();
    }
}
