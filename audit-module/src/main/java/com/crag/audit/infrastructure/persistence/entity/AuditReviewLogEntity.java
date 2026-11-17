package com.crag.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 复核日志表实体（表 audit_review_log）：只追加，无 updated_at/deleted。
 */
@TableName("audit_review_log")
public class AuditReviewLogEntity {

    @TableId(type = IdType.INPUT)
    public Long id;
    public Long tenantId;
    public Long taskId;
    public Long riskItemId;
    public Long reviewerId;
    public String reviewerName;
    public String action;
    public String reasonCode;
    public String comment;
    public String traceId;
    public Instant createdAt;
}
