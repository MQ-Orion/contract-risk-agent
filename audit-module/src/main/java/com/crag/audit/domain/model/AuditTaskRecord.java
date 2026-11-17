package com.crag.audit.domain.model;

import java.time.Instant;

/**
 * 审核任务记录（表 audit_task，规范 4.2、9.1）。
 *
 * @param id              任务标识
 * @param tenantId        租户
 * @param contractId      合同标识
 * @param contractVersion 合同版本号
 * @param status          状态
 * @param auditorId       审核人（状态变更时写入）
 * @param conclusion      人工结论摘要
 * @param modelVersion    模型版本
 * @param promptVersion   Prompt 版本
 * @param reportUrl       最近一次导出文件位置
 * @param startedAt       开始时间
 * @param finishedAt      结束时间
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record AuditTaskRecord(long id,
                              long tenantId,
                              long contractId,
                              int contractVersion,
                              AuditTaskStatusEnum status,
                              Long auditorId,
                              String conclusion,
                              String modelVersion,
                              String promptVersion,
                              String reportUrl,
                              Instant startedAt,
                              Instant finishedAt,
                              Instant createdAt,
                              Instant updatedAt) {

    /**
     * 新建任务（已创建状态）。
     *
     * @param id              任务标识
     * @param tenantId        租户
     * @param contractId      合同标识
     * @param contractVersion 合同版本
     * @param createdAt       创建时间
     * @return 任务
     */
    public static AuditTaskRecord created(long id, long tenantId, long contractId, int contractVersion,
                                          Instant createdAt) {
        return new AuditTaskRecord(id, tenantId, contractId, contractVersion, AuditTaskStatusEnum.CREATED, null,
                null, null, null, null, null, null, createdAt, createdAt);
    }

    /**
     * 状态流转：非法边抛 40001，并记录操作人与时间（规范 9.1）。
     *
     * @param target     目标状态
     * @param operatorId 操作人
     * @param now        当前时间
     * @return 新记录
     */
    public AuditTaskRecord moveTo(AuditTaskStatusEnum target, long operatorId, Instant now) {
        status.checkTransition(target);
        return new AuditTaskRecord(id, tenantId, contractId, contractVersion, target, operatorId, conclusion,
                modelVersion, promptVersion, reportUrl, startedAt, finishedAt, createdAt, now);
    }

    /**
     * 写入审核结果并进入待复核。
     *
     * @param reportUrl     报告位置
     * @param modelVersion  模型版本
     * @param promptVersion Prompt 版本
     * @param operatorId    操作人
     * @param now           当前时间
     * @return 新记录
     */
    public AuditTaskRecord withResult(String reportUrl, String modelVersion, String promptVersion, long operatorId,
                                      Instant now) {
        status.checkTransition(AuditTaskStatusEnum.PENDING_REVIEW);
        return new AuditTaskRecord(id, tenantId, contractId, contractVersion, AuditTaskStatusEnum.PENDING_REVIEW,
                operatorId, conclusion, modelVersion, promptVersion, reportUrl,
                startedAt == null ? now : startedAt, finishedAt, createdAt, now);
    }

    /**
     * 完成：写入人工结论与结束时间。
     *
     * @param conclusion 结论摘要
     * @param operatorId 操作人
     * @param now        当前时间
     * @return 新记录
     */
    public AuditTaskRecord complete(String conclusion, long operatorId, Instant now) {
        status.checkTransition(AuditTaskStatusEnum.COMPLETED);
        return new AuditTaskRecord(id, tenantId, contractId, contractVersion, AuditTaskStatusEnum.COMPLETED,
                operatorId, conclusion, modelVersion, promptVersion, reportUrl, startedAt, now, createdAt, now);
    }

    /**
     * 是否允许复核。
     *
     * @return 待复核返回 true
     */
    public boolean reviewable() {
        return status.reviewable();
    }
}
