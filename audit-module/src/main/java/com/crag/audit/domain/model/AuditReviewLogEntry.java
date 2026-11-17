package com.crag.audit.domain.model;

import java.time.Instant;

/**
 * 复核日志条目（表 audit_review_log，规范 9.4、11.6：只追加，不可修改）。
 *
 * @param id           日志标识
 * @param tenantId     租户
 * @param taskId       任务标识
 * @param riskItemId   风险项标识
 * @param reviewerId   复核人
 * @param reviewerName 复核人名称
 * @param action       动作
 * @param reasonCode   驳回原因码，仅 REJECT 有值
 * @param comment      备注
 * @param traceId      链路标识
 * @param createdAt    发生时间
 */
public record AuditReviewLogEntry(long id,
                                  long tenantId,
                                  long taskId,
                                  long riskItemId,
                                  long reviewerId,
                                  String reviewerName,
                                  ReviewActionEnum action,
                                  RejectReasonCodeEnum reasonCode,
                                  String comment,
                                  String traceId,
                                  Instant createdAt) {

    /**
     * 校验动作与原因码的搭配（规范 9.4：驳回必须选原因码）。
     *
     * @throws com.crag.common.domain.BizException 搭配非法
     */
    public AuditReviewLogEntry {
        if (action == null) {
            throw com.crag.common.domain.BizException.of(com.crag.common.api.ErrorCode.PARAM_INVALID,
                    "review action required");
        }
        if (action.requiresReasonCode() && reasonCode == null) {
            throw com.crag.common.domain.BizException.of(com.crag.common.api.ErrorCode.PARAM_INVALID,
                    "reject requires reason code");
        }
    }
}
