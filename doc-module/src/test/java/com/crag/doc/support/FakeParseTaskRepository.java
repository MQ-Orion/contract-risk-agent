package com.crag.doc.support;

import com.crag.doc.application.port.ParseTaskRepository;
import com.crag.doc.domain.model.ParseTaskRecord;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 解析任务仓储内存实现，仅用于单元测试。
 */
public class FakeParseTaskRepository implements ParseTaskRepository {

    private final Map<Long, ParseTaskRecord> storage = new LinkedHashMap<>();

    @Override
    public Optional<ParseTaskRecord> findById(long taskId) {
        return Optional.ofNullable(storage.get(taskId));
    }

    @Override
    public Optional<ParseTaskRecord> findLatestByContractId(long tenantId, long contractId) {
        return storage.values().stream()
                .filter(task -> task.tenantId() == tenantId && task.contractId() == contractId)
                .max(Comparator.comparing(ParseTaskRecord::createdAt));
    }

    @Override
    public ParseTaskRecord insert(ParseTaskRecord task) {
        storage.put(task.id(), task);
        return task;
    }

    @Override
    public void update(ParseTaskRecord task) {
        storage.put(task.id(), task);
    }
}
