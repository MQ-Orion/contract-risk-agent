package com.crag.doc.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.domain.model.DocumentFormatEnum;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文档格式校验测试（规范 5.1、11.2：类型白名单配合魔数校验）。
 */
class DocumentFormatDetectorTest {

    private final DocumentFormatDetector detector = new DocumentFormatDetector();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("PDF 魔数识别")
    void detectsPdf() throws IOException {
        Path pdf = Files.writeString(tempDir.resolve("a.pdf"), "%PDF-1.7\n", StandardCharsets.US_ASCII);

        assertThat(detector.detect(pdf, "合同.pdf")).isEqualTo(DocumentFormatEnum.PDF);
    }

    @Test
    @DisplayName("OLE 复合文档识别为 DOC")
    void detectsDoc() throws IOException {
        byte[] bytes = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1,
                0x1A, (byte) 0xE1, 0x00, 0x00};
        Path doc = tempDir.resolve("b.doc");
        Files.write(doc, bytes);

        assertThat(detector.detect(doc, "b.doc")).isEqualTo(DocumentFormatEnum.DOC);
    }

    @Test
    @DisplayName("Office Open XML 包识别为 DOCX")
    void detectsDocx() throws IOException {
        Path docx = writeOoxml("c.docx", false);

        assertThat(detector.detect(docx, "c.docx")).isEqualTo(DocumentFormatEnum.DOCX);
    }

    @Test
    @DisplayName("扩展名不在白名单时拒绝，即使魔数合法")
    void rejectsUnknownExtension() throws IOException {
        Path file = Files.writeString(tempDir.resolve("d.txt"), "%PDF-1.7", StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> detector.detect(file, "d.txt"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("扩展名与魔数不一致时拒绝，防止改后缀绕过")
    void rejectsExtensionMagicMismatch() throws IOException {
        Path file = Files.writeString(tempDir.resolve("e.docx"), "%PDF-1.7", StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> detector.detect(file, "e.docx"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("含宏的文档一律拒绝")
    void rejectsMacroEnabledPackage() throws IOException {
        Path docx = writeOoxml("f.docx", true);

        assertThatThrownBy(() -> detector.detect(docx, "f.docx"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("未知魔数拒绝")
    void rejectsUnknownMagic() throws IOException {
        Path file = Files.write(tempDir.resolve("g.pdf"), "not-a-pdf".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> detector.detect(file, "g.pdf"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("扩展名解析大小写不敏感")
    void parsesExtensionCaseInsensitively() {
        assertThat(DocumentFormatDetector.extensionOf("合同.PDF")).isEqualTo("pdf");
        assertThat(DocumentFormatDetector.extensionOf("无扩展名")).isEmpty();
        assertThat(DocumentFormatDetector.extensionOf(null)).isEmpty();
    }

    private Path writeOoxml(String filename, boolean macroEnabled) throws IOException {
        Path path = tempDir.resolve(filename);
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-"
                + "officedocument.wordprocessingml.document.main+xml\"/>"
                + (macroEnabled ? "<Override PartName=\"/word/vbaProject.bin\" ContentType=\"application/vnd.ms-word."
                + "document.macroEnabled.main+xml\"/>" : "")
                + "</Types>";
        try (OutputStream out = Files.newOutputStream(path);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }
}
