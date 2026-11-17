package com.crag.agent.rule.infrastructure.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.agent.rule.application.RuleExecutionException;
import com.crag.agent.rule.domain.ExpressionGuard;
import com.crag.agent.rule.domain.RuleContext;
import com.crag.common.domain.BizException;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 表达式沙箱测试（规范 8.2）：白名单变量、内置函数、反射与 new 的拒绝路径。
 */
class AviatorRuleExpressionEvaluatorTest {

    private final AviatorRuleExpressionEvaluator evaluator = new AviatorRuleExpressionEvaluator(1000L);

    private static RuleContext clause(String text, String type, int days, BigDecimal amount) {
        return new RuleContext(text, type, amount, days, "上海市", false, Set.of(type));
    }

    @Test
    @DisplayName("数值比较：账期阈值按变量求值")
    void numericComparison() {
        RuleContext longTerm = clause("乙方应在收到发票后 120 日内付款。", "PAYMENT", 120, BigDecimal.ONE);
        assertThat(evaluator.matches("paymentDays > 90", longTerm)).isTrue();
        assertThat(evaluator.matches("paymentDays > 90", clause("60 日内付款", "PAYMENT", 60, BigDecimal.ONE)))
                .isFalse();
    }

    @Test
    @DisplayName("字符串函数：string.contains 可用")
    void stringContains() {
        RuleContext context = clause("甲方应在收到发票后 75 日内支付。", "PAYMENT", 75, BigDecimal.ONE);
        assertThat(evaluator.matches("string.contains(clauseText, \"收到发票\")", context)).isTrue();
        assertThat(evaluator.matches("string.contains(clauseText, \"预付款\")", context)).isFalse();
    }

    @Test
    @DisplayName("集合函数：include 可用于合同级 presentTypes")
    void seqInclude() {
        RuleContext context = RuleContext.forContract(Set.of("PAYMENT", "BREACH"));
        assertThat(evaluator.matches("!include(presentTypes, \"CONFIDENTIALITY\")", context)).isTrue();
        assertThat(evaluator.matches("!include(presentTypes, \"PAYMENT\")", context)).isFalse();
    }

    @Test
    @DisplayName("缺失值：账期缺失按 0，不触发阈值规则")
    void nullPaymentDaysDoesNotTrigger() {
        RuleContext context = new RuleContext("条款", "PAYMENT", null, null, null, null, Set.of());
        assertThat(evaluator.matches("paymentDays > 90", context)).isFalse();
    }

    @Test
    @DisplayName("反射链被标识符白名单拒绝")
    void reflectionRejected() {
        RuleContext context = clause("条款", "PAYMENT", 10, BigDecimal.ONE);
        assertThatThrownBy(() -> evaluator.matches("\"\".getClass().forName(\"java.lang.Runtime\")", context))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("new 与静态方法被引擎特性关闭")
    void instantiationRejected() {
        RuleContext context = clause("条款", "PAYMENT", 10, BigDecimal.ONE);
        assertThatThrownBy(() -> evaluator.matches("new java.io.File(\"/tmp/x\")", context))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> evaluator.matches("java.lang.Runtime.getRuntime()", context))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("非法标点与超长表达式被拒绝")
    void syntaxRejected() {
        assertThat(ExpressionGuard.findViolation("paymentDays > 90; drop table rule_config")).isNotNull();
        assertThat(ExpressionGuard.findViolation("paymentDays > " + "9".repeat(600))).isNotNull();
        assertThat(ExpressionGuard.findViolation("paymentDays > 90")).isNull();
    }

    @Test
    @DisplayName("求值超时按配置生效（规范 8.2 要求 1 秒）")
    void timeoutConfigured() {
        assertThat(new AviatorRuleExpressionEvaluator(1000L).timeoutMillis()).isEqualTo(1000L);
    }
}
