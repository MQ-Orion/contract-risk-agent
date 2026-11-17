package com.crag.agent.rule.application;

import com.crag.agent.rule.domain.RuleContext;

/**
 * 规则表达式求值端口（规范 8.2）。
 *
 * <p>实现必须是确定性的：同一表达式与同一上下文在任何机器上得到同一结果。禁止在求值中访问 IO、数据库或模型。</p>
 */
public interface RuleExpressionEvaluator {

    /**
     * 求值表达式。
     *
     * @param expression 表达式
     * @param context    上下文
     * @return 命中返回 true
     * @throws RuleExecutionException 表达式非法或求值超时
     */
    boolean matches(String expression, RuleContext context);

    /**
     * 试算：编译并执行一次，用于保存前校验（规范 8.2“保存前强制试算一遍”）。
     *
     * @param expression 表达式
     * @param context    试算上下文
     * @throws RuleExecutionException 表达式非法或求值失败
     */
    void validate(String expression, RuleContext context);
}
