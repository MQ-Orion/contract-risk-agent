package com.crag.audit.application.port;

import com.crag.audit.domain.model.AuditReviewLogEntry;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import com.crag.common.domain.RiskLevelEnum;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 审核仓储端口（表 audit_task / audit_risk_item / audit_review_log，归属 audit-module）。
 *
 * <p>写操作都在服务层事务内调用；复核日志只追加，端口不提供更新与删除（规范 9.4、11.6）。</p>
 */
public interface AuditRepository {

    /**
     * 插入任务。
     *
     * @param task 任务
     * @return 插入后的任务
     */
    AuditTaskRecord insertTask(AuditTaskRecord task);

    /**
     * 按标识查询任务。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @return 任务
     */
    Optional<AuditTaskRecord> findTask(long tenantId, long taskId);

    /**
     * 查询某合同某版本未终结的任务（幂等与并发控制用）。
     *
     * @param tenantId        租户
     * @param contractId      合同标识
     * @param contractVersion 合同版本
     * @return 任务
     */
    Optional<AuditTaskRecord> findActiveByContract(long tenantId, long contractId, int contractVersion);

    /**
     * 查询合同最近一次审核任务（任意状态），用于合同列表展示审核状态。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 最近任务
     */
    Optional<AuditTaskRecord> findLatestByContract(long tenantId, long contractId);

    /**
     * 更新任务状态与结果字段。
     *
     * @param task 任务
     * @return 更新后的任务
     */
    AuditTaskRecord updateTask(AuditTaskRecord task);

    /**
     * 批量插入风险项。
     *
     * @param items 风险项
     * @return 插入条数
     */
    int insertRiskItems(List<AuditRiskItemRecord> items);

    /**
     * 把任务当前风险项标记为历史（驳回重审时调用）：只改标记，不删除，保证复核日志外键与追溯。
     *
     * @param tenantId 租户
     * @param taskId   任务
     * @return 影响行数
     */
    int supersedeByTask(long tenantId, long taskId);

    /**
     * 查询任务的风险项。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @return 风险项
     */
    List<AuditRiskItemRecord> findRiskItems(long tenantId, long taskId);

    /**
     * 按标识查询风险项。
     *
     * @param tenantId 租户
     * @param riskId   风险项标识
     * @return 风险项
     */
    Optional<AuditRiskItemRecord> findRiskItem(long tenantId, long riskId);

    /**
     * 乐观锁更新复核结果：仅当 {@code version_no = expectedVersion} 时更新。
     *
     * @param item            风险项（含新版本号）
     * @param expectedVersion 期望版本
     * @return 更新行数；0 表示版本冲突
     */
    int updateReview(AuditRiskItemRecord item, int expectedVersion);

    /**
     * 追加复核日志（只插入）。
     *
     * @param entry 日志
     */
    void appendReviewLog(AuditReviewLogEntry entry);

    /**
     * 查询任务的复核日志。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @return 日志列表
     */
    List<AuditReviewLogEntry> findReviewLogs(long tenantId, long taskId);

    /**
     * 查询指定状态之后仍未复核的高/中风险项数量（完成任务前的门槛校验）。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @return 未复核数量
     */
    int countUnreviewedRisks(long tenantId, long taskId);

    /**
     * 统计某原因码的驳回样本，供 hard negatives 导出（规范 9.4）。
     *
     * @param tenantId  租户
     * @param since     起始时间
     * @return 驳回明细（风险项 + 日志）
     */
    List<RejectedSample> findRejectedSamples(long tenantId, Instant since);

    /**
     * 驳回样本视图。
     *
     * @param risk     风险项
     * @param reason   原因码名称
     * @param reviewedAt 复核时间
     */
    record RejectedSample(AuditRiskItemRecord risk, String reason, Instant reviewedAt) {
    }

    /**
     * 按状态统计任务数。
     *
     * @param tenantId 租户
     * @param status   状态
     * @return 数量
     */
    long countTasks(long tenantId, AuditTaskStatusEnum status);
}
