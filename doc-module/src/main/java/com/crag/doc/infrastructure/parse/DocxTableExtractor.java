package com.crag.doc.infrastructure.parse;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DOCX 表格二次还原（规范 5.2：表格必须独立抽取并二次还原行列结构，禁止直接使用 Tika 的表格输出）。
 *
 * <p>按 WordprocessingML 的 {@code gridSpan} 与 {@code vMerge} 还原网格：横向合并把同一文本复制到所跨列，
 * 纵向合并的延续单元格从上一行同列取值，保证输出是规整的二维网格而不是稀疏的 XML 顺序。</p>
 */
@Component
public class DocxTableExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxTableExtractor.class);

    /**
     * 抽取全部表格。
     *
     * @param file DOCX 文件
     * @param startIndex 起始序号
     * @return 表格列表
     */
    public List<com.crag.doc.domain.model.ParsedTable> extract(Path file, int startIndex) {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in)) {
            List<XWPFTable> tables = document.getTables();
            List<com.crag.doc.domain.model.ParsedTable> result = new ArrayList<>(tables.size());
            int index = startIndex;
            for (XWPFTable table : tables) {
                List<List<String>> rows = grid(table);
                if (rows.size() >= 2 && !rows.get(0).isEmpty()) {
                    result.add(new com.crag.doc.domain.model.ParsedTable(index++, 0, "", rows));
                }
            }
            return result;
        } catch (IOException | RuntimeException e) {
            log.warn("DOCX 表格抽取失败，按无表格处理 file={}", file.getFileName(), e);
            return List.of();
        }
    }

    private List<List<String>> grid(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        List<String> previous = List.of();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                int column = cells.size();
                String text = normalize(cell.getText());
                boolean verticalContinue = isVerticalContinue(cell);
                int span = gridSpan(cell);
                if (verticalContinue && text.isEmpty() && column < previous.size()) {
                    text = previous.get(column);
                }
                for (int i = 0; i < span; i++) {
                    cells.add(text);
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
                previous = cells;
            }
        }
        return rows;
    }

    private static int gridSpan(XWPFTableCell cell) {
        CTTcPr properties = cell.getCTTc().getTcPr();
        if (properties == null || !properties.isSetGridSpan()) {
            return 1;
        }
        BigInteger span = properties.getGridSpan().getVal();
        int value = span == null ? 1 : span.intValue();
        return Math.max(value, 1);
    }

    private static boolean isVerticalContinue(XWPFTableCell cell) {
        CTTcPr properties = cell.getCTTc().getTcPr();
        return properties != null && properties.isSetVMerge();
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').replace('\u00A0', ' ').strip();
    }
}
