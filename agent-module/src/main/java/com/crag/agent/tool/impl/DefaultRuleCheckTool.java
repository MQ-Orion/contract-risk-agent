package com.crag.agent.tool.impl;

import com.crag.agent.rule.application.RuleEngine;
import com.crag.agent.rule.application.RuleEvaluationResult;
import com.crag.agent.rule.domain.RuleContext;
import com.crag.agent.tool.RuleCheckTool;
import com.crag.agent.tool.ToolDescriptor;
import com.crag.agent.tool.ToolOutcome;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则校验工具实现（规范 7.1：超时 2 秒、幂等）。
 *
 * <p>只调用 {@link RuleEngine}，不访问数据库、不调用模型；异常转为结构化失败，不把裸异常抛给编排层。</p>
 */
public class DefaultRuleCheckTool implements RuleCheckTool {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuleCheckTool.class);

    private static final ToolDescriptor DESCRIPTOR =
            new ToolDescriptor("rule_check_tool", 2_000L, true, "确定性规则校验，输出风险项（含规则编码）");

    private final RuleEngine ruleEngine;

    /**
     * 构造工具。
     *
     * @param ruleEngine 规则引擎
     */
    public DefaultRuleCheckTool(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolOutcome<RuleEvaluationResult> checkClause(long tenantId, RuleContext context) {
        try {
            return ToolOutcome.ok(ruleEngine.evaluateClause(tenantId, context));
        } catch (RuntimeException e) {
            log.error("规则校验工具失败 tenantId={} clauseType={}", tenantId, context.clauseType(), e);
            return ToolOutcome.failure("40002", "rule_check_failed");
        }
    }

    @Override
    public ToolOutcome<RuleEvaluationResult> checkContract(long tenantId, Set<String> presentTypes) {
        try {
            return ToolOutcome.ok(ruleEngine.evaluateContract(tenantId, presentTypes));
        } catch (RuntimeException e) {
            log.error("合同级规则校验失败 tenantId={}", tenantId, e);
            return ToolOutcome.failure("40002", "rule_check_failed");
        }
    }
}
