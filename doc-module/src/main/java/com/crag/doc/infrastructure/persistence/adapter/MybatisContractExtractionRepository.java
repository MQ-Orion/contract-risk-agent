package com.crag.doc.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.doc.application.port.ContractExtractionRepository;
import com.crag.doc.domain.model.ContractExtractionRecord;
import com.crag.doc.domain.model.ExtractStatusEnum;
import com.crag.doc.infrastructure.persistence.entity.ContractExtractEntity;
import com.crag.doc.infrastructure.persistence.mapper.ContractExtractMapper;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同抽取结果仓储实现。
 *
 * <p>同一合同只保留一行：存在则覆盖（派生的抽取结论，不保留多份历史；历史版本由合同版本与审核快照承载）。</p>
 */
@Repository
public class MybatisContractExtractionRepository implements ContractExtractionRepository {

    private final ContractExtractMapper mapper;
    private final Clock clock;

    /**
     * 构造仓储。
     *
     * @param mapper 抽取结果 Mapper
     * @param clock  统一时钟
     */
    public MybatisContractExtractionRepository(ContractExtractMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ContractExtractionRecord saveOrUpdate(ContractExtractionRecord record) {
        ContractExtractEntity existing = mapper.selectOne(Wrappers.<ContractExtractEntity>lambdaQuery()
                .eq(ContractExtractEntity::getTenantId, record.tenantId())
                .eq(ContractExtractEntity::getContractId, record.contractId()));
        ContractExtractEntity entity = toEntity(record);
        if (existing == null) {
            entity.setDeleted(0);
            mapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            entity.setUpdatedAt(clock.instant());
            mapper.updateById(entity);
        }
        return record;
    }

    @Override
    public Optional<ContractExtractionRecord> findByContract(long tenantId, long contractId) {
        ContractExtractEntity entity = mapper.selectOne(Wrappers.<ContractExtractEntity>lambdaQuery()
                .eq(ContractExtractEntity::getTenantId, tenantId)
                .eq(ContractExtractEntity::getContractId, contractId));
        return Optional.ofNullable(toRecord(entity));
    }

    private static ContractExtractEntity toEntity(ContractExtractionRecord record) {
        ContractExtractEntity entity = new ContractExtractEntity();
        entity.setId(record.id());
        entity.setTenantId(record.tenantId());
        entity.setContractId(record.contractId());
        entity.setContractAmount(record.contractAmount());
        entity.setPaymentDays(record.paymentDays());
        entity.setJurisdiction(record.jurisdiction());
        entity.setHasJointLiability(record.hasJointLiability() == null ? null : (record.hasJointLiability() ? 1 : 0));
        entity.setExtractStatus(record.status().name());
        entity.setModelRawResponse(record.modelRawResponse());
        entity.setSourceSpanJson(record.sourceSpanJson());
        entity.setPromptVersion(record.promptVersion());
        entity.setModelName(record.modelName());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }

    private static ContractExtractionRecord toRecord(ContractExtractEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ContractExtractionRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getContractId(),
                entity.getContractAmount(),
                entity.getPaymentDays(),
                entity.getJurisdiction(),
                entity.getHasJointLiability() == null ? null : entity.getHasJointLiability() == 1,
                ExtractStatusEnum.fromCode(entity.getExtractStatus()),
                entity.getModelRawResponse(),
                entity.getSourceSpanJson(),
                entity.getPromptVersion(),
                entity.getModelName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
