package com.crag.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 风险明细表实体（表 audit_risk_item）。
 */
@TableName("audit_risk_item")
public class AuditRiskItemEntity {

    @TableId(type = IdType.INPUT)
    public Long id;
    public Long tenantId;
    public Long taskId;
    public String riskLevel;
    public String riskContent;
    public String suggestion;
    public String source;
    public String ruleCode;
    public Integer ruleVersion;
    public String sourceDocName;
    public String sourceChunkKey;
    public String sourceExcerpt;
    public Integer citationVerified;
    public String clauseNo;
    public String clauseType;
    public String clauseText;
    public String retrievalSnapshot;
    public Integer reviewed;
    public String reviewResult;
    public Long reviewedBy;
    public Instant reviewedAt;
    public Integer versionNo;
    public Instant createdAt;
    public Instant updatedAt;
    public Long createdBy;
    /** 是否被新一轮审核取代：0 当前，1 历史（保留供复核日志追溯）。 */
    public Integer superseded;
    public Integer deleted;
}
