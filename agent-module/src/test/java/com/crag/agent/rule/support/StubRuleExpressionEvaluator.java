package com.crag.agent.rule.support;

import com.crag.agent.rule.application.RuleExpressionEvaluator;
import com.crag.agent.rule.application.RuleExecutionException;
import com.crag.agent.rule.domain.RuleContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 表达式求值替身：按“表达式 → 结果”映射返回，用于不依赖 Aviator 的单元测试。
 */
public class StubRuleExpressionEvaluator implements RuleExpressionEvaluator {

    private final Map<String, Boolean> results = new HashMap<>();
    private final Set<String> failing = new HashSet<>();

    /**
     * 指定表达式结果。
     *
     * @param expression 表达式
     * @param result     结果
     * @return 自身，便于链式配置
     */
    public StubRuleExpressionEvaluator when(String expression, boolean result) {
        results.put(expression, result);
        return this;
    }

    /**
     * 指定表达式求值失败。
     *
     * @param expression 表达式
     * @return 自身
     */
    public StubRuleExpressionEvaluator failing(String expression) {
        failing.add(expression);
        return this;
    }

    @Override
    public boolean matches(String expression, RuleContext context) {
        if (failing.contains(expression)) {
            throw new RuleExecutionException("stub failure");
        }
        return results.getOrDefault(expression, false);
    }

    @Override
    public void validate(String expression, RuleContext context) {
        matches(expression, context);
    }
}
