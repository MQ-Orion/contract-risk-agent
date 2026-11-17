package com.crag.agent.rule.domain;

import com.crag.common.domain.RiskLevelEnum;

/**
 * 规则命中结果（进入风险清单的最小单元）。
 *
 * <p>规则结论必须进入最终清单，且不允许被模型覆盖（规范 7.2）；携带 {@code ruleCode} 与
 * {@code ruleVersion} 是为了回答“这条高风险依据哪条规定、哪个版本”，满足可追溯要求（规范 1.3、8.1）。</p>
 *
 * @param ruleCode    规则编码
 * @param ruleName    规则名称
 * @param riskLevel   风险等级
 * @param suggestion  建议
 * @param ruleVersion 规则版本
 * @param scope       作用域
 */
public record RuleMatch(String ruleCode,
                        String ruleName,
                        RiskLevelEnum riskLevel,
                        String suggestion,
                        int ruleVersion,
                        RuleScopeEnum scope) {

    /**
     * 由规则定义与求值上下文构造命中结果。
     *
     * @param rule 规则
     * @return 命中结果
     */
    public static RuleMatch of(RuleDefinition rule) {
        return new RuleMatch(rule.ruleCode(), rule.ruleName(), rule.riskLevel(), rule.suggestionTemplate(),
                rule.versionNo(), rule.scope());
    }
}
