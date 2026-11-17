package com.crag.agent.rule.application.port;

import com.crag.agent.rule.domain.RuleDefinition;
import java.util.List;
import java.util.Optional;

/**
 * 规则仓储端口（表 rule_config，归属 agent-module）。
 */
public interface RuleRepository {

    /**
     * 加载全部未删除规则，供缓存刷新使用。
     *
     * <p>跨租户查询：缓存刷新任务没有请求上下文，必须能读到所有租户的规则（规范 8.3 灰度按租户生效）。</p>
     *
     * @return 规则列表
     */
    List<RuleDefinition> findAllForRefresh();

    /**
     * 查询当前租户的规则。
     *
     * @param tenantId        租户
     * @param includeDisabled 是否包含未启用
     * @return 规则列表
     */
    List<RuleDefinition> findByTenant(long tenantId, boolean includeDisabled);

    /**
     * 按标识查询。
     *
     * @param tenantId 租户
     * @param ruleId   规则标识
     * @return 规则
     */
    Optional<RuleDefinition> findById(long tenantId, long ruleId);

    /**
     * 按编码查询。
     *
     * @param tenantId 租户
     * @param ruleCode 规则编码
     * @return 规则
     */
    Optional<RuleDefinition> findByCode(long tenantId, String ruleCode);

    /**
     * 插入规则。
     *
     * @param rule 规则
     * @return 插入后的规则
     */
    RuleDefinition insert(RuleDefinition rule);

    /**
     * 更新规则。
     *
     * @param rule 规则
     * @return 更新后的规则
     */
    RuleDefinition update(RuleDefinition rule);
}
