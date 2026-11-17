package com.crag.doc.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 解析结果：文本、页数、表格与元数据（规范 5.2：输出必须包含文本及页段定位信息）。
 *
 * @param text           纯文本，段落间以换行分隔
 * @param pageCount      页数；未知时为 0
 * @param pageCountKnown 页数是否可信（DOCX 由文档属性提供，可能缺失）
 * @param format         文档格式
 * @param tables         二次还原后的表格
 * @param metadata       解析元数据，仅保留非敏感字段
 */
public record ParsedDocument(String text,
                             int pageCount,
                             boolean pageCountKnown,
                             DocumentFormatEnum format,
                             List<ParsedTable> tables,
                             Map<String, String> metadata) {

    /**
     * 构造解析结果，表格与元数据不可变。
     *
     * @param text           纯文本
     * @param pageCount      页数
     * @param pageCountKnown 页数是否可信
     * @param format         文档格式
     * @param tables         表格
     * @param metadata       元数据
     */
    public ParsedDocument {
        tables = tables == null ? List.of() : List.copyOf(tables);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
