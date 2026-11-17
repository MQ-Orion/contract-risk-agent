package com.crag.agent.rule.domain;

/**
 * 规则变更动作（表 rule_change_log.action）。
 *
 * <p>区分 ENABLE/DISABLE 与 UPDATE：启停不改变表达式，但会改变“哪些条款会被检查”，
 * 属于必须留痕的口径变更（规范 8.3 灰度与全量启用）。</p>
 */
public enum RuleChangeActionEnum {

    /** 新建。 */
    CREATE,
    /** 修改表达式/等级/建议等定义字段。 */
    UPDATE,
    /** 启用。 */
    ENABLE,
    /** 停用。 */
    DISABLE,
    /** 灰度试跑（单租户关闭态比对）。 */
    GRAY;

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名
     * @return 枚举；未知返回 null
     */
    public static RuleChangeActionEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RuleChangeActionEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
