package com.crag.doc.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.text.ClauseBlock;
import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.common.text.ParsedClause;
import com.crag.doc.domain.model.ParsedDocument;
import com.crag.common.text.ClauseSplitter;
import com.crag.common.text.ClauseTypeClassifier;
import com.crag.doc.domain.split.FixedWindowSplitter;
import com.crag.common.text.SplitConfig;
import com.crag.doc.infrastructure.parse.DocxTableExtractor;
import com.crag.doc.infrastructure.parse.PdfTableExtractor;
import com.crag.doc.infrastructure.parse.TikaDocumentParsingAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 切分策略对比（规范 12.2：条款级切分与固定窗口切分的前五命中率差值不低于 8 个百分点）。
 *
 * <p>第 1–2 周尚无检索能力，本报告先测量可直接确定性统计的结构性指标：跨条款切断数、被切断条款比例、
 * 切片长度分布与原文覆盖度；命中率差值随第 3–4 周双路召回与评估集一并测量。
 * 报告归档到 docs/eval/，机器可读明细写入 eval/reports/。</p>
 */
class SplitComparisonTest {

    private static final Path REPO_ROOT = Path.of(System.getProperty("crag.workspace.root", "."));
    private static final Path SAMPLES_DIR = REPO_ROOT.resolve("eval").resolve("samples");
    private static final List<String> SAMPLES = List.of(
            "purchase_contract.docx", "service_contract.docx", "nda_contract.docx");

    private final SplitConfig config = SplitConfig.defaults();
    private final ClauseSplitter splitter = new ClauseSplitter(config, new ClauseTypeClassifier());
    private final FixedWindowSplitter fixedSplitter = new FixedWindowSplitter(config);
    private final TikaDocumentParsingAdapter parser =
            new TikaDocumentParsingAdapter(new DocxTableExtractor(), new PdfTableExtractor());

    @Test
    @DisplayName("条款级切分与固定窗口切分的结构性指标对比")
    void comparesClauseLevelAndFixedWindowSplitting() throws IOException {
        List<SampleMetrics> results = new ArrayList<>();
        for (String sample : SAMPLES) {
            Path file = SAMPLES_DIR.resolve(sample);
            assertThat(Files.exists(file)).as("样例合同缺失：%s", file).isTrue();
            ParsedDocument document = parser.parse(file, DocumentFormatEnum.DOCX);
            String text = document.text();
            List<ClauseBlock> blocks = splitter.splitIntoBlocks(text);
            assertThat(blocks).as("样例 %s 未识别出任何条款", sample).isNotEmpty();

            List<int[]> clauseSpans = splitter.split(text).stream()
                    .map(clause -> new int[]{clause.charStart(), clause.charEnd()})
                    .toList();
            List<int[]> windowSpans = fixedSplitter.split(text).stream()
                    .map(window -> new int[]{window.start(), window.end()})
                    .toList();

            results.add(new SampleMetrics(sample, text.length(), blocks,
                    summarize("条款级切分", text, blocks, clauseSpans),
                    summarize("固定窗口切分", text, blocks, windowSpans)));
            if ("purchase_contract.docx".equals(sample)) {
                assertThat(document.tables()).as("采购合同样例中的设备清单表应被二次还原").isNotEmpty();
            }
        }

        for (SampleMetrics metrics : results) {
            assertThat(metrics.clauseLevel().crossBoundarySpans())
                    .as("条款级切分不得跨越条款边界：%s", metrics.sample())
                    .isZero();
            assertThat(metrics.clauseLevel().uncoveredNonWhitespace())
                    .as("条款级切分必须覆盖原文：%s", metrics.sample())
                    .isZero();
        }
        assertThat(results.stream().mapToInt(m -> m.fixedWindow().crossBoundarySpans()).sum())
                .as("固定窗口切分应当出现跨条款切断，才能证明对比有效")
                .isPositive();

        writeReports(results);
    }

    private SpanSummary summarize(String strategy, String text, List<ClauseBlock> blocks, List<int[]> spans) {
        int crossBoundary = 0;
        java.util.Set<Integer> fragmentedBlockIndexes = new java.util.HashSet<>();
        int maxChars = 0;
        long totalChars = 0;
        boolean[] covered = new boolean[text.length()];
        for (int[] span : spans) {
            int overlapping = 0;
            for (ClauseBlock block : blocks) {
                if (block.charStart() < span[1] && block.charEnd() > span[0]) {
                    overlapping++;
                }
            }
            if (overlapping > 1) {
                crossBoundary++;
            }
            totalChars += span[1] - span[0];
            maxChars = Math.max(maxChars, span[1] - span[0]);
            for (int i = span[0]; i < span[1]; i++) {
                covered[i] = true;
            }
            for (ClauseBlock block : blocks) {
                if (span[0] > block.charStart() && span[0] < block.charEnd()) {
                    fragmentedBlockIndexes.add(block.blockIndex());
                    break;
                }
            }
        }
        int uncoveredNonWhitespace = 0;
        for (int i = 0; i < covered.length; i++) {
            if (!covered[i] && !Character.isWhitespace(text.charAt(i))) {
                uncoveredNonWhitespace++;
            }
        }
        return new SpanSummary(strategy, spans.size(), blocks.size(), crossBoundary, fragmentedBlockIndexes.size(),
                spans.isEmpty() ? 0 : (int) (totalChars / spans.size()), maxChars, uncoveredNonWhitespace);
    }

    private void writeReports(List<SampleMetrics> results) throws IOException {
        String date = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        Files.createDirectories(REPO_ROOT.resolve("docs").resolve("eval"));
        Files.createDirectories(REPO_ROOT.resolve("eval").resolve("reports"));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# 切分策略对比报告（第 1–2 周）\n\n");
        markdown.append("- 生成日期：").append(date).append('\n');
        markdown.append("- 语料：`eval/samples/` 中 ").append(results.size()).append(" 份人工编写的脱敏样例合同\n");
        markdown.append("- 切分配置：窗口 ").append(config.maxClauseLengthChars()).append(" 字，重叠 ")
                .append((int) (config.overlapRatio() * 100)).append("%（两种策略使用完全相同的参数）\n");
        markdown.append("- 指标口径：跨条款切断数＝跨越两条及以上条款的切片数；被切断条款数＝内部存在切片边界的条款数；")
                .append("覆盖缺口＝未被任何切片覆盖的非空白字符数\n\n");
        markdown.append("| 样例 | 策略 | 条款数 | 切片数 | 平均字数 | 最大字数 | 跨条款切断数 | 被切断条款数 | 覆盖缺口 |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (SampleMetrics metrics : results) {
            appendRow(markdown, metrics.sample(), metrics.clauseLevel());
            appendRow(markdown, metrics.sample(), metrics.fixedWindow());
        }
        markdown.append('\n').append("## 结论\n\n");
        markdown.append("- 条款级切分的跨条款切断数为 0，原文覆盖无缺口：任何结论都能回溯到完整条款。\n");
        markdown.append("- 固定窗口切分出现跨条款切断，条款完整性不可保证，与规范 5.3 的判断一致。\n");
        markdown.append("- 规范 12.2 的「前五命中率差值不低于 8 个百分点」依赖双路召回与评估集，")
                .append("第 3–4 周测得后补充本报告。\n");
        markdown.append("- 本报告指标均由脚本实测，未使用任何估值（规范 12.6）。\n");
        Files.writeString(REPO_ROOT.resolve("docs").resolve("eval")
                .resolve("split-compare-" + date + ".md"), markdown.toString(), StandardCharsets.UTF_8);

        StringBuilder csv = new StringBuilder("sample,strategy,clauses,chunks,avg_chars,max_chars,cross_boundary,"
                + "fragmented_clauses,uncovered_non_whitespace\n");
        for (SampleMetrics metrics : results) {
            csv.append(row(metrics.sample(), metrics.clauseLevel()));
            csv.append(row(metrics.sample(), metrics.fixedWindow()));
        }
        Files.writeString(REPO_ROOT.resolve("eval").resolve("reports")
                .resolve("split-compare-" + date + ".csv"), csv.toString(), StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder markdown, String sample, SpanSummary summary) {
        markdown.append("| ").append(sample).append(" | ").append(summary.strategy())
                .append(" | ").append(summary.clauseCount())
                .append(" | ").append(summary.chunkCount())
                .append(" | ").append(summary.avgChars())
                .append(" | ").append(summary.maxChars())
                .append(" | ").append(summary.crossBoundarySpans())
                .append(" | ").append(summary.fragmentedClauses())
                .append(" | ").append(summary.uncoveredNonWhitespace())
                .append(" |\n");
    }

    private static String row(String sample, SpanSummary summary) {
        return sample + "," + summary.strategy() + "," + summary.clauseCount() + "," + summary.chunkCount()
                + "," + summary.avgChars() + "," + summary.maxChars() + "," + summary.crossBoundarySpans()
                + "," + summary.fragmentedClauses() + "," + summary.uncoveredNonWhitespace() + "\n";
    }

    /**
     * 单份样例的对比结果。
     *
     * @param sample      文件名
     * @param textLength  正文字符数
     * @param blocks      条款块
     * @param clauseLevel 条款级切分指标
     * @param fixedWindow 固定窗口切分指标
     */
    private record SampleMetrics(String sample, int textLength, List<ClauseBlock> blocks,
                                 SpanSummary clauseLevel, SpanSummary fixedWindow) {
    }

    /**
     * 单策略指标。
     *
     * @param strategy              策略名
     * @param chunkCount            切片数
     * @param clauseCount           条款数
     * @param crossBoundarySpans    跨条款切断的切片数
     * @param fragmentedClauses     被切断条款数
     * @param avgChars              平均切片字数
     * @param maxChars              最大切片字数
     * @param uncoveredNonWhitespace 覆盖缺口
     */
    private record SpanSummary(String strategy, int chunkCount, int clauseCount, int crossBoundarySpans,
                               int fragmentedClauses, int avgChars, int maxChars, int uncoveredNonWhitespace) {
    }
}
