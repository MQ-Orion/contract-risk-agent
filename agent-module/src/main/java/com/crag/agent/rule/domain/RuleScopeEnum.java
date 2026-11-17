package com.crag.agent.rule.domain;

/**
 * 规则作用域（规范 8.1）。
 *
 * <p>{@link #CLAUSE} 对单条款求值；{@link #CONTRACT} 对整份合同求值，用于“必备条款缺失”这类
 * 无法在单条款内表达的判断（负责人 2026-11-14 批准，见 ADR 0005）。</p>
 */
public enum RuleScopeEnum {

    /** 单条款作用域。 */
    CLAUSE,
    /** 合同作用域。 */
    CONTRACT;

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名，忽略大小写
     * @return 枚举；未知值返回 null
     */
    public static RuleScopeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RuleScopeEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
