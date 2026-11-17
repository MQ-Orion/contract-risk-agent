package com.crag.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 审核任务表实体（表 audit_task）。
 *
 * <p>仅作持久化载体：字段公开以便 MyBatis 直接映射，转换集中在仓储适配器（规范 3.5）。</p>
 */
@TableName("audit_task")
public class AuditTaskEntity {

    @TableId(type = IdType.INPUT)
    public Long id;
    public Long tenantId;
    public Long contractId;
    public Integer contractVersion;
    public String status;
    public Long auditorId;
    public String conclusion;
    public String modelVersion;
    public String promptVersion;
    public String reportUrl;
    public Instant startedAt;
    public Instant finishedAt;
    public Instant createdAt;
    public Instant updatedAt;
    public Long createdBy;
    public Integer deleted;
}
