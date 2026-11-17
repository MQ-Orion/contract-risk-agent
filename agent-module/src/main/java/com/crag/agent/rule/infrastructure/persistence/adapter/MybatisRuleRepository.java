package com.crag.agent.rule.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.agent.rule.application.port.RuleRepository;
import com.crag.common.domain.RiskLevelEnum;
import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.agent.rule.domain.RuleScopeEnum;
import com.crag.agent.rule.infrastructure.persistence.entity.RuleConfigEntity;
import com.crag.agent.rule.infrastructure.persistence.mapper.RuleConfigMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 规则仓储的 MyBatis-Plus 实现。
 *
 * <p>租户条件由拦截器注入（规范 4.4）；本类只在“缓存刷新”一条路径上走跨租户查询，
 * 且该路径不使用请求上下文。</p>
 */
@Repository
public class MybatisRuleRepository implements RuleRepository {

    private final RuleConfigMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 规则 Mapper
     */
    public MybatisRuleRepository(RuleConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RuleDefinition> findAllForRefresh() {
        return toDefinitions(mapper.selectAllForRefresh());
    }

    @Override
    public List<RuleDefinition> findByTenant(long tenantId, boolean includeDisabled) {
        var query = Wrappers.<RuleConfigEntity>lambdaQuery()
                .eq(RuleConfigEntity::getTenantId, tenantId);
        if (!includeDisabled) {
            query.eq(RuleConfigEntity::getEnabled, 1);
        }
        query.orderByAsc(RuleConfigEntity::getRuleCode);
        return toDefinitions(mapper.selectList(query));
    }

    @Override
    public Optional<RuleDefinition> findById(long tenantId, long ruleId) {
        RuleConfigEntity entity = mapper.selectOne(Wrappers.<RuleConfigEntity>lambdaQuery()
                .eq(RuleConfigEntity::getTenantId, tenantId)
                .eq(RuleConfigEntity::getId, ruleId));
        return Optional.ofNullable(toDefinition(entity));
    }

    @Override
    public Optional<RuleDefinition> findByCode(long tenantId, String ruleCode) {
        RuleConfigEntity entity = mapper.selectOne(Wrappers.<RuleConfigEntity>lambdaQuery()
                .eq(RuleConfigEntity::getTenantId, tenantId)
                .eq(RuleConfigEntity::getRuleCode, ruleCode));
        return Optional.ofNullable(toDefinition(entity));
    }

    @Override
    public RuleDefinition insert(RuleDefinition rule) {
        RuleConfigEntity entity = toEntity(rule);
        entity.setDeleted(0);
        mapper.insert(entity);
        return rule;
    }

    @Override
    public RuleDefinition update(RuleDefinition rule) {
        RuleConfigEntity entity = toEntity(rule);
        mapper.updateById(entity);
        return rule;
    }

    private static List<RuleDefinition> toDefinitions(List<RuleConfigEntity> entities) {
        List<RuleDefinition> rules = new ArrayList<>(entities.size());
        for (RuleConfigEntity entity : entities) {
            rules.add(toDefinition(entity));
        }
        return List.copyOf(rules);
    }

    private static RuleDefinition toDefinition(RuleConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RuleDefinition(
                entity.getId(),
                entity.getTenantId(),
                entity.getRuleCode(),
                entity.getRuleName(),
                entity.getClauseType(),
                RuleScopeEnum.fromCode(entity.getScope()),
                entity.getConditionExpr(),
                RiskLevelEnum.fromCode(entity.getRiskLevel()),
                entity.getSuggestionTemplate(),
                entity.getEnabled() != null && entity.getEnabled() == 1,
                entity.getVersionNo() == null ? 1 : entity.getVersionNo(),
                entity.getPositiveExample(),
                entity.getNegativeExample(),
                entity.getOwnerName(),
                entity.getUpdatedAt());
    }

    private static RuleConfigEntity toEntity(RuleDefinition rule) {
        RuleConfigEntity entity = new RuleConfigEntity();
        entity.setId(rule.id());
        entity.setTenantId(rule.tenantId());
        entity.setRuleCode(rule.ruleCode());
        entity.setRuleName(rule.ruleName());
        entity.setClauseType(rule.clauseType());
        entity.setScope(rule.scope().name());
        entity.setConditionExpr(rule.conditionExpr());
        entity.setRiskLevel(rule.riskLevel().name());
        entity.setSuggestionTemplate(rule.suggestionTemplate());
        entity.setEnabled(rule.enabled() ? 1 : 0);
        entity.setVersionNo(rule.versionNo());
        entity.setPositiveExample(rule.positiveExample());
        entity.setNegativeExample(rule.negativeExample());
        entity.setOwnerName(rule.ownerName());
        entity.setUpdatedAt(rule.updatedAt());
        return entity;
    }
}
