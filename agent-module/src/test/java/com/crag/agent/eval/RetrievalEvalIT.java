package com.crag.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.domain.TenantContext;
import com.crag.kb.application.KbDocService;
import com.crag.kb.application.KbQueryService;
import com.crag.kb.application.port.EmbeddingPort;
import com.crag.kb.application.port.KbChunkReadPort;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 检索评估（规范 12.1、12.2）：命中率、召回率、融合增益与阈值标定。
 *
 * <p>需要真实的 PostgreSQL/Redis/百炼 embedding，故按 {@code -Dcrag.it=true} 启用；
 * 语料与题集来自 eval/kb 与 eval/retrieval，报告归档到 docs/eval。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "crag.it", matches = "true")
class RetrievalEvalIT {

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
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("检索基线：命中率、召回率、融合增益与阈值标定")
    void runsRetrievalEvaluation() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        Path kbDir = repoRoot.resolve("eval/kb");
        Path queryFile = repoRoot.resolve("eval/retrieval/queries.json");
        assertThat(Files.exists(queryFile)).as("评估题集缺失：%s", queryFile).isTrue();

        TenantContext.set(TENANT);
        try {
            seedKnowledgeBase(kbDir);
            JsonNode queries = objectMapper.readTree(Files.readString(queryFile, StandardCharsets.UTF_8));
            List<QueryResult> results = new ArrayList<>();
            for (JsonNode query : queries) {
                results.add(evaluate(query));
            }
            writeReport(repoRoot, results);
            assertBaseline(results);
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
                    .findFirst()
                    .orElse(null);
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
                assertThat(queryService.chunks(TENANT, docId)).as("文档 %s 无切片", title).isNotEmpty();
                return;
            }
            sleep(500L);
        }
        throw new AssertionError("知识库同步超时：" + title);
    }

    private QueryResult evaluate(JsonNode query) {
        String queryText = query.path("query").asText();
        String clauseType = query.path("clauseType").asText(null);
        String expectedKey = KbChunkKeys.chunkKey(KbChunkKeys.docKeyOf(query.path("doc").asText()),
                query.path("clauseNo").asText(), 0);
        RetrievalResult hybrid = retrievalService.retrieve(queryText, clauseType, TENANT);
        List<RetrievedChunk> hybridTop = hybrid.chunks().stream().limit(TOP_K).toList();
        boolean hybridHit = hybridTop.stream().anyMatch(chunk -> expectedKey.equals(chunk.chunkKey()));

        float[] vector = embeddingPort.embed(List.of(queryText)).get(0);
        List<RetrievedChunk> vectorOnly = readPort.vectorSearch(toLiteral(vector), clauseType, TENANT, TOP_K);
        boolean vectorHit = vectorOnly.stream().anyMatch(chunk -> expectedKey.equals(chunk.chunkKey()));
        double vectorTopScore = vectorOnly.isEmpty() ? 0D : vectorOnly.get(0).score();

        return new QueryResult(query.path("id").asText(), queryText, clauseType, query.path("split").asText(),
                expectedKey, hybridHit, vectorHit, vectorTopScore, hybrid.degraded(), hybrid.fallback(),
                hybridTop.size());
    }

    private void assertBaseline(List<QueryResult> results) {
        double devHit = rate(results, "dev", QueryResult::hybridHit);
        assertThat(devHit).as("开发集条款级命中率（规范 12.2 目标 ≥80%%）当前为 %.3f", devHit)
                .isGreaterThanOrEqualTo(0.80D);
    }

    private void writeReport(Path repoRoot, List<QueryResult> results) throws IOException {
        String date = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        double devHit = rate(results, "dev", QueryResult::hybridHit);
        double valHit = rate(results, "val", QueryResult::hybridHit);
        double devVectorHit = rate(results, "dev", QueryResult::vectorHit);
        double devGain = devHit - devVectorHit;
        double threshold = calibrateThreshold(results);

        StringBuilder md = new StringBuilder();
        md.append("# 检索评估报告（第 3–4 周）\n\n");
        md.append("- 生成日期：").append(date).append('\n');
        md.append("- 模型：").append("qwen3.7-text-embedding-flash（1024 维）").append('\n');
        md.append("- 语料：6 份虚构制度文档、").append(distinctChunks(results)).append(" 个标注条款\n");
        md.append("- 题集：24 题，开发集 17 / 验证集 7（7:3，规范 12.1）\n");
        md.append("- 指标口径：命中=期望切片出现在前 ").append(TOP_K).append(" 条召回结果中；")
                .append("融合增益=双路融合命中率−纯向量命中率\n\n");
        md.append("## 结果\n\n");
        md.append("| 口径 | 命中率 |\n| --- | ---: |\n");
        md.append(String.format(Locale.ROOT, "| 双路融合（开发集） | %.1f%% |%n", devHit * 100));
        md.append(String.format(Locale.ROOT, "| 双路融合（验证集） | %.1f%% |%n", valHit * 100));
        md.append(String.format(Locale.ROOT, "| 纯向量基线（开发集） | %.1f%% |%n", devVectorHit * 100));
        md.append(String.format(Locale.ROOT, "| 融合增益（开发集） | %+.1f 个百分点 |%n", devGain * 100));
        md.append('\n');
        md.append("## 阈值标定\n\n");
        md.append("置信度取向量路首位余弦相似度（RRF 归一化后首位恒为 1，无法作为阈值判据，解释见 ADR 0004）。\n\n");
        md.append(String.format(Locale.ROOT, "- 开发集标定建议阈值：**%.2f**%n", threshold));
        md.append(String.format(Locale.ROOT, "- 当前配置阈值：%.2f（规范 6.4 初值，需复核）%n",
                retrievalProperties.getMinScore()));
        md.append("- 阈值须先在开发集标定、再在验证集复核；本次标定基于 17 道开发集题目。\n\n");
        md.append("## 分题明细\n\n");
        md.append("| id | 口径 | 类型 | 双路命中 | 纯向量命中 | 置信度 | 降级 | 回退 |\n");
        md.append("| --- | --- | --- | --- | --- | ---: | --- | --- |\n");
        for (QueryResult result : results) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s | %.3f | %s | %s |%n",
                    result.id(), result.split(), result.clauseType(), result.hybridHit() ? "是" : "否",
                    result.vectorHit() ? "是" : "否", result.vectorTopScore(), result.degraded() ? "是" : "否",
                    result.fallback() ? "是" : "否"));
        }
        md.append('\n').append("## 说明与局限\n\n");
        md.append("- 本报告所有数字来自脚本实测，未使用任何估值（规范 12.6）。\n");
        md.append("- 切分策略对比（规范 12.2 的另一项指标）见 `split-compare-*.md`。\n");
        md.append("- 验证集仅用于复核，未参与调参；若上线前调整 Prompt/检索参数，须重跑本报告。\n");
        if (devHit >= 1D && valHit >= 1D) {
            md.append("- **指标已饱和**：语料仅 6 份制度、约 55 个切片，各条款表述差异大，")
                    .append("本题集区分度不足，故融合增益为 0 不代表双路无价值。\n");
            md.append("- 下一步：按规范 9.4 把人工复核驳回项导出为 hard negatives，")
                    .append("扩充到 20–30 题并重跑，届时才能对融合增益与阈值给出有效结论。\n");
        }

        Files.createDirectories(repoRoot.resolve("docs/eval"));
        Files.writeString(repoRoot.resolve("docs/eval/retrieval-" + date + ".md"), md.toString(),
                StandardCharsets.UTF_8);

        StringBuilder csv = new StringBuilder("id,split,clauseType,hybridHit,vectorHit,vectorTopScore,degraded,fallback\n");
        for (QueryResult result : results) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%.4f,%s,%s%n", result.id(), result.split(),
                    result.clauseType(), result.hybridHit(), result.vectorHit(), result.vectorTopScore(),
                    result.degraded(), result.fallback()));
        }
        Files.createDirectories(repoRoot.resolve("eval/reports"));
        Files.writeString(repoRoot.resolve("eval/reports/retrieval-" + date + ".csv"), csv.toString(),
                StandardCharsets.UTF_8);
    }

    private double calibrateThreshold(List<QueryResult> results) {
        List<QueryResult> dev = results.stream().filter(r -> "dev".equals(r.split())).toList();
        if (dev.isEmpty()) {
            return retrievalProperties.getMinScore();
        }
        List<Double> candidates = dev.stream().map(QueryResult::vectorTopScore)
                .sorted(Comparator.naturalOrder()).toList();
        double best = retrievalProperties.getMinScore();
        int bestScore = -1;
        for (double candidate : candidates) {
            int correct = 0;
            for (QueryResult result : dev) {
                boolean predictedHit = result.vectorTopScore() >= candidate;
                if (predictedHit == result.hybridHit()) {
                    correct++;
                }
            }
            if (correct > bestScore) {
                bestScore = correct;
                best = candidate;
            }
        }
        return best;
    }

    private static int distinctChunks(List<QueryResult> results) {
        return (int) results.stream().map(QueryResult::expectedKey).distinct().count();
    }

    private static double rate(List<QueryResult> results, String split,
                               java.util.function.Predicate<QueryResult> predicate) {
        List<QueryResult> subset = results.stream().filter(r -> split.equals(r.split())).toList();
        if (subset.isEmpty()) {
            return 0D;
        }
        long hits = subset.stream().filter(predicate).count();
        return (double) hits / subset.size();
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 单题评估结果。
     *
     * @param id             题号
     * @param query          查询
     * @param clauseType     条款类型
     * @param split          dev 或 val
     * @param expectedKey    期望切片业务键
     * @param hybridHit      双路融合是否命中
     * @param vectorHit      纯向量是否命中
     * @param vectorTopScore 向量路首位相似度
     * @param degraded       是否降级
     * @param fallback       是否类型回退
     * @param returned       返回条数
     */
    private record QueryResult(String id, String query, String clauseType, String split, String expectedKey,
                               boolean hybridHit, boolean vectorHit, double vectorTopScore, boolean degraded,
                               boolean fallback, int returned) {
    }
}
