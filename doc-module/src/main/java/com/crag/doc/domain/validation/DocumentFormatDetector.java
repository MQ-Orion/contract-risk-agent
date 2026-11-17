package com.crag.doc.domain.validation;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.domain.model.DocumentFormatEnum;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 文档格式校验：类型白名单配合魔数校验，禁止仅凭扩展名判断（规范 5.1、11.2）。
 *
 * <p>校验顺序：扩展名白名单 → 魔数 → 扩展名与魔数一致 → Office Open XML 包校验。
 * 含宏的文档（{@code macroEnabled}）一律拒绝，防止后续环节误执行（规范 11.2）。</p>
 */
public final class DocumentFormatDetector {

    /** 读取用于判定的头部字节数。 */
    public static final int SNIFF_LENGTH = 8;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OLE_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final String CONTENT_TYPES_ENTRY = "[Content_Types].xml";
    private static final String WORD_DOCUMENT_ENTRY = "word/document.xml";
    private static final String MACRO_MARKER = "macroEnabled";

    /**
     * 校验并识别文档格式。
     *
     * @param path             已落盘文件
     * @param originalFilename 原始文件名，仅用于取扩展名
     * @return 识别出的格式
     * @throws BizException 类型不支持、扩展名与内容不一致、含宏
     */
    public DocumentFormatEnum detect(Path path, String originalFilename) {
        String extension = extensionOf(originalFilename);
        DocumentFormatEnum byExtension = DocumentFormatEnum.fromExtension(extension);
        if (byExtension == null) {
            throw BizException.of(ErrorCode.FILE_TYPE_UNSUPPORTED, "extension=" + extension);
        }
        byte[] header = readHeader(path);
        DocumentFormatEnum byMagic;
        if (startsWith(header, PDF_MAGIC)) {
            byMagic = DocumentFormatEnum.PDF;
        } else if (startsWith(header, OLE_MAGIC)) {
            byMagic = DocumentFormatEnum.DOC;
        } else if (startsWith(header, ZIP_MAGIC)) {
            byMagic = DocumentFormatEnum.DOCX;
        } else {
            throw BizException.of(ErrorCode.FILE_TYPE_UNSUPPORTED, "unknown magic");
        }
        if (byMagic != byExtension) {
            throw BizException.of(ErrorCode.FILE_TYPE_UNSUPPORTED,
                    "extension=" + extension + " magic=" + byMagic);
        }
        if (byMagic == DocumentFormatEnum.DOCX) {
            verifyOoxmlPackage(path);
        }
        return byMagic;
    }

    private void verifyOoxmlPackage(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry contentTypes = zip.getEntry(CONTENT_TYPES_ENTRY);
            if (contentTypes == null || zip.getEntry(WORD_DOCUMENT_ENTRY) == null) {
                throw BizException.of(ErrorCode.FILE_TYPE_UNSUPPORTED, "not a wordprocessing ooxml package");
            }
            try (InputStream in = zip.getInputStream(contentTypes)) {
                String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (xml.contains(MACRO_MARKER)) {
                    throw BizException.of(ErrorCode.FILE_TYPE_UNSUPPORTED, "macro enabled document rejected");
                }
            }
        } catch (IOException e) {
            throw BizException.of(ErrorCode.FILE_TYPE_UNSUPPORTED, "zip read failed: " + e.getMessage());
        }
    }

    private static byte[] readHeader(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[SNIFF_LENGTH];
            int read = in.readNBytes(buffer, 0, SNIFF_LENGTH);
            return read == SNIFF_LENGTH ? buffer : Arrays.copyOf(buffer, Math.max(read, 0));
        } catch (IOException e) {
            throw BizException.of(ErrorCode.FILE_TYPE_UNSUPPORTED, "read failed: " + e.getMessage());
        }
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取小写扩展名。
     *
     * @param filename 文件名，可为空
     * @return 扩展名；无扩展名返回空串
     */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
