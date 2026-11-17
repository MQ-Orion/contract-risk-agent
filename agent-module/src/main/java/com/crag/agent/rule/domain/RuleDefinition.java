package com.crag.agent.rule.domain;

import com.crag.common.domain.RiskLevelEnum;

import java.time.Instant;

/**
 * 规则定义（表 rule_config，规范 8.1）。
 *
 * <p>不可变记录，缓存与求值只读；变更通过生成新记录完成，避免并发刷新时读到半更新状态。</p>
 *
 * @param id                 规则标识
 * @param tenantId           租户（规范 8.3 灰度需要租户维度）
 * @param ruleCode           三段式编码：条款类型.业务点.序号
 * @param ruleName           规则名称
 * @param clauseType         适用条款类型；合同级规则为 CONTRACT
 * @param scope              作用域
 * @param conditionExpr      条件表达式（Aviator）
 * @param riskLevel          命中后的风险等级
 * @param suggestionTemplate 建议模板
 * @param enabled            启用标记；0 为灰度试跑态
 * @param versionNo          版本号，每次变更 +1
 * @param positiveExample    正例 JSON（含完整求值上下文）
 * @param negativeExample    反例 JSON
 * @param ownerName          维护人
 * @param updatedAt          更新时间
 */
public record RuleDefinition(long id,
                             long tenantId,
                             String ruleCode,
                             String ruleName,
                             String clauseType,
                             RuleScopeEnum scope,
                             String conditionExpr,
                             RiskLevelEnum riskLevel,
                             String suggestionTemplate,
                             boolean enabled,
                             int versionNo,
                             String positiveExample,
                             String negativeExample,
                             String ownerName,
                             Instant updatedAt) {

    /**
     * 是否适用于某条款类型。
     *
     * @param type 条款类型 code
     * @return 适用返回 true
     */
    public boolean appliesToClauseType(String type) {
        return clauseType != null && clauseType.equalsIgnoreCase(type);
    }

    /**
     * 是否为合同级规则。
     *
     * @return 合同级返回 true
     */
    public boolean isContractScope() {
        return scope == RuleScopeEnum.CONTRACT;
    }

    /**
     * 生成启停后的新记录：只改运行状态，版本号不变。
     *
     * <p>版本号表示“规则内容版本”，只在 update 修改条件/等级/建议时递增（规范 8.3）；
     * 启停是可逆的运行开关，若也递增版本，会让变更日志里的版本号失去“内容变更”含义。</p>
     *
     * @param target 目标启用状态
     * @param now    当前时间
     * @return 新记录
     */
    public RuleDefinition withEnabled(boolean target, Instant now) {
        return new RuleDefinition(id, tenantId, ruleCode, ruleName, clauseType, scope, conditionExpr, riskLevel,
                suggestionTemplate, target, versionNo, positiveExample, negativeExample, ownerName, now);
    }
}
