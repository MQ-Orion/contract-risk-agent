package com.crag.agent.rule.infrastructure.expression;

import com.crag.agent.rule.application.RuleExecutionException;
import com.crag.agent.rule.application.RuleExpressionEvaluator;
import com.crag.agent.rule.domain.ExpressionGuard;
import com.crag.agent.rule.domain.RuleContext;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aviator 实现的规则求值器（规范 8.2）。
 *
 * <p>安全策略是两层：</p>
 * <ol>
 *   <li>引擎层：关闭全部可选特性（赋值、循环、lambda、模块、{@code new}、静态字段/方法），
 *       类白名单置空，求值超时 1 秒，防止长表达式或死循环拖垮审核线程；</li>
 *   <li>语法层：{@link ExpressionGuard} 的标识符白名单，拒绝 {@code getClass}、{@code forName}、
 *       {@code exec} 等不依赖特性即可调用的反射链。</li>
 * </ol>
 *
 * <p>变量注入：只放规范 8.2 的 6 个变量与合同级 {@code presentTypes}，不注入任何 Spring Bean、Mapper 或类加载器。
 * 缺失值按 {@link RuleContext} 的约定填充（数值 0、布尔 false、文本空串），避免 null 参与比较时抛异常。</p>
 *
 * <p>线程安全性：{@link AviatorEvaluatorInstance} 的表达式缓存内部有并发保护，可在多线程共享。</p>
 */
public class AviatorRuleExpressionEvaluator implements RuleExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AviatorRuleExpressionEvaluator.class);

    private final AviatorEvaluatorInstance instance;
    private final long timeoutMillis;

    /**
     * 构造求值器。
     *
     * @param timeoutMillis 单次求值超时（毫秒），规范 8.2 要求 1 秒
     */
    public AviatorRuleExpressionEvaluator(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        this.instance = AviatorEvaluator.newInstance();
        // 空特性集合：赋值、分支、循环、lambda、模块、new、静态方法全部不可用，只剩表达式与内置函数
        this.instance.setOption(Options.FEATURE_SET, Feature.asSet());
        // 类白名单为空：即使特性开关被误改，也无法通过 new 构造任意类
        this.instance.setOption(Options.ALLOWED_CLASS_SET, Collections.emptySet());
        this.instance.setOption(Options.EVAL_TIMEOUT_MS, timeoutMillis);
    }

    @Override
    public boolean matches(String expression, RuleContext context) {
        Object result = evaluate(expression, context);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void validate(String expression, RuleContext context) {
        evaluate(expression, context);
    }

    /**
     * 配置的求值超时，供测试与运维核对（规范 8.2 要求 1 秒）。
     *
     * @return 超时毫秒
     */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    private Object evaluate(String expression, RuleContext context) {
        ExpressionGuard.check(expression);
        Map<String, Object> environment = buildEnvironment(context);
        try {
            return instance.execute(expression, environment, true);
        } catch (RuntimeException e) {
            // 不把原始表达式写进异常消息的对外部分，但日志需要它来定位规则问题
            log.warn("规则求值失败 expression={} reason={}", expression, e.getMessage());
            throw new RuleExecutionException("expression evaluation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 构造变量环境；只包含白名单变量。
     *
     * @param context 上下文
     * @return 变量表
     */
    static Map<String, Object> buildEnvironment(RuleContext context) {
        Map<String, Object> environment = new HashMap<>(8);
        environment.put("contractAmount",
                context.contractAmount() == null ? BigDecimal.ZERO : context.contractAmount());
        environment.put("paymentDays", context.paymentDays() == null ? 0 : context.paymentDays());
        environment.put("jurisdiction", context.jurisdiction() == null ? "" : context.jurisdiction());
        environment.put("hasJointLiability", Boolean.TRUE.equals(context.hasJointLiability()));
        environment.put("clauseText", context.clauseText() == null ? "" : context.clauseText());
        environment.put("clauseType", context.clauseType() == null ? "" : context.clauseType());
        environment.put("presentTypes", context.presentTypes());
        return environment;
    }
}
