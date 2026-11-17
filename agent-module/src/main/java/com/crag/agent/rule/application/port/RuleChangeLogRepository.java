package com.crag.agent.rule.application.port;

import com.crag.agent.rule.domain.RuleChangeLogEntry;

/**
 * 规则变更日志仓储端口（表 rule_change_log）：只追加。
 */
public interface RuleChangeLogRepository {

    /**
     * 追加一条变更日志。
     *
     * @param entry 日志条目
     */
    void append(RuleChangeLogEntry entry);
}
