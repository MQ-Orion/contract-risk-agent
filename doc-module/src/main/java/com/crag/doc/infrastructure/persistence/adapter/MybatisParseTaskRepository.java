package com.crag.doc.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.doc.application.port.ParseTaskRepository;
import com.crag.doc.domain.model.ParseStatusEnum;
import com.crag.doc.domain.model.ParseTaskRecord;
import com.crag.doc.infrastructure.persistence.entity.ContractParseTaskEntity;
import com.crag.doc.infrastructure.persistence.mapper.ContractParseTaskMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 解析任务仓储的 MyBatis-Plus 实现。
 */
@Repository
public class MybatisParseTaskRepository implements ParseTaskRepository {

    private final ContractParseTaskMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 任务 Mapper
     */
    public MybatisParseTaskRepository(ContractParseTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ParseTaskRecord> findById(long taskId) {
        return Optional.ofNullable(toRecord(mapper.selectById(taskId)));
    }

    @Override
    public Optional<ParseTaskRecord> findLatestByContractId(long tenantId, long contractId) {
        ContractParseTaskEntity entity = mapper.selectOne(Wrappers.<ContractParseTaskEntity>lambdaQuery()
                .eq(ContractParseTaskEntity::getTenantId, tenantId)
                .eq(ContractParseTaskEntity::getContractId, contractId)
                .orderByDesc(ContractParseTaskEntity::getCreatedAt)
                .last("limit 1"));
        return Optional.ofNullable(toRecord(entity));
    }

    @Override
    public ParseTaskRecord insert(ParseTaskRecord task) {
        ContractParseTaskEntity entity = new ContractParseTaskEntity();
        entity.setId(task.id());
        entity.setTenantId(task.tenantId());
        entity.setContractId(task.contractId());
        entity.setStatus(task.status().name());
        entity.setRetryCount(task.retryCount());
        entity.setNextRetryAt(task.nextRetryAt());
        entity.setErrorCode(task.errorCode());
        entity.setErrorMessage(task.errorMessage());
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
        entity.setCreatedBy(0L);
        entity.setDeleted(0);
        mapper.insert(entity);
        return task;
    }

    @Override
    public void update(ParseTaskRecord task) {
        ContractParseTaskEntity entity = new ContractParseTaskEntity();
        entity.setId(task.id());
        entity.setStatus(task.status().name());
        entity.setRetryCount(task.retryCount());
        entity.setNextRetryAt(task.nextRetryAt());
        entity.setErrorCode(task.errorCode());
        entity.setErrorMessage(task.errorMessage());
        entity.setUpdatedAt(task.updatedAt());
        if (task.status() == ParseStatusEnum.PARSING) {
            entity.setStartedAt(task.updatedAt());
        }
        if (task.status().isTerminal()) {
            entity.setFinishedAt(task.updatedAt());
        }
        mapper.updateById(entity);
    }

    private static ParseTaskRecord toRecord(ContractParseTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ParseTaskRecord(
                entity.getId(),
                entity.getContractId(),
                entity.getTenantId(),
                ParseStatusEnum.valueOf(entity.getStatus()),
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                entity.getNextRetryAt(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreatedAt() == null ? Instant.EPOCH : entity.getCreatedAt(),
                entity.getUpdatedAt() == null ? Instant.EPOCH : entity.getUpdatedAt());
    }
}
