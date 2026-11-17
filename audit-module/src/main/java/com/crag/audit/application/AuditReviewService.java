package com.crag.audit.application;

import com.crag.audit.application.port.AuditRepository;
import com.crag.audit.domain.model.AuditReviewLogEntry;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import com.crag.audit.domain.model.RejectReasonCodeEnum;
import com.crag.audit.domain.model.ReviewActionEnum;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.common.domain.TraceContext;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人工复核用例（规范 9.4、10.7）。
 *
 * <p>调用链：{@code AuditController.review} → 本类（事务）→ 仓储：乐观锁更新风险项 → 追加复核日志 →
 * 驳回时把任务退回审核中（唯一允许的回退）。</p>
 *
 * <p>顺序不可调换：先更新风险项（带版本条件），成功后才写日志；否则版本冲突时会留下一条
 * “实际未生效”的复核日志，审计记录与事实不符。</p>
 *
 * <p>失败语义：版本冲突抛 40001，调用方应重新拉取风险项后重试；不得静默覆盖他人结论（规范 10.7）。</p>
 */
@Service
public class AuditReviewService {

    private static final Logger log = LoggerFactory.getLogger(AuditReviewService.class);

    private final AuditRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock clock;

    /**
     * 构造服务。
     *
     * @param repository  审核仓储
     * @param idGenerator 雪花标识
     * @param clock       统一时钟
     */
    public AuditReviewService(AuditRepository repository, SnowflakeIdGenerator idGenerator, Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 复核单个风险项。
     *
     * @param tenantId        租户
     * @param taskId          任务标识
     * @param riskId          风险项标识
     * @param action          动作
     * @param reasonCode      驳回原因码（REJECT 必填）
     * @param comment         备注
     * @param reviewerId      复核人
     * @param reviewerName    复核人名称
     * @param expectedVersion 期望版本（乐观锁）
     * @return 复核结果
     */
    @Transactional
    public ReviewResult review(long tenantId, long taskId, long riskId, ReviewActionEnum action,
                               RejectReasonCodeEnum reasonCode, String comment, long reviewerId,
                               String reviewerName, int expectedVersion) {
        AuditTaskRecord task = repository.findTask(tenantId, taskId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "audit task " + taskId));
        if (!task.reviewable()) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL, "task status " + task.status() + " is not reviewable");
        }
        AuditRiskItemRecord item = repository.findRiskItem(tenantId, riskId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "risk item " + riskId));
        if (item.taskId() != taskId) {
            throw BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "risk item not in task");
        }
        if (action == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "action required");
        }
        if (action.requiresReasonCode() && reasonCode == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "reject requires reason code");
        }
        AuditRiskItemRecord updated = item.reviewed(action, reviewerId, clock.instant());
        int rows = repository.updateReview(updated, expectedVersion);
        if (rows == 0) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL,
                    "risk item version conflict, expected " + expectedVersion);
        }
        repository.appendReviewLog(new AuditReviewLogEntry(idGenerator.nextId(), tenantId, taskId, riskId,
                reviewerId, reviewerName, action, reasonCode, comment, TraceContext.current(), clock.instant()));
        AuditTaskRecord currentTask = task;
        if (action == ReviewActionEnum.REJECT) {
            // 规范 9.1：复核不通过返回审核中是唯一允许的回退
            currentTask = task.moveTo(AuditTaskStatusEnum.AUDITING, reviewerId, clock.instant());
            repository.updateTask(currentTask);
        }
        log.info("复核完成 tenantId={} taskId={} riskId={} action={} reason={}", tenantId, taskId, riskId, action,
                reasonCode);
        return new ReviewResult(updated, currentTask);
    }

    /**
     * 复核结果。
     *
     * @param item 风险项
     * @param task 任务
     */
    public record ReviewResult(AuditRiskItemRecord item, AuditTaskRecord task) {
    }
}
