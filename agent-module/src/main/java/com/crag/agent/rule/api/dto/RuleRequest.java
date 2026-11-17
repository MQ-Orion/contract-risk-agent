package com.crag.agent.rule.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 规则新增/修改请求（规范 8.1 字段）。
 *
 * <p>字段校验只做“必填与长度”；编码格式、表达式白名单、正反例回放由服务层校验，
 * 因为它们需要组合判断与求值，注解无法表达（规范 3.3 分层职责）。</p>
 *
 * @param ruleCode           规则编码，修改时可不传
 * @param ruleName           规则名称
 * @param clauseType         适用条款类型
 * @param scope              作用域：CLAUSE / CONTRACT
 * @param conditionExpr      条件表达式
 * @param riskLevel          风险等级
 * @param suggestionTemplate 建议模板
 * @param enabled            是否启用
 * @param positiveExample    正例 JSON
 * @param negativeExample    反例 JSON
 * @param ownerName          维护人
 */
public record RuleRequest(@Size(max = 64) String ruleCode,
                          @NotBlank @Size(max = 128) String ruleName,
                          @NotBlank @Size(max = 32) String clauseType,
                          @NotBlank @Size(max = 16) String scope,
                          @NotBlank @Size(max = 500) String conditionExpr,
                          @NotBlank @Size(max = 16) String riskLevel,
                          @NotBlank @Size(max = 500) String suggestionTemplate,
                          Boolean enabled,
                          @NotBlank String positiveExample,
                          @NotBlank String negativeExample,
                          @NotBlank @Size(max = 64) String ownerName) {
}
