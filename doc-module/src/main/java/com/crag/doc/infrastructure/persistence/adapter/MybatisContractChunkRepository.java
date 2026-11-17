package com.crag.doc.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.common.text.ClauseTypeEnum;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.common.text.TypeSourceEnum;
import com.crag.doc.infrastructure.persistence.entity.ContractChunkEntity;
import com.crag.doc.infrastructure.persistence.mapper.ContractChunkMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 合同切片仓储实现。
 *
 * <p>重新解析时先逻辑删除旧切片再批量写入；逻辑删除保证唯一索引只约束有效行（规范 4.1、4.6）。</p>
 */
@Repository
public class MybatisContractChunkRepository implements ContractChunkRepository {

    /** 单批写入行数上限（规范 4.6）。 */
    private static final int BATCH_SIZE = 500;

    private final ContractChunkMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 切片 Mapper
     */
    public MybatisContractChunkRepository(ContractChunkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void replaceAll(long tenantId, long contractId, List<ContractChunkRecord> chunks) {
        // 先逻辑删除旧切片：唯一索引 uk_contract_chunk_index(contract_id, full_text_index) WHERE deleted = 0
        // 只约束有效行，所以删除与插入可以并存；物理删除会丢失历史条款，违反“业务数据禁止物理删除”（规范 4.1）。
        // 本方法不自带事务：删除与批量插入必须由调用方（ParseProgressWriter）放在同一事务里，否则中途失败会留下半份切片。
        mapper.delete(Wrappers.<ContractChunkEntity>lambdaQuery()
                .eq(ContractChunkEntity::getTenantId, tenantId)
                .eq(ContractChunkEntity::getContractId, contractId));
        // 500 条一批（规范 4.6）：单条提交会把一次解析的往返次数放大到切片段数，批量过大又会撑爆参数上限。
        List<ContractChunkEntity> buffer = new ArrayList<>(Math.min(chunks.size(), BATCH_SIZE));
        for (ContractChunkRecord chunk : chunks) {
            buffer.add(toEntity(chunk));
            if (buffer.size() == BATCH_SIZE) {
                mapper.insertBatch(buffer);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            mapper.insertBatch(buffer);
        }
    }

    @Override
    public List<ContractChunkRecord> findByContractId(long tenantId, long contractId) {
        List<ContractChunkEntity> entities = mapper.selectList(Wrappers.<ContractChunkEntity>lambdaQuery()
                .eq(ContractChunkEntity::getTenantId, tenantId)
                .eq(ContractChunkEntity::getContractId, contractId)
                .orderByAsc(ContractChunkEntity::getFullTextIndex));
        List<ContractChunkRecord> records = new ArrayList<>(entities.size());
        for (ContractChunkEntity entity : entities) {
            records.add(toRecord(entity));
        }
        return List.copyOf(records);
    }

    @Override
    public void deleteByContractId(long tenantId, long contractId) {
        // 逻辑删除：@TableLogic 改写成 deleted=1；合同详情已不可见，切片也不应再被条款接口读到
        mapper.delete(Wrappers.<ContractChunkEntity>lambdaQuery()
                .eq(ContractChunkEntity::getTenantId, tenantId)
                .eq(ContractChunkEntity::getContractId, contractId));
    }

    private static ContractChunkEntity toEntity(ContractChunkRecord record) {
        ContractChunkEntity entity = new ContractChunkEntity();
        entity.setId(record.id());
        entity.setTenantId(record.tenantId());
        entity.setContractId(record.contractId());
        entity.setClauseNo(record.clauseNo());
        entity.setChunkType(record.clauseType().name());
        entity.setTypeSource(record.typeSource().name());
        entity.setChunkText(record.chunkText());
        entity.setFullTextIndex(record.fullTextIndex());
        entity.setCharStart(record.charStart());
        entity.setCharEnd(record.charEnd());
        entity.setDeleted(0);
        return entity;
    }

    private static ContractChunkRecord toRecord(ContractChunkEntity entity) {
        return new ContractChunkRecord(
                entity.getId(),
                entity.getContractId(),
                entity.getTenantId(),
                entity.getClauseNo(),
                ClauseTypeEnum.fromCode(entity.getChunkType()),
                entity.getTypeSource() == null ? TypeSourceEnum.DEFAULT : TypeSourceEnum.valueOf(entity.getTypeSource()),
                entity.getChunkText(),
                entity.getFullTextIndex() == null ? 0 : entity.getFullTextIndex(),
                entity.getCharStart() == null ? 0 : entity.getCharStart(),
                entity.getCharEnd() == null ? 0 : entity.getCharEnd());
    }
}
