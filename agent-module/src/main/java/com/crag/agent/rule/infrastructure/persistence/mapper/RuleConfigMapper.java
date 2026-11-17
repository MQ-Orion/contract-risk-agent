package com.crag.agent.rule.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.agent.rule.infrastructure.persistence.entity.RuleConfigEntity;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/**
 * 规则配置 Mapper（表 rule_config，归属 agent-module）。
 *
 * <p>普通增删改查走 MyBatis-Plus，并由租户拦截器注入 {@code tenant_id}（规范 4.4）。
 * 唯一例外是 {@link #selectAllForRefresh()}：缓存刷新任务需要跨租户加载全量规则，因此关闭自动注入并显式声明
 * “无租户条件”的用途，登记在 ADR 0005。该查询只返回配置数据，不返回任何业务事实（规范 4.4 允许的运维/基础组件场景）。</p>
 */
public interface RuleConfigMapper extends BaseMapper<RuleConfigEntity> {

    /**
     * 加载全部未删除规则，供缓存刷新使用（跨租户）。
     *
     * @return 规则实体
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, rule_code, rule_name, clause_type, scope, condition_expr, risk_level, "
            + "suggestion_template, enabled, version_no, positive_example, negative_example, owner_name, "
            + "created_at, updated_at, created_by, deleted "
            + "FROM rule_config WHERE deleted = 0")
    List<RuleConfigEntity> selectAllForRefresh();
}
