package com.crag.doc.support;

import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.doc.domain.model.ContractChunkRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同切片仓储内存实现，仅用于单元测试。
 */
public class FakeChunkRepository implements ContractChunkRepository {

    private final Map<Long, List<ContractChunkRecord>> storage = new HashMap<>();

    @Override
    public void replaceAll(long tenantId, long contractId, List<ContractChunkRecord> chunks) {
        storage.put(contractId, new ArrayList<>(chunks));
    }

    @Override
    public List<ContractChunkRecord> findByContractId(long tenantId, long contractId) {
        return List.copyOf(storage.getOrDefault(contractId, List.of()));
    }

    @Override
    public void deleteByContractId(long tenantId, long contractId) {
        storage.remove(contractId);
    }
}
