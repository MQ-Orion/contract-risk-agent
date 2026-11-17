package com.crag.agent.model.persistence;

import com.crag.agent.model.ModelCallLog;
import com.crag.agent.model.ModelCallLogRepository;
import com.crag.common.domain.SnowflakeIdGenerator;
import org.springframework.stereotype.Repository;

/**
 * 模型调用日志仓储实现：只插入，不更新。
 *
 * <p>标识由仓储生成：调用日志是基础设施产物，不应让业务侧负责主键（业务侧只关心“记了哪些字段”）。</p>
 */
@Repository
public class MybatisModelCallLogRepository implements ModelCallLogRepository {

    private final ModelCallLogMapper mapper;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 构造仓储。
     *
     * @param mapper      日志 Mapper
     * @param idGenerator 雪花标识
     */
    public MybatisModelCallLogRepository(ModelCallLogMapper mapper, SnowflakeIdGenerator idGenerator) {
        this.mapper = mapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public void append(ModelCallLog log) {
        ModelCallLogEntity entity = new ModelCallLogEntity();
        entity.setId(log.id() == 0L ? idGenerator.nextId() : log.id());
        entity.setTenantId(log.tenantId());
        entity.setContractId(log.contractId());
        entity.setClauseNo(log.clauseNo());
        entity.setPurpose(log.purpose().name());
        entity.setModelName(log.modelName());
        entity.setModelVersion(log.modelVersion());
        entity.setPromptVersion(log.promptVersion());
        entity.setPromptTokens(log.promptTokens());
        entity.setCompletionTokens(log.completionTokens());
        entity.setTotalTokens(log.totalTokens());
        entity.setLatencyMillis(Math.toIntExact(Math.min(Integer.MAX_VALUE, log.latencyMillis())));
        entity.setDegraded(log.degraded() ? 1 : 0);
        entity.setDegradeReason(log.degradeReason());
        entity.setTraceId(log.traceId());
        entity.setCreatedAt(log.createdAt());
        mapper.insert(entity);
    }
}
