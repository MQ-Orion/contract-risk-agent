package com.crag.doc.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.doc.infrastructure.persistence.entity.ContractEntity;
import com.crag.doc.infrastructure.persistence.mapper.ContractMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 合同仓储的 MyBatis-Plus 实现。
 *
 * <p>查询必须显式带租户条件；逻辑删除由 {@code @TableLogic} 自动追加（规范 4.1）。</p>
 */
@Repository
public class MybatisContractRepository implements ContractRepository {

    private final ContractMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 合同 Mapper
     */
    public MybatisContractRepository(ContractMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ContractRecord> findById(long tenantId, long contractId) {
        ContractEntity entity = mapper.selectOne(Wrappers.<ContractEntity>lambdaQuery()
                .eq(ContractEntity::getTenantId, tenantId)
                .eq(ContractEntity::getId, contractId));
        return Optional.ofNullable(toRecord(entity));
    }

    @Override
    public Optional<ContractRecord> findByHash(long tenantId, String fileHash) {
        ContractEntity entity = mapper.selectOne(Wrappers.<ContractEntity>lambdaQuery()
                .eq(ContractEntity::getTenantId, tenantId)
                .eq(ContractEntity::getFileHash, fileHash));
        return Optional.ofNullable(toRecord(entity));
    }

    @Override
    public ContractRecord insert(ContractRecord contract) {
        ContractEntity entity = toEntity(contract);
        mapper.insert(entity);
        return contract;
    }

    @Override
    public void updatePageCount(long tenantId, long contractId, int pageCount) {
        mapper.update(null, Wrappers.<ContractEntity>lambdaUpdate()
                .eq(ContractEntity::getTenantId, tenantId)
                .eq(ContractEntity::getId, contractId)
                .set(ContractEntity::getPageCount, pageCount));
    }

    @Override
    public List<ContractRecord> findPage(long tenantId, int offset, int limit) {
        List<ContractEntity> entities = mapper.selectList(Wrappers.<ContractEntity>lambdaQuery()
                .eq(ContractEntity::getTenantId, tenantId)
                .orderByDesc(ContractEntity::getCreatedAt)
                .last("limit " + limit + " offset " + offset));
        List<ContractRecord> records = new ArrayList<>(entities.size());
        for (ContractEntity entity : entities) {
            records.add(toRecord(entity));
        }
        return List.copyOf(records);
    }

    @Override
    public long countByTenant(long tenantId) {
        Long count = mapper.selectCount(Wrappers.<ContractEntity>lambdaQuery()
                .eq(ContractEntity::getTenantId, tenantId));
        return count == null ? 0L : count;
    }

    @Override
    public void markDeleted(long tenantId, long contractId) {
        // @TableLogic 把 delete 改写为 deleted=1；租户条件由拦截器追加，这里再显式带一次防漏
        mapper.delete(Wrappers.<ContractEntity>lambdaQuery()
                .eq(ContractEntity::getTenantId, tenantId)
                .eq(ContractEntity::getId, contractId));
    }

    private static ContractRecord toRecord(ContractEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ContractRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getContractNo(),
                entity.getName(),
                entity.getFileHash(),
                entity.getFilePath(),
                DocumentFormatEnum.valueOf(entity.getFileFormat()),
                entity.getFileSize() == null ? 0L : entity.getFileSize(),
                entity.getPageCount() == null ? 0 : entity.getPageCount(),
                entity.getUploaderId() == null ? 0L : entity.getUploaderId(),
                entity.getAuditStatus(),
                entity.getVersionNo() == null ? 1 : entity.getVersionNo(),
                entity.getCreatedAt());
    }

    private static ContractEntity toEntity(ContractRecord record) {
        ContractEntity entity = new ContractEntity();
        entity.setId(record.id());
        entity.setTenantId(record.tenantId());
        entity.setContractNo(record.contractNo());
        entity.setName(record.name());
        entity.setFileHash(record.fileHash());
        entity.setFilePath(record.filePath());
        entity.setFileFormat(record.format().name());
        entity.setFileSize(record.fileSize());
        entity.setPageCount(record.pageCount());
        entity.setUploaderId(record.uploaderId());
        entity.setAuditStatus(record.auditStatus());
        entity.setVersionNo(record.versionNo());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.createdAt());
        entity.setCreatedBy(record.uploaderId());
        entity.setDeleted(0);
        return entity;
    }
}
