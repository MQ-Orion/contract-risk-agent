package com.crag.doc.domain.model;

import java.util.List;

/**
 * 二次还原后的表格：行列结构由 POI 或 PDF 文本坐标重建，不采用 Tika 的表格输出（规范 5.2）。
 *
 * @param index   表格序号，从 0 开始
 * @param pageNo  所在页，PDF 从 1 开始；DOCX 为 0 表示页信息不可得
 * @param caption 表题，可为空串
 * @param rows    行数据，已按行列对齐；合并单元格以重复值填充
 */
public record ParsedTable(int index, int pageNo, String caption, List<List<String>> rows) {

    /**
     * 构造表格，行数据不可变。
     *
     * @param index   表格序号
     * @param pageNo  页码
     * @param caption 表题
     * @param rows    行数据
     */
    public ParsedTable {
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    }

    /**
     * 行数。
     *
     * @return 行数
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * 列数，取首行列数。
     *
     * @return 列数
     */
    public int columnCount() {
        return rows.isEmpty() ? 0 : rows.get(0).size();
    }
}
