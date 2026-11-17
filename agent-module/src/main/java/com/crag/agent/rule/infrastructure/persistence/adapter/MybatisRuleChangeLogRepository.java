package com.crag.agent.rule.infrastructure.persistence.adapter;

import com.crag.agent.rule.application.port.RuleChangeLogRepository;
import com.crag.agent.rule.domain.RuleChangeLogEntry;
import com.crag.agent.rule.infrastructure.persistence.entity.RuleChangeLogEntity;
import com.crag.agent.rule.infrastructure.persistence.mapper.RuleChangeLogMapper;
import org.springframework.stereotype.Repository;

/**
 * 规则变更日志仓储实现：只插入，不提供更新与删除（规范 8.3、11.6）。
 */
@Repository
public class MybatisRuleChangeLogRepository implements RuleChangeLogRepository {

    private final RuleChangeLogMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 变更日志 Mapper
     */
    public MybatisRuleChangeLogRepository(RuleChangeLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(RuleChangeLogEntry entry) {
        RuleChangeLogEntity entity = new RuleChangeLogEntity();
        entity.setId(entry.id());
        entity.setTenantId(entry.tenantId());
        entity.setRuleId(entry.ruleId());
        entity.setRuleCode(entry.ruleCode());
        entity.setAction(entry.action().name());
        entity.setVersionBefore(entry.versionBefore());
        entity.setVersionAfter(entry.versionAfter());
        entity.setBeforeJson(entry.beforeJson());
        entity.setAfterJson(entry.afterJson());
        entity.setOperatorId(entry.operatorId());
        entity.setOperatorName(entry.operatorName());
        entity.setCreatedAt(entry.createdAt());
        mapper.insertWithJsonCast(entity);
    }
}
