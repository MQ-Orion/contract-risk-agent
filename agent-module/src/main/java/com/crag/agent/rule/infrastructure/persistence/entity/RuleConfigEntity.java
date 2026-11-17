package com.crag.agent.rule.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 规则配置表实体（表 rule_config，规范 8.1）。
 *
 * <p>与领域对象分离：表结构变更不应传导到规则求值代码，转换集中在仓储适配器（规范 3.5）。</p>
 */
@TableName("rule_config")
public class RuleConfigEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private String ruleCode;
    private String ruleName;
    private String clauseType;
    private String scope;
    private String conditionExpr;
    private String riskLevel;
    private String suggestionTemplate;
    private Integer enabled;
    private Integer versionNo;
    private String positiveExample;
    private String negativeExample;
    private String ownerName;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getClauseType() {
        return clauseType;
    }

    public void setClauseType(String clauseType) {
        this.clauseType = clauseType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getConditionExpr() {
        return conditionExpr;
    }

    public void setConditionExpr(String conditionExpr) {
        this.conditionExpr = conditionExpr;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getSuggestionTemplate() {
        return suggestionTemplate;
    }

    public void setSuggestionTemplate(String suggestionTemplate) {
        this.suggestionTemplate = suggestionTemplate;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getPositiveExample() {
        return positiveExample;
    }

    public void setPositiveExample(String positiveExample) {
        this.positiveExample = positiveExample;
    }

    public String getNegativeExample() {
        return negativeExample;
    }

    public void setNegativeExample(String negativeExample) {
        this.negativeExample = negativeExample;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
