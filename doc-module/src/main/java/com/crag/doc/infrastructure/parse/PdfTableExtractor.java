package com.crag.doc.infrastructure.parse;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.domain.model.ParsedTable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PDF 表格二次还原（规范 5.2）：按文本坐标重建行列，不使用 Tika 的表格输出。
 *
 * <p>实现思路：按基线把字符归并为文本行，行内水平间距超过阈值即切分为单元格；连续多行且列起始坐标对齐时
 * 判定为表格，并按列锚点回填。该做法对“有列对齐”的版式有效，对无对齐的流式排版会退化为不识别表格：
 * 宁可漏识别也不产出错位行列，错位表格会污染后续条款切分。</p>
 */
@Component
public class PdfTableExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTableExtractor.class);

    /** 同一行的基线容差（单位：pt）。 */
    private static final float LINE_TOLERANCE_POINTS = 2.5F;
    /** 单元格切分的最小水平间距（单位：pt）。 */
    private static final float CELL_GAP_POINTS = 8F;
    /** 列锚点对齐容差（单位：pt）。 */
    private static final float COLUMN_ALIGN_TOLERANCE_POINTS = 6F;
    /** 表格最少行数。 */
    private static final int MIN_TABLE_ROWS = 2;

    /**
     * 抽取全部表格。
     *
     * @param file PDF 文件
     * @return 表格列表
     */
    public List<ParsedTable> extract(Path file) {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            GlyphCollectingStripper stripper = new GlyphCollectingStripper();
            stripper.setSortByPosition(true);
            stripper.getText(document);
            return stripper.buildTables();
        } catch (IOException e) {
            log.warn("PDF 表格抽取失败，按无表格处理 file={}", file.getFileName(), e);
            return List.of();
        }
    }

    /**
     * 读取页数。
     *
     * @param file PDF 文件
     * @return 页数；读取失败返回 0
     */
    public int pageCount(Path file) {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            log.warn("PDF 页数读取失败 file={}", file.getFileName(), e);
            return 0;
        }
    }

    /**
     * 供调用方复用的页数断言：读取失败按解析失败处理。
     *
     * @param file PDF 文件
     * @return 页数
     */
    public int requirePageCount(Path file) {
        int pages = pageCount(file);
        if (pages <= 0) {
            throw BizException.of(ErrorCode.PARSE_FAILED, "pdf page count unavailable");
        }
        return pages;
    }

    /**
     * 采集全部字符坐标，再按基线归并为行。
     */
    static final class GlyphCollectingStripper extends PDFTextStripper {

        private final transient List<Glyph> glyphs = new ArrayList<>();
        private int currentPage;

        /**
         * PDFTextStripper 构造器声明了 IOException，子类必须显式透传。
         *
         * @throws IOException 初始化失败
         */
        GlyphCollectingStripper() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            currentPage++;
            super.startPage(page);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            String unicode = text.getUnicode();
            if (unicode == null || unicode.isEmpty()) {
                return;
            }
            glyphs.add(new Glyph(currentPage, text.getXDirAdj(), text.getYDirAdj(),
                    text.getWidthDirAdj(), unicode));
        }

        /**
         * 组装表格。
         *
         * @return 表格列表
         */
        List<ParsedTable> buildTables() {
            List<Line> lines = groupLines();
            List<ParsedTable> tables = new ArrayList<>();
            int index = 0;
            int cursor = 0;
            while (cursor < lines.size()) {
                Line head = lines.get(cursor);
                if (head.cells().size() < 2) {
                    cursor++;
                    continue;
                }
                List<Line> group = new ArrayList<>();
                group.add(head);
                int next = cursor + 1;
                while (next < lines.size()) {
                    Line candidate = lines.get(next);
                    if (candidate.pageNo() != head.pageNo() || candidate.cells().size() < 2
                            || !aligned(group.get(group.size() - 1), candidate)) {
                        break;
                    }
                    group.add(candidate);
                    next++;
                }
                if (group.size() >= MIN_TABLE_ROWS) {
                    tables.add(toTable(index++, head.pageNo(), group));
                    cursor = next;
                } else {
                    cursor++;
                }
            }
            return tables;
        }

        private List<Line> groupLines() {
            List<Glyph> sorted = new ArrayList<>(glyphs);
            sorted.sort(Comparator.comparingInt(Glyph::pageNo)
                    .thenComparingDouble(Glyph::y)
                    .thenComparingDouble(Glyph::x));
            List<Line> lines = new ArrayList<>();
            int cursor = 0;
            while (cursor < sorted.size()) {
                Glyph head = sorted.get(cursor);
                List<Glyph> band = new ArrayList<>();
                int next = cursor;
                while (next < sorted.size() && sorted.get(next).pageNo() == head.pageNo()
                        && Math.abs(sorted.get(next).y() - head.y()) <= LINE_TOLERANCE_POINTS) {
                    band.add(sorted.get(next));
                    next++;
                }
                Line line = toLine(head.pageNo(), band);
                if (!line.cells().isEmpty()) {
                    lines.add(line);
                }
                cursor = next;
            }
            return lines;
        }

        private Line toLine(int pageNo, List<Glyph> band) {
            List<Cell> cells = new ArrayList<>();
            StringBuilder buffer = new StringBuilder();
            float cellStart = band.get(0).x();
            float lastEnd = cellStart;
            for (Glyph glyph : band) {
                if (glyph.text().isBlank()) {
                    lastEnd = glyph.x() + glyph.width();
                    continue;
                }
                if (buffer.length() > 0 && glyph.x() - lastEnd > CELL_GAP_POINTS) {
                    cells.add(new Cell(cellStart, buffer.toString().strip()));
                    buffer.setLength(0);
                    cellStart = glyph.x();
                }
                buffer.append(glyph.text());
                lastEnd = glyph.x() + glyph.width();
            }
            if (buffer.length() > 0) {
                cells.add(new Cell(cellStart, buffer.toString().strip()));
            }
            cells.removeIf(cell -> cell.text().isEmpty());
            return new Line(pageNo, cells);
        }

        private boolean aligned(Line previous, Line current) {
            if (previous.cells().size() != current.cells().size()) {
                return false;
            }
            int matched = 0;
            for (int i = 0; i < previous.cells().size(); i++) {
                if (Math.abs(previous.cells().get(i).x() - current.cells().get(i).x())
                        <= COLUMN_ALIGN_TOLERANCE_POINTS) {
                    matched++;
                }
            }
            return matched * 100 / previous.cells().size() >= 60;
        }

        private ParsedTable toTable(int index, int pageNo, List<Line> group) {
            List<Float> anchors = new ArrayList<>();
            for (Cell cell : group.get(0).cells()) {
                anchors.add(cell.x());
            }
            List<List<String>> rows = new ArrayList<>(group.size());
            for (Line line : group) {
                List<String> row = new ArrayList<>();
                for (int i = 0; i < anchors.size(); i++) {
                    row.add("");
                }
                for (Cell cell : line.cells()) {
                    int column = nearestAnchor(anchors, cell.x());
                    if (column >= 0 && column < row.size()) {
                        row.set(column, row.get(column).isEmpty()
                                ? cell.text()
                                : row.get(column) + " " + cell.text());
                    }
                }
                rows.add(row);
            }
            return new ParsedTable(index, pageNo, "", rows);
        }

        private int nearestAnchor(List<Float> anchors, float x) {
            int best = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int i = 0; i < anchors.size(); i++) {
                float distance = Math.abs(anchors.get(i) - x);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            return bestDistance <= COLUMN_ALIGN_TOLERANCE_POINTS * 2 ? best : -1;
        }

        /**
         * 单个字符坐标。
         *
         * @param pageNo 页码
         * @param x      横坐标
         * @param y      纵坐标（自上而下）
         * @param width  宽度
         * @param text   字符
         */
        record Glyph(int pageNo, float x, float y, float width, String text) {
        }

        /**
         * 文本行。
         *
         * @param pageNo 页码
         * @param cells  单元格
         */
        record Line(int pageNo, List<Cell> cells) {
        }

        /**
         * 单元格。
         *
         * @param x    起始横坐标
         * @param text 文本
         */
        record Cell(float x, String text) {
        }
    }
}
