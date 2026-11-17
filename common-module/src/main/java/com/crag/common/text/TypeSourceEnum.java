package com.crag.common.text;

/**
 * 条款类型标签的来源，用于人工修正留痕（规范 5.3：标签结果必须可在管理端人工修正并留痕）。
 */
public enum TypeSourceEnum {

    /** 标题关键词命中。 */
    KEYWORD,
    /** 模型兜底分类。 */
    MODEL,
    /** 人工修正。 */
    MANUAL,
    /** 未命中任何规则，暂置其他。 */
    DEFAULT
}
