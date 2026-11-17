package com.crag.agent.rule.support;

import com.crag.agent.rule.application.port.RuleRepository;
import com.crag.agent.rule.domain.RuleDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 规则仓储内存实现，仅用于单元测试。
 */
public class FakeRuleRepository implements RuleRepository {

    private final Map<Long, RuleDefinition> storage = new LinkedHashMap<>();

    /**
     * 预置规则。
     *
     * @param rule 规则
     * @return 自身
     */
    public FakeRuleRepository with(RuleDefinition rule) {
        storage.put(rule.id(), rule);
        return this;
    }

    /**
     * 当前全部规则。
     *
     * @return 规则列表
     */
    public List<RuleDefinition> all() {
        return List.copyOf(storage.values());
    }

    @Override
    public List<RuleDefinition> findAllForRefresh() {
        return all();
    }

    @Override
    public List<RuleDefinition> findByTenant(long tenantId, boolean includeDisabled) {
        List<RuleDefinition> result = new ArrayList<>();
        for (RuleDefinition rule : storage.values()) {
            if (rule.tenantId() != tenantId) {
                continue;
            }
            if (!includeDisabled && !rule.enabled()) {
                continue;
            }
            result.add(rule);
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<RuleDefinition> findById(long tenantId, long ruleId) {
        RuleDefinition rule = storage.get(ruleId);
        return rule != null && rule.tenantId() == tenantId ? Optional.of(rule) : Optional.empty();
    }

    @Override
    public Optional<RuleDefinition> findByCode(long tenantId, String ruleCode) {
        return storage.values().stream()
                .filter(rule -> rule.tenantId() == tenantId && rule.ruleCode().equals(ruleCode))
                .findFirst();
    }

    @Override
    public RuleDefinition insert(RuleDefinition rule) {
        storage.put(rule.id(), rule);
        return rule;
    }

    @Override
    public RuleDefinition update(RuleDefinition rule) {
        storage.put(rule.id(), rule);
        return rule;
    }
}
