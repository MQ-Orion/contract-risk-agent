package com.crag.common.text;

/**
 * 一个条款块（切分前的自然边界），用于切分与切分对比统计（规范 5.3）。
 *
 * @param blockIndex 块序号，从 0 开始
 * @param clauseNo   条款序号
 * @param charStart  在原文中的起始偏移（含）
 * @param charEnd    在原文中的结束偏移（不含）
 */
public record ClauseBlock(int blockIndex, String clauseNo, int charStart, int charEnd) {

    /**
     * 块长度。
     *
     * @return 字符数
     */
    public int length() {
        return charEnd - charStart;
    }
}
