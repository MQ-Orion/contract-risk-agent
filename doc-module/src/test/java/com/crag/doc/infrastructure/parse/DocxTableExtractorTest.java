package com.crag.doc.infrastructure.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.doc.domain.model.ParsedTable;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DOCX 表格二次还原测试（规范 5.2）。
 */
class DocxTableExtractorTest {

    private final DocxTableExtractor extractor = new DocxTableExtractor();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("普通表格按行列还原")
    void reconstructsPlainGrid() throws Exception {
        Path file = tempDir.resolve("plain.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(file)) {
            XWPFTable table = document.createTable(2, 3);
            table.getRow(0).getCell(0).setText("设备名称");
            table.getRow(0).getCell(1).setText("数量");
            table.getRow(0).getCell(2).setText("单价");
            table.getRow(1).getCell(0).setText("服务器");
            table.getRow(1).getCell(1).setText("10");
            table.getRow(1).getCell(2).setText("20000");
            document.write(out);
        }

        List<ParsedTable> tables = extractor.extract(file, 0);

        assertThat(tables).hasSize(1);
        ParsedTable table = tables.get(0);
        assertThat(table.rowCount()).isEqualTo(2);
        assertThat(table.columnCount()).isEqualTo(3);
        assertThat(table.rows().get(0)).containsExactly("设备名称", "数量", "单价");
        assertThat(table.rows().get(1)).containsExactly("服务器", "10", "20000");
    }

    @Test
    @DisplayName("横向合并单元格按 gridSpan 复制填充，保持列对齐")
    void expandsHorizontalMerge() throws Exception {
        Path file = tempDir.resolve("merged.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(file)) {
            XWPFTable table = document.createTable(2, 3);
            XWPFTableCell merged = table.getRow(0).getCell(0);
            merged.setText("合并表头");
            merged.getCTTc().addNewTcPr().addNewGridSpan().setVal(BigInteger.valueOf(2));
            table.getRow(0).getCell(2).setText("金额");
            // 横向合并意味着该行少一个 w:tc，POS 的网格才与 Word 呈现一致
            table.getRow(0).removeCell(1);
            table.getRow(1).getCell(0).setText("A");
            table.getRow(1).getCell(1).setText("B");
            table.getRow(1).getCell(2).setText("C");
            document.write(out);
        }

        List<ParsedTable> tables = extractor.extract(file, 0);

        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).columnCount()).isEqualTo(3);
        assertThat(tables.get(0).rows().get(0)).containsExactly("合并表头", "合并表头", "金额");
        assertThat(tables.get(0).rows().get(1)).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("无表格文件返回空列表")
    void returnsEmptyWhenNoTable() throws Exception {
        Path file = tempDir.resolve("none.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("仅文本，无表格");
            document.write(out);
        }

        assertThat(extractor.extract(file, 0)).isEmpty();
    }

    @Test
    @DisplayName("损坏文件不抛异常，按无表格处理")
    void toleratesBrokenFile() throws Exception {
        Path file = tempDir.resolve("broken.docx");
        Files.writeString(file, "not a zip");

        assertThat(extractor.extract(file, 0)).isEmpty();
    }
}
