package com.crag.agent.rule.support;

import com.crag.agent.rule.application.port.RuleChangeLogRepository;
import com.crag.agent.rule.domain.RuleChangeLogEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则变更日志内存实现，仅用于单元测试。
 */
public class FakeRuleChangeLogRepository implements RuleChangeLogRepository {

    private final List<RuleChangeLogEntry> entries = new ArrayList<>();

    @Override
    public void append(RuleChangeLogEntry entry) {
        entries.add(entry);
    }

    /**
     * 已记录的日志。
     *
     * @return 日志列表
     */
    public List<RuleChangeLogEntry> entries() {
        return List.copyOf(entries);
    }
}
