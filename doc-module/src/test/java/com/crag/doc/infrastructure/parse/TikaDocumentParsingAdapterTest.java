package com.crag.doc.infrastructure.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.doc.domain.model.ParsedDocument;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 解析适配器集成测试：用真实 DOCX 验证文本与表格输出（规范 13.1 集成测试）。
 */
class TikaDocumentParsingAdapterTest {

    private final TikaDocumentParsingAdapter adapter =
            new TikaDocumentParsingAdapter(new DocxTableExtractor(), new PdfTableExtractor());

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("解析 DOCX：文本完整、表格被二次还原")
    void parsesDocxWithTable() throws Exception {
        Path file = tempDir.resolve("contract.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("设备采购合同");
            document.createParagraph().createRun().setText("第一条 合同标的");
            document.createParagraph().createRun().setText("甲方向乙方采购服务器十台，总价款二十万元。");
            document.createParagraph().createRun().setText("第二条 违约责任");
            document.createParagraph().createRun().setText("逾期交付的，每逾期一日按合同总额千分之三支付违约金。");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("设备名称");
            table.getRow(0).getCell(1).setText("数量");
            table.getRow(1).getCell(0).setText("服务器");
            table.getRow(1).getCell(1).setText("10");
            document.write(out);
        }

        ParsedDocument parsed = adapter.parse(file, DocumentFormatEnum.DOCX);

        assertThat(parsed.text()).contains("设备采购合同").contains("第二条 违约责任").contains("违约金");
        assertThat(parsed.tables()).hasSize(1);
        assertThat(parsed.tables().get(0).rows().get(0)).containsExactly("设备名称", "数量");
        assertThat(parsed.format()).isEqualTo(DocumentFormatEnum.DOCX);
    }

    @Test
    @DisplayName("解析空文档不抛出异常，由质量校验判定失败")
    void parsesEmptyDocumentWithoutThrowing() throws Exception {
        Path file = tempDir.resolve("empty.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(file)) {
            document.write(out);
        }

        ParsedDocument parsed = adapter.parse(file, DocumentFormatEnum.DOCX);

        assertThat(parsed.text()).isBlank();
    }
}
