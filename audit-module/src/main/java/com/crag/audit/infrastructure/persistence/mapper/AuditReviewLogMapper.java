package com.crag.audit.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.audit.infrastructure.persistence.entity.AuditReviewLogEntity;

/**
 * 复核日志 Mapper（表 audit_review_log）：只插入，不提供更新与删除（规范 9.4、11.6）。
 */
public interface AuditReviewLogMapper extends BaseMapper<AuditReviewLogEntity> {
}
