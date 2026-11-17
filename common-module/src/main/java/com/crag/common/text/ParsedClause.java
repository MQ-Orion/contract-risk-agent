package com.crag.common.text;

/**
 * 一个切分单元的领域模型（规范 5.3：每个 chunk 必须保留条款序号与全文递增索引）。
 *
 * @param clauseNo      条款序号，如“第三条”“1.1”“（一）”；前言为“前言”
 * @param clauseType    条款类型
 * @param typeSource    类型来源
 * @param text          切分后的文本
 * @param fullTextIndex 全文递增索引，从 0 开始
 * @param charStart     在原文中的起始偏移（含）
 * @param charEnd       在原文中的结束偏移（不含）
 * @param blockIndex    所属条款块序号，用于切分对比统计
 * @param partIndex     块内分片序号，0 表示未二次切分
 * @param partCount     块内分片总数
 */
public record ParsedClause(String clauseNo,
                           ClauseTypeEnum clauseType,
                           TypeSourceEnum typeSource,
                           String text,
                           int fullTextIndex,
                           int charStart,
                           int charEnd,
                           int blockIndex,
                           int partIndex,
                           int partCount) {

    /**
     * 是否由超长条款二次切分产生。
     *
     * @return 分片数大于 1 时返回 true
     */
    public boolean isSplitPart() {
        return partCount > 1;
    }

    /**
     * 字符数。
     *
     * @return 文本长度
     */
    public int length() {
        return text.length();
    }
}
