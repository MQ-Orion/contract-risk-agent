package com.crag.audit.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.audit.infrastructure.persistence.entity.AuditTaskEntity;

/**
 * 审核任务 Mapper（表 audit_task），租户条件由拦截器注入（规范 4.4）。
 */
public interface AuditTaskMapper extends BaseMapper<AuditTaskEntity> {
}
