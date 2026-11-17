package com.crag.agent.rule.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.agent.rule.infrastructure.persistence.entity.RuleChangeLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 规则变更日志 Mapper（表 rule_change_log）：只追加，不提供更新与删除方法。
 *
 * <p>为什么插入要手写 SQL 而不是用 {@code BaseMapper.insert}：{@code before_json}/{@code after_json} 是 jsonb 列，
 * JDBC 以 varchar 绑定会被 PostgreSQL 拒绝（{@code column is of type jsonb but expression is of type character varying}）。
 * 这里显式 {@code CAST(... AS jsonb)}，避免为一个日志表引入 PGobject 类型处理器。</p>
 */
public interface RuleChangeLogMapper extends BaseMapper<RuleChangeLogEntity> {

    /**
     * 追加变更日志（jsonb 列显式转换）。
     *
     * @param entity 日志实体
     * @return 写入行数
     */
    @Insert("INSERT INTO rule_change_log (id, tenant_id, rule_id, rule_code, action, version_before, version_after, "
            + "before_json, after_json, operator_id, operator_name, created_at) VALUES ("
            + "#{e.id}, #{e.tenantId}, #{e.ruleId}, #{e.ruleCode}, #{e.action}, #{e.versionBefore}, "
            + "#{e.versionAfter}, CAST(#{e.beforeJson} AS jsonb), CAST(#{e.afterJson} AS jsonb), "
            + "#{e.operatorId}, #{e.operatorName}, #{e.createdAt})")
    int insertWithJsonCast(@Param("e") RuleChangeLogEntity entity);
}
