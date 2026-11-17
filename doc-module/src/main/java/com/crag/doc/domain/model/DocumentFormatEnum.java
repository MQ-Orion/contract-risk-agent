package com.crag.doc.domain.model;

/**
 * 允许的文档格式（规范 5.1：类型白名单为 PDF、DOC、DOCX，且必须以魔数判定）。
 */
public enum DocumentFormatEnum {

    /** PDF。 */
    PDF("pdf"),
    /** Word 97-2003 复合文档。 */
    DOC("doc"),
    /** Office Open XML 文档。 */
    DOCX("docx");

    private final String extension;

    DocumentFormatEnum(String extension) {
        this.extension = extension;
    }

    /**
     * 规范扩展名（小写、不含点）。
     *
     * @return 扩展名
     */
    public String extension() {
        return extension;
    }

    /**
     * 按扩展名匹配白名单。
     *
     * @param extension 小写扩展名
     * @return 枚举；不在白名单返回 null
     */
    public static DocumentFormatEnum fromExtension(String extension) {
        for (DocumentFormatEnum value : values()) {
            if (value.extension.equals(extension)) {
                return value;
            }
        }
        return null;
    }
}
