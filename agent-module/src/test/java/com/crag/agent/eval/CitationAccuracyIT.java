package com.crag.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.text.TextNormalizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 引用准确率评估（规范 12.3，目标 ≥95%）。
 *
 * <p>两部分：</p>
 * <ol>
 *   <li><b>机制校验</b>：从知识库切片取原文，构造“正确引用”与“篡改引用”，确认校验器分别通过/拒绝；
 *       这部分保证校验逻辑本身不会把错误引用放过去（可自动化、样本可稳定造）。</li>
 *   <li><b>存量复核</b>：扫描 {@code audit_risk_item} 的已产出风险，按来源切片重新执行确定性校验，
 *       与入库时记录的 {@code citation_verified} 比对；这是对“系统实际产出”的准确率测量。</li>
 * </ol>
 *
 * <p>存量样本量取决于已跑过的审核次数，报告如实标注 N；样本不足时不做达标断言（规范 12.6）。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "crag.it", matches = "true")
class CitationAccuracyIT {

    private static final long TENANT = 1L;
    private static final int MECHANISM_SAMPLES = 20;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("引用准确率：机制校验 100% + 存量风险项复核")
    void measuresCitationAccuracy() throws IOException {
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        MechanismResult mechanism = checkMechanism();
        StoredResult stored = checkStoredRisks();
        writeReport(repoRoot, mechanism, stored);

        assertThat(mechanism.passed()).as("引用校验机制必须全部通过").isEqualTo(mechanism.total());
        if (stored.total() >= 20) {
            assertThat(stored.accuracy()).as("存量引用准确率（规范 12.3 目标 ≥95%%）当前为 %.3f", stored.accuracy())
                    .isGreaterThanOrEqualTo(0.95D);
        }
    }

    private MechanismResult checkMechanism() {
        List<Map<String, Object>> chunks = jdbcTemplate.queryForList(
                "SELECT chunk_key, chunk_text FROM kb_chunk WHERE tenant_id = ? AND deleted = 0 "
                        + "AND length(chunk_text) >= 40 ORDER BY id LIMIT ?", TENANT, MECHANISM_SAMPLES);
        int total = 0;
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Map<String, Object> row : chunks) {
            String key = String.valueOf(row.get("chunk_key"));
            String text = String.valueOf(row.get("chunk_text"));
            // 正确引用：取文本中段 20 字（避免与标题重合）
            String correct = text.substring(10, Math.min(text.length(), 30));
            total++;
            if (TextNormalizer.containsNormalized(text, correct)) {
                passed++;
            } else {
                failures.add(key + ": 正确引用被判失败");
            }
            // 篡改引用：把最后一个字替换成“噸”，必须被拒绝
            String tampered = correct.substring(0, correct.length() - 1) + "噸";
            total++;
            if (!TextNormalizer.containsNormalized(text, tampered)) {
                passed++;
            } else {
                failures.add(key + ": 篡改引用未被拒绝");
            }
        }
        return new MechanismResult(total, passed, failures);
    }

    private StoredResult checkStoredRisks() {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id, source_chunk_key, source_excerpt, citation_verified FROM audit_risk_item "
                        + "WHERE tenant_id = ? AND deleted = 0 AND source_excerpt IS NOT NULL "
                        + "AND source_chunk_key IS NOT NULL ORDER BY id", TENANT);
        int total = 0;
        int consistent = 0;
        int mismatch = 0;
        List<String> details = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String key = String.valueOf(item.get("source_chunk_key"));
            String excerpt = String.valueOf(item.get("source_excerpt"));
            String sourceText = resolveSourceText(key);
            if (sourceText == null) {
                details.add(item.get("id") + ": 来源切片不存在（" + key + "）");
                mismatch++;
                total++;
                continue;
            }
            boolean actual = TextNormalizer.containsNormalized(sourceText, excerpt);
            boolean recorded = ((Number) item.get("citation_verified")).intValue() == 1;
            total++;
            if (actual == recorded) {
                consistent++;
            } else {
                mismatch++;
                details.add(item.get("id") + ": 记录=" + recorded + " 实测=" + actual + " key=" + key);
            }
        }
        return new StoredResult(total, consistent, mismatch, details);
    }

    private String resolveSourceText(String chunkKey) {
        if (chunkKey.startsWith("contract:")) {
            String[] parts = chunkKey.substring("contract:".length()).split("#");
            if (parts.length != 2) {
                return null;
            }
            List<String> texts = jdbcTemplate.queryForList(
                    "SELECT chunk_text FROM contract_chunk WHERE contract_id = ? AND full_text_index = ? "
                            + "AND deleted = 0", String.class, Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
            return texts.isEmpty() ? null : texts.get(0);
        }
        List<String> texts = jdbcTemplate.queryForList(
                "SELECT chunk_text FROM kb_chunk WHERE chunk_key = ? AND deleted = 0", String.class, chunkKey);
        return texts.isEmpty() ? null : texts.get(0);
    }

    private void writeReport(Path repoRoot, MechanismResult mechanism, StoredResult stored) throws IOException {
        String date = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        double mechanismRate = mechanism.total() == 0 ? 0D : (double) mechanism.passed() / mechanism.total();
        double storedRate = stored.total() == 0 ? 0D : (double) stored.consistent() / stored.total();

        StringBuilder md = new StringBuilder();
        md.append("# 引用准确率评估报告（第 10–11 周）\n\n");
        md.append("- 生成日期：").append(date).append('\n');
        md.append("- 校验方式：确定性字符串包含校验，校验前统一归一化（去空白/全半角/标点），规范 7.5、12.3\n");
        md.append("- 样本来源：知识库切片构造 + `audit_risk_item` 存量风险项\n\n");
        md.append("## 1. 机制校验（正确引用必须通过、篡改引用必须拒绝）\n\n");
        md.append(String.format(Locale.ROOT, "- 通过：%d / %d（%.1f%%）\n", mechanism.passed(), mechanism.total(),
                mechanismRate * 100));
        if (!mechanism.failures().isEmpty()) {
            for (String failure : mechanism.failures()) {
                md.append("  - 失败：").append(failure).append('\n');
            }
        }
        md.append('\n');
        md.append("## 2. 存量风险项复核\n\n");
        md.append(String.format(Locale.ROOT, "- 样本量 N=%d；与入库标记一致：%d（%.1f%%）；不一致：%d\n",
                stored.total(), stored.consistent(), storedRate * 100, stored.mismatch()));
        if (stored.total() < 20) {
            md.append("- **样本不足 20，不据此宣称达标**（规范 12.6），仅作为机制回归；扩样后需重跑。\n");
        }
        if (!stored.details().isEmpty()) {
            md.append("\n差异明细：\n\n");
            for (String detail : stored.details()) {
                md.append("- ").append(detail).append('\n');
            }
        }
        md.append('\n').append("## 3. 口径\n\n");
        md.append("- 引用准确率 = 引用片段逐字存在于声称的来源切片 / 声称的引用总数。\n");
        md.append("- 存量复核以数据库中的切片文本为准，不依赖模型；来源切片缺失按不一致计。\n");

        Files.createDirectories(repoRoot.resolve("docs/eval"));
        Files.writeString(repoRoot.resolve("docs/eval/citation-" + date + ".md"), md.toString(),
                StandardCharsets.UTF_8);
    }

    /**
     * 机制校验结果。
     *
     * @param total    样本数
     * @param passed   通过数
     * @param failures 失败明细
     */
    private record MechanismResult(int total, int passed, List<String> failures) {
    }

    /**
     * 存量复核结果。
     *
     * @param total      样本数
     * @param consistent 一致数
     * @param mismatch   不一致数
     * @param details    差异明细
     */
    private record StoredResult(int total, int consistent, int mismatch, List<String> details) {

        /**
         * 一致率。
         *
         * @return 0–1
         */
        double accuracy() {
            return total == 0 ? 0D : (double) consistent / total;
        }
    }
}
