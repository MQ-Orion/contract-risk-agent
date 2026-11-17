package com.crag.audit.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.audit.infrastructure.persistence.entity.AuditRiskItemEntity;

/**
 * 风险明细 Mapper（表 audit_risk_item），租户条件由拦截器注入（规范 4.4）。
 */
public interface AuditRiskItemMapper extends BaseMapper<AuditRiskItemEntity> {
}
