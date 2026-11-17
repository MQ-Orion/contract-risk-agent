package com.crag.audit.application;

import com.crag.audit.application.port.AuditRepository;
import com.crag.audit.application.port.AuditRiskInput;
import com.crag.audit.application.port.AuditTaskPort;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核任务用例（规范 9.1、10.7）。
 *
 * <p>调用链：{@code AuditController} / agent 的初审编排 → 本类（事务）→ {@link AuditRepository}。
 * 状态流转全部经 {@link AuditTaskStatusEnum} 白名单校验，非法边抛 40001 且不写库。</p>
 *
 * <p>幂等：先按「租户+合同+版本」查未终结任务，命中直接复用；并发插入由唯一索引兜底，
 * 捕获 {@link DuplicateKeyException} 后回查并返回已有任务（规范 9.1、10.7）。</p>
 */
@Service
public class AuditTaskService implements AuditTaskPort {

    private static final Logger log = LoggerFactory.getLogger(AuditTaskService.class);

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
    public AuditTaskService(AuditRepository repository, SnowflakeIdGenerator idGenerator, Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public long createOrGet(long tenantId, long contractId, int contractVersion, long operatorId) {
        var existing = repository.findActiveByContract(tenantId, contractId, contractVersion);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        Instant now = clock.instant();
        AuditTaskRecord task = AuditTaskRecord.created(idGenerator.nextId(), tenantId, contractId,
                contractVersion, now);
        try {
            repository.insertTask(task);
            log.info("审核任务创建 tenantId={} contractId={} version={} taskId={}", tenantId, contractId,
                    contractVersion, task.id());
            return task.id();
        } catch (DuplicateKeyException e) {
            // 并发创建：唯一索引兜底后回查，保证重复提交返回同一任务（规范 10.7）
            return repository.findActiveByContract(tenantId, contractId, contractVersion)
                    .map(AuditTaskRecord::id)
                    .orElseThrow(() -> BizException.of(ErrorCode.SYSTEM_ERROR, "task race unresolved"));
        }
    }

    @Override
    @Transactional
    public void markAuditing(long tenantId, long taskId, long operatorId) {
        AuditTaskRecord task = requireTask(tenantId, taskId);
        repository.updateTask(task.moveTo(AuditTaskStatusEnum.AUDITING, operatorId, clock.instant()));
    }

    @Override
    @Transactional
    public void submitResult(long tenantId, long taskId, List<AuditRiskInput> risks, String reportUrl,
                             String modelVersion, String promptVersion, long operatorId) {
        AuditTaskRecord task = requireTask(tenantId, taskId);
        AuditTaskRecord pending = task.withResult(reportUrl, modelVersion, promptVersion, operatorId,
                clock.instant());
        repository.updateTask(pending);
        List<AuditRiskItemRecord> items = new ArrayList<>(risks.size());
        for (AuditRiskInput risk : risks) {
            items.add(new AuditRiskItemRecord(idGenerator.nextId(), tenantId, taskId, risk.riskLevel(),
                    risk.riskContent(), risk.suggestion(), risk.source(), risk.ruleCode(), risk.ruleVersion(),
                    risk.sourceDocName(), risk.sourceChunkKey(), risk.sourceExcerpt(), risk.citationVerified(),
                    risk.clauseNo(), risk.clauseType(), risk.clauseText(), risk.retrievalSnapshot(),
                    false, null, null, null, 1));
        }
        // 驳回重审：先让上一轮风险项失效，避免同一任务出现重复条目（全链路测试发现）
        repository.supersedeByTask(tenantId, taskId);
        repository.insertRiskItems(items);
        log.info("审核结果提交 tenantId={} taskId={} risks={} report={}", tenantId, taskId, items.size(),
                reportUrl);
    }

    @Override
    @Transactional
    public void fail(long tenantId, long taskId, String reason) {
        AuditTaskRecord task = requireTask(tenantId, taskId);
        repository.updateTask(task.moveTo(AuditTaskStatusEnum.FAILED, 0L, clock.instant()));
        log.warn("审核任务失败 tenantId={} taskId={} reason={}", tenantId, taskId, reason);
    }

    /**
     * 完成任务：存在未复核的高/中风险时拒绝（规范 9.2）。
     *
     * @param tenantId   租户
     * @param taskId     任务标识
     * @param conclusion 人工结论摘要
     * @param operatorId 操作人
     * @return 完成后的任务
     */
    @Transactional
    public AuditTaskRecord complete(long tenantId, long taskId, String conclusion, long operatorId) {
        AuditTaskRecord task = requireTask(tenantId, taskId);
        int unreviewed = repository.countUnreviewedRisks(tenantId, taskId);
        if (unreviewed > 0) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL,
                    "unreviewed high/medium risks: " + unreviewed);
        }
        AuditTaskRecord completed = task.complete(conclusion, operatorId, clock.instant());
        repository.updateTask(completed);
        return completed;
    }

    /**
     * 取消任务。
     *
     * @param tenantId   租户
     * @param taskId     任务标识
     * @param operatorId 操作人
     * @return 取消后的任务
     */
    @Transactional
    public AuditTaskRecord cancel(long tenantId, long taskId, long operatorId) {
        AuditTaskRecord task = requireTask(tenantId, taskId);
        AuditTaskRecord cancelled = task.moveTo(AuditTaskStatusEnum.CANCELLED, operatorId, clock.instant());
        repository.updateTask(cancelled);
        return cancelled;
    }

    /**
     * 查询任务。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @return 任务
     */
    public AuditTaskRecord get(long tenantId, long taskId) {
        return requireTask(tenantId, taskId);
    }

    /**
     * 查询合同最近一次审核任务；没有任务返回空（合同列表显示“未审核”）。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 最近任务
     */
    public java.util.Optional<AuditTaskRecord> latestByContract(long tenantId, long contractId) {
        return repository.findLatestByContract(tenantId, contractId);
    }

    /**
     * 查询任务的风险项。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     * @return 风险项
     */
    public List<AuditRiskItemRecord> risks(long tenantId, long taskId) {
        requireTask(tenantId, taskId);
        return repository.findRiskItems(tenantId, taskId);
    }

    private AuditTaskRecord requireTask(long tenantId, long taskId) {
        return repository.findTask(tenantId, taskId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "audit task " + taskId));
    }
}
