package com.crag.audit.support;

import com.crag.audit.application.port.AuditRepository;
import com.crag.audit.domain.model.AuditReviewLogEntry;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 审核仓储内存实现，仅用于单元测试（不校验租户隔离，隔离由集成测试覆盖）。
 */
public class FakeAuditRepository implements AuditRepository {

    private final Map<Long, AuditTaskRecord> tasks = new LinkedHashMap<>();
    private final Map<Long, AuditRiskItemRecord> risks = new LinkedHashMap<>();
    private final List<AuditReviewLogEntry> logs = new ArrayList<>();
    private boolean duplicateOnInsert;

    /**
     * 模拟并发唯一键冲突。
     *
     * @param duplicate 是否冲突
     */
    public void duplicateOnInsert(boolean duplicate) {
        this.duplicateOnInsert = duplicate;
    }

    @Override
    public AuditTaskRecord insertTask(AuditTaskRecord task) {
        if (duplicateOnInsert) {
            throw new org.springframework.dao.DuplicateKeyException("simulated");
        }
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<AuditTaskRecord> findTask(long tenantId, long taskId) {
        AuditTaskRecord task = tasks.get(taskId);
        return task != null && task.tenantId() == tenantId ? Optional.of(task) : Optional.empty();
    }

    @Override
    public Optional<AuditTaskRecord> findActiveByContract(long tenantId, long contractId, int contractVersion) {
        return tasks.values().stream()
                .filter(task -> task.tenantId() == tenantId && task.contractId() == contractId
                        && task.contractVersion() == contractVersion
                        && task.status() != AuditTaskStatusEnum.CANCELLED
                        && task.status() != AuditTaskStatusEnum.FAILED)
                .findFirst();
    }

    @Override
    public AuditTaskRecord updateTask(AuditTaskRecord task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<AuditTaskRecord> findLatestByContract(long tenantId, long contractId) {
        return tasks.values().stream()
                .filter(task -> task.tenantId() == tenantId && task.contractId() == contractId)
                .max(java.util.Comparator.comparingLong(AuditTaskRecord::id));
    }

    @Override
    public int supersedeByTask(long tenantId, long taskId) {
        // 内存实现直接移除旧项：单测只关心“当前清单”，历史保留由真实仓储与复核日志承担
        List<Long> ids = risks.values().stream()
                .filter(item -> item.tenantId() == tenantId && item.taskId() == taskId)
                .map(AuditRiskItemRecord::id).toList();
        ids.forEach(risks::remove);
        return ids.size();
    }

    @Override
    public int insertRiskItems(List<AuditRiskItemRecord> items) {
        for (AuditRiskItemRecord item : items) {
            risks.put(item.id(), item);
        }
        return items.size();
    }

    @Override
    public List<AuditRiskItemRecord> findRiskItems(long tenantId, long taskId) {
        return risks.values().stream()
                .filter(item -> item.tenantId() == tenantId && item.taskId() == taskId)
                .toList();
    }

    @Override
    public Optional<AuditRiskItemRecord> findRiskItem(long tenantId, long riskId) {
        AuditRiskItemRecord item = risks.get(riskId);
        return item != null && item.tenantId() == tenantId ? Optional.of(item) : Optional.empty();
    }

    @Override
    public int updateReview(AuditRiskItemRecord item, int expectedVersion) {
        AuditRiskItemRecord current = risks.get(item.id());
        if (current == null || current.versionNo() != expectedVersion) {
            return 0;
        }
        risks.put(item.id(), item);
        return 1;
    }

    @Override
    public void appendReviewLog(AuditReviewLogEntry entry) {
        logs.add(entry);
    }

    @Override
    public List<AuditReviewLogEntry> findReviewLogs(long tenantId, long taskId) {
        return logs.stream().filter(log -> log.taskId() == taskId).toList();
    }

    @Override
    public int countUnreviewedRisks(long tenantId, long taskId) {
        return (int) risks.values().stream()
                .filter(item -> item.taskId() == taskId && item.blocksCompletion())
                .count();
    }

    @Override
    public List<RejectedSample> findRejectedSamples(long tenantId, Instant since) {
        return logs.stream()
                .filter(log -> log.tenantId() == tenantId
                        && log.action() == com.crag.audit.domain.model.ReviewActionEnum.REJECT)
                .map(log -> new RejectedSample(risks.get(log.riskItemId()), log.reasonCode().name(), log.createdAt()))
                .toList();
    }

    @Override
    public long countTasks(long tenantId, AuditTaskStatusEnum status) {
        return tasks.values().stream().filter(task -> task.status() == status).count();
    }

    /**
     * 测试辅助：取日志。
     *
     * @return 日志
     */
    public List<AuditReviewLogEntry> logs() {
        return List.copyOf(logs);
    }
}
