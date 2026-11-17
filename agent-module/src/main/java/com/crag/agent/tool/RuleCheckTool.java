package com.crag.agent.tool;

import com.crag.agent.rule.application.RuleEvaluationResult;
import com.crag.agent.rule.domain.RuleContext;

/**
 * 规则校验工具（规范 7.1：rule_check_tool，超时 2 秒，幂等）。
 */
public interface RuleCheckTool extends AgentTool {

    /**
     * 对单条款执行规则校验。
     *
     * @param tenantId 租户
     * @param context  条款上下文
     * @return 结果；工具内部异常转为失败结果
     */
    ToolOutcome<RuleEvaluationResult> checkClause(long tenantId, RuleContext context);

    /**
     * 对整份合同执行必备条款校验。
     *
     * @param tenantId     租户
     * @param presentTypes 条款类型集合
     * @return 结果
     */
    ToolOutcome<RuleEvaluationResult> checkContract(long tenantId, java.util.Set<String> presentTypes);
}
