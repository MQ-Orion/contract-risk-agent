package com.crag.agent.rule.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 规则变更日志实体（表 rule_change_log，规范 8.3）。
 *
 * <p>只追加，不做更新与删除，因此没有 {@code updatedAt} 与 {@code deleted} 字段。</p>
 */
@TableName("rule_change_log")
public class RuleChangeLogEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long ruleId;
    private String ruleCode;
    private String action;
    private Integer versionBefore;
    private Integer versionAfter;
    private String beforeJson;
    private String afterJson;
    private Long operatorId;
    private String operatorName;
    private Instant createdAt;

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

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getVersionBefore() {
        return versionBefore;
    }

    public void setVersionBefore(Integer versionBefore) {
        this.versionBefore = versionBefore;
    }

    public Integer getVersionAfter() {
        return versionAfter;
    }

    public void setVersionAfter(Integer versionAfter) {
        this.versionAfter = versionAfter;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
