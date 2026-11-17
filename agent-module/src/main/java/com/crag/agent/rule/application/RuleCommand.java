package com.crag.agent.rule.application;

/**
 * 规则新增/修改命令（对应接口请求体）。
 *
 * <p>用命令对象而不是直接接收实体：接口字段与表结构解耦，且 {@code ruleCode} 在修改时不可变
 * （编码是审计与回放的锚点，改编码等于换了一条规则，规范 8.3）。</p>
 *
 * @param ruleCode           规则编码（三段式）
 * @param ruleName           规则名称
 * @param clauseType         适用条款类型；合同级规则传 CONTRACT
 * @param scope              作用域：CLAUSE / CONTRACT
 * @param conditionExpr      条件表达式
 * @param riskLevel          风险等级：HIGH / MEDIUM / LOW
 * @param suggestionTemplate 建议模板
 * @param enabled            是否启用；新建默认建议 false，先灰度试跑
 * @param positiveExample    正例 JSON
 * @param negativeExample    反例 JSON
 * @param ownerName          维护人
 */
public record RuleCommand(String ruleCode,
                          String ruleName,
                          String clauseType,
                          String scope,
                          String conditionExpr,
                          String riskLevel,
                          String suggestionTemplate,
                          Boolean enabled,
                          String positiveExample,
                          String negativeExample,
                          String ownerName) {
}
