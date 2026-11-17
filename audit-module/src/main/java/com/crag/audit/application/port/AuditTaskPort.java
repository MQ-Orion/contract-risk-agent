package com.crag.audit.application.port;

/**
 * 供 agent-module 调用的审核任务端口（依赖方向：agent → audit，规范 2.2）。
 *
 * <p>初审编排完成后由 agent 侧调用：先创建/复用任务，再回写风险清单与报告位置。
 * 端口只暴露 DTO，不把 audit 的实体或 Mapper 泄露给上层。</p>
 */
public interface AuditTaskPort {

    /**
     * 创建或复用任务（幂等：同合同同版本未终结的任务直接返回）。
     *
     * @param tenantId        租户
     * @param contractId      合同标识
     * @param contractVersion 合同版本
     * @param operatorId      操作人
     * @return 任务标识
     */
    long createOrGet(long tenantId, long contractId, int contractVersion, long operatorId);

    /**
     * 置为审核中。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @param operatorId 操作人
     */
    void markAuditing(long tenantId, long taskId, long operatorId);

    /**
     * 回写风险清单与报告位置，任务进入待复核。
     *
     * @param tenantId      租户
     * @param taskId        任务标识
     * @param risks         风险清单
     * @param reportUrl     报告位置
     * @param modelVersion  模型版本
     * @param promptVersion Prompt 版本
     * @param operatorId    操作人
     */
    void submitResult(long tenantId, long taskId, java.util.List<AuditRiskInput> risks, String reportUrl,
                      String modelVersion, String promptVersion, long operatorId);

    /**
     * 标记失败。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @param reason   原因
     */
    void fail(long tenantId, long taskId, String reason);
}
