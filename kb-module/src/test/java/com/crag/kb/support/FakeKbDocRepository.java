package com.crag.kb.support;

import com.crag.kb.application.port.KbDocRepository;
import com.crag.kb.domain.model.KbDocRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识库文档仓储内存实现，仅用于单元测试。
 */
public class FakeKbDocRepository implements KbDocRepository {

    private final Map<Long, KbDocRecord> storage = new LinkedHashMap<>();

    @Override
    public Optional<KbDocRecord> findById(long tenantId, long docId) {
        KbDocRecord record = storage.get(docId);
        return record != null && record.tenantId() == tenantId ? Optional.of(record) : Optional.empty();
    }

    @Override
    public Optional<KbDocRecord> findByDocKeyAndVersion(long tenantId, String docKey, int versionNo) {
        return storage.values().stream()
                .filter(record -> record.tenantId() == tenantId && record.docKey().equals(docKey)
                        && record.versionNo() == versionNo)
                .findFirst();
    }

    @Override
    public List<KbDocRecord> listVersions(long tenantId, String docKey) {
        return storage.values().stream()
                .filter(record -> record.tenantId() == tenantId && record.docKey().equals(docKey))
                .sorted(Comparator.comparingInt(KbDocRecord::versionNo).reversed())
                .toList();
    }

    @Override
    public List<KbDocRecord> list(long tenantId, Boolean active) {
        return storage.values().stream()
                .filter(record -> record.tenantId() == tenantId)
                .filter(record -> active == null || record.active() == active)
                .toList();
    }

    @Override
    public List<Long> findActiveTenants() {
        return storage.values().stream()
                .filter(KbDocRecord::active)
                .map(KbDocRecord::tenantId)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<KbDocRecord> findPendingSync(long tenantId) {
        return storage.values().stream()
                .filter(record -> record.tenantId() == tenantId && record.active())
                .filter(record -> record.syncedFileHash() == null
                        || !record.syncedFileHash().equals(record.sourceHash()))
                .toList();
    }

    @Override
    public KbDocRecord insert(KbDocRecord doc) {
        storage.put(doc.id(), doc);
        return doc;
    }

    @Override
    public void update(KbDocRecord doc) {
        storage.put(doc.id(), doc);
    }

    @Override
    public void updateActive(long tenantId, long docId, boolean active) {
        KbDocRecord record = storage.get(docId);
        if (record != null && record.tenantId() == tenantId) {
            storage.put(docId, record.withActive(active));
        }
    }

    /**
     * 测试辅助：写入。
     *
     * @param doc 文档
     */
    public void seed(KbDocRecord doc) {
        storage.put(doc.id(), doc);
    }

    /**
     * 测试辅助：按标识读取。
     *
     * @param docId 标识
     * @return 文档
     */
    public KbDocRecord get(long docId) {
        return storage.get(docId);
    }

    /**
     * 测试辅助：当前版本列表。
     *
     * @return 文档列表
     */
    public List<KbDocRecord> all() {
        return new ArrayList<>(storage.values());
    }
}
