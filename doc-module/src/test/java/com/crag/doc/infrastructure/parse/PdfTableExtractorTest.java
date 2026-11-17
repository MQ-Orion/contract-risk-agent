package com.crag.doc.infrastructure.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.doc.domain.model.ParsedTable;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PDF 表格二次还原测试（规范 5.2）：以坐标对齐的表格为输入，验证行列重建。
 */
class PdfTableExtractorTest {

    private final PdfTableExtractor extractor = new PdfTableExtractor();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("按列坐标还原 3×3 表格")
    void reconstructsAlignedGrid() throws Exception {
        Path pdf = tempDir.resolve("table.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                writeRow(stream, 760, new String[]{"Item", "Qty", "Price"});
                writeRow(stream, 740, new String[]{"Server", "10", "20000"});
                writeRow(stream, 720, new String[]{"Switch", "4", "5000"});
            }
            document.save(pdf.toFile());
        }

        List<ParsedTable> tables = extractor.extract(pdf);

        assertThat(tables).isNotEmpty();
        ParsedTable table = tables.get(0);
        assertThat(table.rowCount()).isEqualTo(3);
        assertThat(table.columnCount()).isEqualTo(3);
        assertThat(table.rows().get(0)).containsExactly("Item", "Qty", "Price");
        assertThat(table.rows().get(1)).containsExactly("Server", "10", "20000");
    }

    @Test
    @DisplayName("页数读取正确")
    void readsPageCount() throws Exception {
        Path pdf = tempDir.resolve("pages.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(pdf.toFile());
        }

        assertThat(extractor.pageCount(pdf)).isEqualTo(2);
        assertThat(extractor.requirePageCount(pdf)).isEqualTo(2);
    }

    @Test
    @DisplayName("无表格文本不产生表格")
    void noTableForPlainParagraph() throws Exception {
        Path pdf = tempDir.resolve("plain.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(72, 760);
                stream.showText("This contract has no table at all.");
                stream.endText();
            }
            document.save(pdf.toFile());
        }

        assertThat(extractor.extract(pdf)).isEmpty();
    }

    private void writeRow(PDPageContentStream stream, float y, String[] cells) throws Exception {
        float[] x = {72F, 250F, 420F};
        for (int i = 0; i < cells.length; i++) {
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(x[i], y);
            stream.showText(cells[i]);
            stream.endText();
        }
    }
}
