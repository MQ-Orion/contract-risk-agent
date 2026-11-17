package com.crag.audit.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.audit.application.port.AuditRepository;
import com.crag.audit.domain.model.AuditReviewLogEntry;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import com.crag.audit.domain.model.RejectReasonCodeEnum;
import com.crag.audit.domain.model.ReviewActionEnum;
import com.crag.audit.infrastructure.persistence.entity.AuditReviewLogEntity;
import com.crag.audit.infrastructure.persistence.entity.AuditRiskItemEntity;
import com.crag.audit.infrastructure.persistence.entity.AuditTaskEntity;
import com.crag.audit.infrastructure.persistence.mapper.AuditReviewLogMapper;
import com.crag.audit.infrastructure.persistence.mapper.AuditRiskItemMapper;
import com.crag.audit.infrastructure.persistence.mapper.AuditTaskMapper;
import com.crag.common.domain.RiskLevelEnum;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 审核仓储的 MyBatis-Plus 实现（三张表）。
 *
 * <p>租户条件由拦截器注入（规范 4.4），这里仍显式带 {@code tenantId} 作为第二道保障；
 * 乐观锁更新以 {@code version_no = expected} 为条件，0 行即冲突（规范 10.7）。</p>
 *
 * <p>为什么风险项与日志要分开写：日志只追加且不允许更新，与业务行的可变生命周期不同，
 * 合表会让“日志不可改”这条约束失效（规范 11.6）。</p>
 */
@Repository
public class MybatisAuditRepository implements AuditRepository {

    private final AuditTaskMapper taskMapper;
    private final AuditRiskItemMapper riskMapper;
    private final AuditReviewLogMapper logMapper;
    private final Clock clock;

    /**
     * 构造仓储。
     *
     * @param taskMapper 任务 Mapper
     * @param riskMapper 风险 Mapper
     * @param logMapper  日志 Mapper
     * @param clock      统一时钟
     */
    public MybatisAuditRepository(AuditTaskMapper taskMapper, AuditRiskItemMapper riskMapper,
                                  AuditReviewLogMapper logMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.riskMapper = riskMapper;
        this.logMapper = logMapper;
        this.clock = clock;
    }

    @Override
    public AuditTaskRecord insertTask(AuditTaskRecord task) {
        AuditTaskEntity entity = toEntity(task);
        taskMapper.insert(entity);
        return task;
    }

    @Override
    public Optional<AuditTaskRecord> findTask(long tenantId, long taskId) {
        AuditTaskEntity entity = taskMapper.selectOne(Wrappers.<AuditTaskEntity>query()
                .eq("tenant_id", tenantId).eq("id", taskId).eq("deleted", 0));
        return Optional.ofNullable(toRecord(entity));
    }

    @Override
    public Optional<AuditTaskRecord> findActiveByContract(long tenantId, long contractId, int contractVersion) {
        AuditTaskEntity entity = taskMapper.selectOne(Wrappers.<AuditTaskEntity>query()
                .eq("tenant_id", tenantId).eq("contract_id", contractId)
                .eq("contract_version", contractVersion).eq("deleted", 0)
                .notIn("status", AuditTaskStatusEnum.COMPLETED.name(), AuditTaskStatusEnum.CANCELLED.name(), AuditTaskStatusEnum.FAILED.name())
                .last("limit 1"));
        return Optional.ofNullable(toRecord(entity));
    }

    @Override
    public Optional<AuditTaskRecord> findLatestByContract(long tenantId, long contractId) {
        AuditTaskEntity entity = taskMapper.selectOne(Wrappers.<AuditTaskEntity>query()
                .eq("tenant_id", tenantId).eq("contract_id", contractId).eq("deleted", 0)
                .orderByDesc("id").last("limit 1"));
        return Optional.ofNullable(toRecord(entity));
    }

    @Override
    public AuditTaskRecord updateTask(AuditTaskRecord task) {
        AuditTaskEntity entity = toEntity(task);
        entity.updatedAt = clock.instant();
        taskMapper.updateById(entity);
        return task;
    }

    @Override
    public int insertRiskItems(List<AuditRiskItemRecord> items) {
        int written = 0;
        for (AuditRiskItemRecord item : items) {
            AuditRiskItemEntity entity = toEntity(item);
            entity.createdAt = clock.instant();
            entity.updatedAt = entity.createdAt;
            entity.deleted = 0;
            riskMapper.insert(entity);
            written++;
        }
        return written;
    }

    @Override
    public int supersedeByTask(long tenantId, long taskId) {
        // 驳回重审：旧一轮风险项保留（复核日志外键与追溯需要），仅置为历史；当前清单只看 superseded=0
        return riskMapper.update(null, Wrappers.<AuditRiskItemEntity>update()
                .eq("tenant_id", tenantId)
                .eq("task_id", taskId)
                .eq("superseded", 0)
                .set("superseded", 1)
                .set("updated_at", clock.instant()));
    }

    @Override
    public List<AuditRiskItemRecord> findRiskItems(long tenantId, long taskId) {
        List<AuditRiskItemEntity> entities = riskMapper.selectList(Wrappers.<AuditRiskItemEntity>query()
                .eq("tenant_id", tenantId).eq("task_id", taskId).eq("deleted", 0).eq("superseded", 0)
                .orderByAsc("risk_level").orderByAsc("id"));
        List<AuditRiskItemRecord> items = new ArrayList<>(entities.size());
        for (AuditRiskItemEntity entity : entities) {
            items.add(toRecord(entity));
        }
        return List.copyOf(items);
    }

    @Override
    public Optional<AuditRiskItemRecord> findRiskItem(long tenantId, long riskId) {
        AuditRiskItemEntity entity = riskMapper.selectOne(Wrappers.<AuditRiskItemEntity>query()
                .eq("tenant_id", tenantId).eq("id", riskId).eq("deleted", 0).eq("superseded", 0));
        return Optional.ofNullable(toRecord(entity));
    }

    @Override
    public int updateReview(AuditRiskItemRecord item, int expectedVersion) {
        return riskMapper.update(null, Wrappers.<AuditRiskItemEntity>update()
                .eq("tenant_id", item.tenantId())
                .eq("id", item.id())
                .eq("version_no", expectedVersion)
                .set("reviewed", 1)
                .set("review_result", item.reviewResult() == null ? null : item.reviewResult().name())
                .set("reviewed_by", item.reviewedBy())
                .set("reviewed_at", item.reviewedAt())
                .set("version_no", item.versionNo())
                .set("updated_at", clock.instant()));
    }

    @Override
    public void appendReviewLog(AuditReviewLogEntry entry) {
        AuditReviewLogEntity entity = new AuditReviewLogEntity();
        entity.id = entry.id();
        entity.tenantId = entry.tenantId();
        entity.taskId = entry.taskId();
        entity.riskItemId = entry.riskItemId();
        entity.reviewerId = entry.reviewerId();
        entity.reviewerName = entry.reviewerName();
        entity.action = entry.action().name();
        entity.reasonCode = entry.reasonCode() == null ? null : entry.reasonCode().name();
        entity.comment = entry.comment();
        entity.traceId = entry.traceId();
        entity.createdAt = entry.createdAt();
        logMapper.insert(entity);
    }

    @Override
    public List<AuditReviewLogEntry> findReviewLogs(long tenantId, long taskId) {
        List<AuditReviewLogEntity> entities = logMapper.selectList(Wrappers.<AuditReviewLogEntity>query()
                .eq("tenant_id", tenantId).eq("task_id", taskId).orderByAsc("created_at"));
        List<AuditReviewLogEntry> entries = new ArrayList<>(entities.size());
        for (AuditReviewLogEntity entity : entities) {
            entries.add(toRecord(entity));
        }
        return List.copyOf(entries);
    }

    @Override
    public int countUnreviewedRisks(long tenantId, long taskId) {
        Long count = riskMapper.selectCount(Wrappers.<AuditRiskItemEntity>query()
                .eq("tenant_id", tenantId).eq("task_id", taskId).eq("deleted", 0).eq("superseded", 0)
                .eq("reviewed", 0)
                .in("risk_level", RiskLevelEnum.HIGH.name(), RiskLevelEnum.MEDIUM.name()));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public List<RejectedSample> findRejectedSamples(long tenantId, Instant since) {
        List<AuditReviewLogEntity> logs = logMapper.selectList(Wrappers.<AuditReviewLogEntity>query()
                .eq("tenant_id", tenantId).eq("action", ReviewActionEnum.REJECT.name())
                .ge("created_at", since).orderByAsc("created_at"));
        List<RejectedSample> samples = new ArrayList<>(logs.size());
        for (AuditReviewLogEntity log : logs) {
            AuditRiskItemEntity item = riskMapper.selectOne(Wrappers.<AuditRiskItemEntity>query()
                    .eq("tenant_id", tenantId).eq("id", log.riskItemId).eq("deleted", 0));
            if (item != null) {
                samples.add(new RejectedSample(toRecord(item), log.reasonCode, log.createdAt));
            }
        }
        return List.copyOf(samples);
    }

    @Override
    public long countTasks(long tenantId, AuditTaskStatusEnum status) {
        Long count = taskMapper.selectCount(Wrappers.<AuditTaskEntity>query()
                .eq("tenant_id", tenantId).eq("status", status.name()).eq("deleted", 0));
        return count == null ? 0L : count;
    }

    private static AuditTaskEntity toEntity(AuditTaskRecord task) {
        AuditTaskEntity entity = new AuditTaskEntity();
        entity.id = task.id();
        entity.tenantId = task.tenantId();
        entity.contractId = task.contractId();
        entity.contractVersion = task.contractVersion();
        entity.status = task.status().name();
        entity.auditorId = task.auditorId();
        entity.conclusion = task.conclusion();
        entity.modelVersion = task.modelVersion();
        entity.promptVersion = task.promptVersion();
        entity.reportUrl = task.reportUrl();
        entity.startedAt = task.startedAt();
        entity.finishedAt = task.finishedAt();
        entity.createdAt = task.createdAt();
        entity.updatedAt = task.updatedAt();
        entity.createdBy = task.auditorId();
        entity.deleted = 0;
        return entity;
    }

    private static AuditTaskRecord toRecord(AuditTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AuditTaskRecord(entity.id, entity.tenantId, entity.contractId,
                entity.contractVersion == null ? 1 : entity.contractVersion,
                AuditTaskStatusEnum.fromCode(entity.status), entity.auditorId, entity.conclusion,
                entity.modelVersion, entity.promptVersion, entity.reportUrl, entity.startedAt, entity.finishedAt,
                entity.createdAt, entity.updatedAt);
    }

    private static AuditRiskItemEntity toEntity(AuditRiskItemRecord item) {
        AuditRiskItemEntity entity = new AuditRiskItemEntity();
        entity.id = item.id();
        entity.tenantId = item.tenantId();
        entity.taskId = item.taskId();
        entity.riskLevel = item.riskLevel().name();
        entity.riskContent = item.riskContent();
        entity.suggestion = item.suggestion();
        entity.source = item.source();
        entity.ruleCode = item.ruleCode();
        entity.ruleVersion = item.ruleVersion();
        entity.sourceDocName = item.sourceDocName();
        entity.sourceChunkKey = item.sourceChunkKey();
        entity.sourceExcerpt = item.sourceExcerpt();
        entity.citationVerified = item.citationVerified() ? 1 : 0;
        entity.clauseNo = item.clauseNo();
        entity.clauseType = item.clauseType();
        entity.clauseText = item.clauseText();
        entity.retrievalSnapshot = item.retrievalSnapshot();
        entity.reviewed = item.reviewed() ? 1 : 0;
        entity.reviewResult = item.reviewResult() == null ? null : item.reviewResult().name();
        entity.reviewedBy = item.reviewedBy();
        entity.reviewedAt = item.reviewedAt();
        entity.versionNo = item.versionNo();
        entity.superseded = 0;
        entity.deleted = 0;
        return entity;
    }

    private static AuditRiskItemRecord toRecord(AuditRiskItemEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AuditRiskItemRecord(entity.id, entity.tenantId, entity.taskId,
                RiskLevelEnum.fromCode(entity.riskLevel), entity.riskContent, entity.suggestion, entity.source,
                entity.ruleCode, entity.ruleVersion, entity.sourceDocName, entity.sourceChunkKey,
                entity.sourceExcerpt, entity.citationVerified != null && entity.citationVerified == 1,
                entity.clauseNo, entity.clauseType, entity.clauseText, entity.retrievalSnapshot,
                entity.reviewed != null && entity.reviewed == 1,
                ReviewActionEnum.fromCode(entity.reviewResult), entity.reviewedBy, entity.reviewedAt,
                entity.versionNo == null ? 1 : entity.versionNo);
    }

    private static AuditReviewLogEntry toRecord(AuditReviewLogEntity entity) {
        return new AuditReviewLogEntry(entity.id, entity.tenantId, entity.taskId, entity.riskItemId,
                entity.reviewerId, entity.reviewerName, ReviewActionEnum.fromCode(entity.action),
                RejectReasonCodeEnum.fromCode(entity.reasonCode), entity.comment, entity.traceId,
                entity.createdAt);
    }
}
