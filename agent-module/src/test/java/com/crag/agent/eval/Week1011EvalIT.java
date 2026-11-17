package com.crag.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.domain.TenantContext;
import com.crag.kb.application.KbDocService;
import com.crag.kb.application.KbQueryService;
import com.crag.kb.application.port.EmbeddingPort;
import com.crag.kb.application.port.KbChunkReadPort;
import com.crag.kb.config.EmbeddingProperties;
import com.crag.kb.domain.KbChunkKeys;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbDocTypeEnum;
import com.crag.kb.domain.model.KbSyncStatusEnum;
import com.crag.kb.domain.model.RetrievedChunk;
import com.crag.retrieval.application.RetrievalService;
import com.crag.retrieval.config.RetrievalProperties;
import com.crag.retrieval.domain.model.RetrievalResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 第 10–11 周扩展评估（规范 12.1、12.2、12.4）：上下文召回率与 hard negatives 误报率。
 *
 * <p>与 {@link RetrievalEvalIT} 分开的原因：基础评估回答“能不能命中”，本评估回答“多条款问题能否召回全”
 * 与“无关问题会不会硬凑结果”，两者数据来源与失败处理不同，混在一起会让报告难以解释。</p>
 *
 * <p>需要真实 PostgreSQL/Redis/百炼 embedding，按 {@code -Dcrag.it=true} 启用；报告写入 docs/eval。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "crag.it", matches = "true")
class Week1011EvalIT {

    private static final long TENANT = 1L;
    private static final long USER = 1L;
    private static final int TOP_K = 5;

    @Autowired
    private KbDocService docService;
    @Autowired
    private KbQueryService queryService;
    @Autowired
    private RetrievalService retrievalService;
    @Autowired
    private KbChunkReadPort readPort;
    @Autowired
    private EmbeddingPort embeddingPort;
    @Autowired
    private RetrievalProperties retrievalProperties;
    @Autowired
    private EmbeddingProperties embeddingProperties;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("上下文召回率与 hard negatives 误报率")
    void runsContextRecallAndHardNegativeEvaluation() throws Exception {
        long startedAt = System.currentTimeMillis();
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        TenantContext.set(TENANT);
        try {
            seedKnowledgeBase(repoRoot.resolve("eval/kb"));
            List<MultiResult> multiResults = evaluateMultiLabel(
                    repoRoot.resolve("eval/retrieval/multi-label.json"));
            List<NegativeResult> negatives = evaluateHardNegatives(
                    repoRoot.resolve("eval/retrieval/hard-negatives.json"));
            writeReport(repoRoot, multiResults, negatives, System.currentTimeMillis() - startedAt);
            assertBaseline(multiResults);
        } finally {
            TenantContext.clear();
        }
    }

    private void seedKnowledgeBase(Path kbDir) throws IOException {
        List<Path> docs = new ArrayList<>();
        try (var stream = Files.list(kbDir)) {
            stream.filter(path -> path.toString().endsWith(".md")).sorted().forEach(docs::add);
        }
        assertThat(docs).as("知识库语料缺失：%s", kbDir).isNotEmpty();
        for (Path doc : docs) {
            List<String> lines = Files.readAllLines(doc, StandardCharsets.UTF_8);
            String title = lines.get(0).replaceFirst("^#\\s*", "").strip();
            String content = String.join("\n", lines.subList(1, lines.size()));
            KbDocRecord existing = queryService.list(TENANT, null).stream()
                    .filter(record -> record.title().equals(title))
                    .findFirst().orElse(null);
            long docId;
            if (existing == null) {
                docId = docService.create(TENANT, USER, title, KbDocTypeEnum.INSTITUTION, content, true).id();
            } else {
                docId = existing.id();
                docService.updateContent(TENANT, docId, content);
                docService.publish(TENANT, docId);
            }
            awaitReady(docId, title);
        }
    }

    private void awaitReady(long docId, String title) {
        long deadline = System.currentTimeMillis() + 180_000L;
        while (System.currentTimeMillis() < deadline) {
            KbDocRecord record = queryService.detail(TENANT, docId);
            if (record.syncStatus() == KbSyncStatusEnum.READY) {
                return;
            }
            sleep(500L);
        }
        throw new AssertionError("知识库同步超时：" + title);
    }

    private List<MultiResult> evaluateMultiLabel(Path file) throws IOException {
        JsonNode queries = objectMapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        List<MultiResult> results = new ArrayList<>();
        for (JsonNode query : queries) {
            Set<String> expectedKeys = new LinkedHashSet<>();
            for (JsonNode expected : query.path("expected")) {
                expectedKeys.add(KbChunkKeys.chunkKey(KbChunkKeys.docKeyOf(expected.path("doc").asText()),
                        expected.path("clauseNo").asText(), 0));
            }
            RetrievalResult retrieval = retrievalService.retrieve(query.path("query").asText(),
                    query.path("clauseType").asText(null), TENANT);
            List<String> returned = retrieval.chunks().stream().limit(TOP_K).map(RetrievedChunk::chunkKey).toList();
            long matched = expectedKeys.stream().filter(returned::contains).count();
            results.add(new MultiResult(query.path("id").asText(), query.path("split").asText(),
                    expectedKeys.size(), (int) matched, matched > 0, retrieval.degraded()));
        }
        return List.copyOf(results);
    }

    private List<NegativeResult> evaluateHardNegatives(Path file) throws IOException {
        JsonNode queries = objectMapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        List<NegativeResult> results = new ArrayList<>();
        for (JsonNode query : queries) {
            String queryText = query.path("query").asText();
            String clauseType = query.path("clauseType").asText(null);
            RetrievalResult retrieval = retrievalService.retrieve(queryText, clauseType, TENANT);
            boolean falsePositive = !retrieval.chunks().isEmpty() && !retrieval.degraded();
            double topScore = retrieval.chunks().isEmpty() ? 0D : retrieval.chunks().get(0).score();
            double rawTop = rawVectorTop(queryText, clauseType);
            results.add(new NegativeResult(query.path("id").asText(), query.path("split").asText(),
                    queryText, falsePositive, topScore, rawTop, retrieval.degraded(),
                    retrieval.degradeReason(), query.path("reason").asText()));
        }
        return List.copyOf(results);
    }

    /**
     * 纯向量路首位原始相似度，供阈值标定使用：RRF 归一化后首位恒为 1，无法与阈值比较（ADR 0004）。
     *
     * @param query      查询
     * @param clauseType 条款类型
     * @return 原始相似度；无结果返回 0
     */
    private double rawVectorTop(String query, String clauseType) {
        float[] vector = embeddingPort.embed(List.of(query)).get(0);
        List<RetrievedChunk> chunks = readPort.vectorSearch(toLiteral(vector), clauseType, TENANT, 1);
        return chunks.isEmpty() ? 0D : chunks.get(0).score();
    }

    private static String toLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    private void assertBaseline(List<MultiResult> results) {
        int expectedTotal = results.stream().mapToInt(MultiResult::expectedCount).sum();
        int matchedTotal = results.stream().mapToInt(MultiResult::matchedCount).sum();
        double recall = expectedTotal == 0 ? 0D : (double) matchedTotal / expectedTotal;
        assertThat(recall).as("上下文召回率（规范 12.2 目标 ≥75%%）当前为 %.3f", recall)
                .isGreaterThanOrEqualTo(0.75D);
    }

    private void writeReport(Path repoRoot, List<MultiResult> multi, List<NegativeResult> negatives,
                             long elapsedMillis) throws IOException {
        String date = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        int expectedTotal = multi.stream().mapToInt(MultiResult::expectedCount).sum();
        int matchedTotal = multi.stream().mapToInt(MultiResult::matchedCount).sum();
        double recall = expectedTotal == 0 ? 0D : (double) matchedTotal / expectedTotal;
        long falsePositives = negatives.stream().filter(NegativeResult::falsePositive).count();
        double fpRate = negatives.isEmpty() ? 0D : (double) falsePositives / negatives.size();

        StringBuilder md = new StringBuilder();
        md.append("# 检索扩展评估：上下文召回率与 hard negatives（第 10–11 周）\n\n");
        md.append("- 生成日期：").append(date).append('\n');
        md.append("- 嵌入模型：").append(embeddingProperties.getModel()).append("（")
                .append(embeddingProperties.getDimension()).append(" 维）\n");
        md.append("- 阈值配置：").append(retrievalProperties.getMinScore()).append("；TOP_K=").append(TOP_K)
                .append('\n');
        md.append("- 题集：多条款题 ").append(multi.size()).append(" 道；hard negatives ")
                .append(negatives.size()).append(" 道；耗时 ").append(elapsedMillis).append(" ms\n\n");
        md.append("## 上下文召回率（规范 12.2 目标 ≥75%）\n\n");
        md.append("| 口径 | 命中标注条款数 / 标注总数 | 召回率 |\n| --- | ---: | ---: |\n");
        md.append(String.format(Locale.ROOT, "| 全部多条款题 | %d / %d | %.1f%% |%n",
                matchedTotal, expectedTotal, recall * 100));
        md.append(String.format(Locale.ROOT, "| 开发集 | %d / %d | %.1f%% |%n",
                sumMatched(multi, "dev"), sumExpected(multi, "dev"), rate(multi, "dev") * 100));
        md.append(String.format(Locale.ROOT, "| 验证集 | %d / %d | %.1f%% |%n",
                sumMatched(multi, "val"), sumExpected(multi, "val"), rate(multi, "val") * 100));
        md.append('\n');
        md.append("## hard negatives 误报率（规范 9.4 回流回归）\n\n");
        md.append(String.format(Locale.ROOT, "- 误报定义：检索返回非空且未标记降级（即系统认为找到了依据）\n"));
        md.append(String.format(Locale.ROOT, "- 实测误报率：**%.1f%%**（%d / %d）\n\n", fpRate * 100, falsePositives,
                negatives.size()));
        md.append("| id | 口径 | 查询 | 误报 | 归一化首位分 | 原始向量首位分 | 降级 | 预期 |\n");
        md.append("| --- | --- | --- | --- | ---: | ---: | --- | --- |\n");
        for (NegativeResult result : negatives) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %.3f | %.3f | %s | %s |%n", result.id(),
                    result.split(), result.query(), result.falsePositive() ? "是" : "否", result.topScore(),
                    result.rawTop(), result.degraded() ? "是" : "否", result.reason()));
        }
        md.append('\n');
        md.append("## 多条款分题明细\n\n");
        md.append("| id | 口径 | 期望数 | 命中数 | 任一条命中 | 降级 |\n| --- | --- | ---: | ---: | --- | --- |\n");
        for (MultiResult result : multi) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %d | %d | %s | %s |%n", result.id(), result.split(),
                    result.expectedCount(), result.matchedCount(), result.anyHit() ? "是" : "否",
                    result.degraded() ? "是" : "否"));
        }
        md.append('\n').append("## 说明\n\n");
        md.append("- 数字全部来自实测；hard negatives 为虚构查询，语料中不存在对应条款（规范 12.1）。\n");
        md.append("- 误报率不设门槛，作为下一轮阈值与召回策略调整的基线；调整阈值属检索变更，须按 6.7 重跑。\n");
        md.append("- 忠实度抽检与幻觉率对比不在本报告内，见 `docs/compliance/week10-11.md` 未覆盖项。\n");

        Files.createDirectories(repoRoot.resolve("docs/eval"));
        Files.writeString(repoRoot.resolve("docs/eval/context-recall-" + date + ".md"), md.toString(),
                StandardCharsets.UTF_8);
    }

    private static int sumMatched(List<MultiResult> results, String split) {
        return results.stream().filter(r -> split.equals(r.split())).mapToInt(MultiResult::matchedCount).sum();
    }

    private static int sumExpected(List<MultiResult> results, String split) {
        return results.stream().filter(r -> split.equals(r.split())).mapToInt(MultiResult::expectedCount).sum();
    }

    private static double rate(List<MultiResult> results, String split) {
        int expected = sumExpected(results, split);
        return expected == 0 ? 0D : (double) sumMatched(results, split) / expected;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 多条款题结果。
     *
     * @param id           题号
     * @param split        划分
     * @param expectedCount 标注条款数
     * @param matchedCount 命中条款数
     * @param anyHit       是否任一条命中
     * @param degraded     是否降级
     */
    private record MultiResult(String id, String split, int expectedCount, int matchedCount, boolean anyHit,
                               boolean degraded) {
    }

    /**
     * hard negative 结果。
     *
     * @param id            题号
     * @param split         划分
     * @param query         查询
     * @param falsePositive 是否误报
     * @param topScore      归一化首位分
     * @param rawTop        原始向量首位相似度
     * @param degraded      是否降级
     * @param degradeReason 降级原因
     * @param reason        预期说明
     */
    private record NegativeResult(String id, String split, String query, boolean falsePositive, double topScore,
                                  double rawTop, boolean degraded, String degradeReason, String reason) {
    }
}
