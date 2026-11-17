package com.crag.doc.support;

import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.domain.model.ContractRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 合同仓储内存实现，仅用于单元测试隔离数据库。
 */
public class FakeContractRepository implements ContractRepository {

    private final Map<Long, ContractRecord> storage = new LinkedHashMap<>();

    @Override
    public Optional<ContractRecord> findById(long tenantId, long contractId) {
        ContractRecord record = storage.get(contractId);
        return record != null && record.tenantId() == tenantId ? Optional.of(record) : Optional.empty();
    }

    @Override
    public Optional<ContractRecord> findByHash(long tenantId, String fileHash) {
        return storage.values().stream()
                .filter(record -> record.tenantId() == tenantId && record.fileHash().equals(fileHash))
                .findFirst();
    }

    @Override
    public ContractRecord insert(ContractRecord contract) {
        storage.put(contract.id(), contract);
        return contract;
    }

    @Override
    public void updatePageCount(long tenantId, long contractId, int pageCount) {
        ContractRecord record = storage.get(contractId);
        if (record != null) {
            storage.put(contractId, record.withPageCount(pageCount));
        }
    }

    @Override
    public List<ContractRecord> findPage(long tenantId, int offset, int limit) {
        List<ContractRecord> all = new ArrayList<>(storage.values().stream()
                .filter(record -> record.tenantId() == tenantId)
                .sorted(Comparator.comparing(ContractRecord::createdAt).reversed())
                .toList());
        int from = Math.min(offset, all.size());
        int to = Math.min(from + limit, all.size());
        return List.copyOf(all.subList(from, to));
    }

    @Override
    public long countByTenant(long tenantId) {
        return storage.values().stream().filter(record -> record.tenantId() == tenantId).count();
    }

    @Override
    public void markDeleted(long tenantId, long contractId) {
        ContractRecord record = storage.get(contractId);
        if (record != null && record.tenantId() == tenantId) {
            storage.remove(contractId);
        }
    }

    /**
     * 测试辅助：直接写入。
     *
     * @param contract 合同
     */
    public void seed(ContractRecord contract) {
        storage.put(contract.id(), contract);
    }

    /**
     * 测试辅助：当前记录数。
     *
     * @return 数量
     */
    public int size() {
        return storage.size();
    }
}
