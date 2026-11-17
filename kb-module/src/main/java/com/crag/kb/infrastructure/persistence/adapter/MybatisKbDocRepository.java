package com.crag.kb.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.kb.application.port.KbDocRepository;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbDocTypeEnum;
import com.crag.kb.domain.model.KbSyncStatusEnum;
import com.crag.kb.infrastructure.persistence.entity.KbDocEntity;
import com.crag.kb.infrastructure.persistence.mapper.KbDocMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 知识库文档仓储的 MyBatis-Plus 实现。
 */
@Repository
public class MybatisKbDocRepository implements KbDocRepository {

    private final KbDocMapper mapper;
    private final Clock clock;

    /**
     * 构造仓储。
     *
     * @param mapper 文档 Mapper
     * @param clock  统一时钟（规范 3.10：禁止直接取系统时间）
     */
    public MybatisKbDocRepository(KbDocMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<KbDocRecord> findById(long tenantId, long docId) {
        return Optional.ofNullable(toRecord(mapper.selectOne(Wrappers.<KbDocEntity>lambdaQuery()
                .eq(KbDocEntity::getTenantId, tenantId)
                .eq(KbDocEntity::getId, docId))));
    }

    @Override
    public Optional<KbDocRecord> findByDocKeyAndVersion(long tenantId, String docKey, int versionNo) {
        return Optional.ofNullable(toRecord(mapper.selectOne(Wrappers.<KbDocEntity>lambdaQuery()
                .eq(KbDocEntity::getTenantId, tenantId)
                .eq(KbDocEntity::getDocKey, docKey)
                .eq(KbDocEntity::getVersionNo, versionNo))));
    }

    @Override
    public List<KbDocRecord> listVersions(long tenantId, String docKey) {
        return toRecords(mapper.selectList(Wrappers.<KbDocEntity>lambdaQuery()
                .eq(KbDocEntity::getTenantId, tenantId)
                .eq(KbDocEntity::getDocKey, docKey)
                .orderByDesc(KbDocEntity::getVersionNo)));
    }

    @Override
    public List<KbDocRecord> list(long tenantId, Boolean active) {
        var query = Wrappers.<KbDocEntity>lambdaQuery().eq(KbDocEntity::getTenantId, tenantId);
        if (active != null) {
            query.eq(KbDocEntity::getIsActive, active ? 1 : 0);
        }
        query.orderByDesc(KbDocEntity::getUpdatedAt);
        return toRecords(mapper.selectList(query));
    }

    @Override
    public KbDocRecord insert(KbDocRecord doc) {
        KbDocEntity entity = new KbDocEntity();
        entity.setId(doc.id());
        entity.setTenantId(doc.tenantId());
        entity.setDocKey(doc.docKey());
        entity.setTitle(doc.title());
        entity.setDocType(doc.docType().name());
        entity.setVersionNo(doc.versionNo());
        entity.setIsActive(doc.active() ? 1 : 0);
        entity.setSourceHash(doc.sourceHash());
        entity.setSyncedFileHash(doc.syncedFileHash());
        entity.setLastSyncedAt(doc.lastSyncedAt());
        entity.setSyncStatus(doc.syncStatus().name());
        entity.setContentText(doc.contentText());
        entity.setCreatedAt(doc.createdAt());
        entity.setUpdatedAt(doc.createdAt());
        entity.setDeleted(0);
        mapper.insert(entity);
        return doc;
    }

    @Override
    public void update(KbDocRecord doc) {
        KbDocEntity entity = new KbDocEntity();
        entity.setId(doc.id());
        entity.setTitle(doc.title());
        // 不写 is_active：本方法也用于异步同步落库，携带的是同步开始时的快照，
        // 写入会覆盖同步期间用户执行的上下架（多版本切换并发缺陷）
        entity.setSourceHash(doc.sourceHash());
        entity.setSyncedFileHash(doc.syncedFileHash());
        entity.setLastSyncedAt(doc.lastSyncedAt());
        entity.setSyncStatus(doc.syncStatus().name());
        entity.setContentText(doc.contentText());
        entity.setUpdatedAt(clock.instant());
        mapper.updateById(entity);
    }

    @Override
    public void updateActive(long tenantId, long docId, boolean active) {
        // 单独语句只改 is_active 与 updated_at，供上架/下架使用
        mapper.update(null, Wrappers.<KbDocEntity>lambdaUpdate()
                .eq(KbDocEntity::getTenantId, tenantId)
                .eq(KbDocEntity::getId, docId)
                .set(KbDocEntity::getIsActive, active ? 1 : 0)
                .set(KbDocEntity::getUpdatedAt, clock.instant()));
    }

    @Override
    public List<Long> findActiveTenants() {
        return mapper.selectActiveTenantIds();
    }

    @Override
    public List<KbDocRecord> findPendingSync(long tenantId) {
        return toRecords(mapper.selectList(Wrappers.<KbDocEntity>lambdaQuery()
                .eq(KbDocEntity::getTenantId, tenantId)
                .eq(KbDocEntity::getIsActive, 1)
                .apply("synced_file_hash IS NULL OR synced_file_hash <> source_hash")));
    }

    private List<KbDocRecord> toRecords(List<KbDocEntity> entities) {
        List<KbDocRecord> records = new ArrayList<>(entities.size());
        for (KbDocEntity entity : entities) {
            records.add(toRecord(entity));
        }
        return List.copyOf(records);
    }

    private static KbDocRecord toRecord(KbDocEntity entity) {
        if (entity == null) {
            return null;
        }
        return new KbDocRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getDocKey(),
                entity.getTitle(),
                KbDocTypeEnum.valueOf(entity.getDocType()),
                entity.getVersionNo() == null ? 1 : entity.getVersionNo(),
                entity.getIsActive() != null && entity.getIsActive() == 1,
                entity.getSourceHash(),
                entity.getSyncedFileHash(),
                entity.getLastSyncedAt(),
                KbSyncStatusEnum.valueOf(entity.getSyncStatus()),
                entity.getContentText(),
                entity.getCreatedAt());
    }
}
