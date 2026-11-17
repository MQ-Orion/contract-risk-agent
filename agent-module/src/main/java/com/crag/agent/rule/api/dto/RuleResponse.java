package com.crag.agent.rule.api.dto;

import com.crag.agent.rule.domain.RuleDefinition;
import java.time.Instant;

/**
 * 规则响应（对外结构）。
 *
 * @param id                 规则标识
 * @param ruleCode           规则编码
 * @param ruleName           规则名称
 * @param clauseType         适用条款类型
 * @param scope              作用域
 * @param conditionExpr      条件表达式
 * @param riskLevel          风险等级 code
 * @param riskLevelLabel     风险等级中文
 * @param suggestionTemplate 建议模板
 * @param enabled            是否启用
 * @param versionNo          版本号
 * @param positiveExample    正例 JSON
 * @param negativeExample    反例 JSON
 * @param ownerName          维护人
 * @param updatedAt          更新时间
 */
public record RuleResponse(String id,
                           String ruleCode,
                           String ruleName,
                           String clauseType,
                           String scope,
                           String conditionExpr,
                           String riskLevel,
                           String riskLevelLabel,
                           String suggestionTemplate,
                           boolean enabled,
                           int versionNo,
                           String positiveExample,
                           String negativeExample,
                           String ownerName,
                           Instant updatedAt) {

    /**
     * 由领域对象转换。
     *
     * @param rule 规则
     * @return 响应
     */
    public static RuleResponse from(RuleDefinition rule) {
        return new RuleResponse(String.valueOf(rule.id()), rule.ruleCode(), rule.ruleName(), rule.clauseType(),
                rule.scope().name(), rule.conditionExpr(), rule.riskLevel().name(), rule.riskLevel().label(),
                rule.suggestionTemplate(), rule.enabled(), rule.versionNo(), rule.positiveExample(),
                rule.negativeExample(), rule.ownerName(), rule.updatedAt());
    }
}
