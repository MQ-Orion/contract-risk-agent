package com.crag.audit.domain.model;

/**
 * 驳回原因码（规范 9.4，负责人 2026-11-14 确认固定四类）。
 *
 * <p>四个值分别对应检索、引用、规则、语义四层问题；这是 hard negatives 归类的唯一入口。
 * 枚举固定，不允许新增临时码：码表一旦漂移，回流样本就无法按层统计。</p>
 */
public enum RejectReasonCodeEnum {

    /** 检索无关：召回片段与条款不相关。 */
    IRRELEVANT_RETRIEVAL("检索无关"),
    /** 引用错误：引用片段与来源切片不符。 */
    WRONG_CITATION("引用错误"),
    /** 规则误报：规则表达式命中但业务上不构成风险。 */
    RULE_FALSE_POSITIVE("规则误报"),
    /** 语义误判：模型对条款含义判断错误。 */
    SEMANTIC_MISJUDGE("语义误判");

    private final String label;

    RejectReasonCodeEnum(String label) {
        this.label = label;
    }

    /**
     * 中文展示名。
     *
     * @return 展示名
     */
    public String label() {
        return label;
    }

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名
     * @return 枚举；未知返回 null
     */
    public static RejectReasonCodeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RejectReasonCodeEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
