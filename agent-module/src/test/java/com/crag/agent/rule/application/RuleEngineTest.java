package com.crag.agent.rule.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.domain.RiskLevelEnum;
import com.crag.agent.rule.domain.RuleContext;
import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.agent.rule.domain.RuleScopeEnum;
import com.crag.agent.rule.support.StubRuleExpressionEvaluator;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规则引擎测试：作用域隔离、等级排序、失败不阻断。
 */
class RuleEngineTest {

    private static RuleDefinition rule(long id, String code, String clauseType, RuleScopeEnum scope,
                                       String expr, RiskLevelEnum level, boolean enabled) {
        return new RuleDefinition(id, 1L, code, code, clauseType, scope, expr, level, "建议", enabled, 1,
                "{}", "{}", "tester", Instant.parse("2026-11-14T00:00:00Z"));
    }

    private static RuleEngine engine(StubRuleExpressionEvaluator evaluator, RuleDefinition... rules) {
        RuleCache cache = new RuleCache();
        cache.replace(List.of(rules));
        return new RuleEngine(cache, evaluator);
    }

    @Test
    @DisplayName("只求值同租户、同条款类型、已启用的单条款规则")
    void filtersByTenantTypeAndEnabled() {
        StubRuleExpressionEvaluator evaluator = new StubRuleExpressionEvaluator()
                .when("paymentDays > 90", true);
        RuleEngine engine = engine(evaluator,
                rule(1, "PAYMENT.A.01", "PAYMENT", RuleScopeEnum.CLAUSE, "paymentDays > 90", RiskLevelEnum.HIGH, true),
                rule(2, "PAYMENT.B.02", "PAYMENT", RuleScopeEnum.CLAUSE, "paymentDays > 90", RiskLevelEnum.LOW, false),
                rule(3, "BREACH.C.03", "BREACH", RuleScopeEnum.CLAUSE, "paymentDays > 90", RiskLevelEnum.MEDIUM, true),
                rule(4, "CONTRACT.D.04", "CONTRACT", RuleScopeEnum.CONTRACT, "paymentDays > 90",
                        RiskLevelEnum.HIGH, true));

        RuleEvaluationResult result = engine.evaluateClause(1L, RuleContext.forClause("条款", "PAYMENT", Set.of()));

        assertThat(result.matches()).extracting("ruleCode").containsExactly("PAYMENT.A.01");
        assertThat(result.degraded()).isFalse();
    }

    @Test
    @DisplayName("命中结果按风险等级排序，同级按编码排序")
    void sortsBySeverityThenCode() {
        StubRuleExpressionEvaluator evaluator = new StubRuleExpressionEvaluator()
                .when("x", true);
        RuleEngine engine = engine(evaluator,
                rule(1, "PAYMENT.Z.01", "PAYMENT", RuleScopeEnum.CLAUSE, "x", RiskLevelEnum.LOW, true),
                rule(2, "PAYMENT.A.02", "PAYMENT", RuleScopeEnum.CLAUSE, "x", RiskLevelEnum.HIGH, true),
                rule(3, "PAYMENT.B.03", "PAYMENT", RuleScopeEnum.CLAUSE, "x", RiskLevelEnum.HIGH, true));

        List<String> order = engine.evaluateClause(1L, RuleContext.forClause("条款", "PAYMENT", Set.of()))
                .matches().stream().map(match -> match.ruleCode()).toList();

        assertThat(order).containsExactly("PAYMENT.A.02", "PAYMENT.B.03", "PAYMENT.Z.01");
    }

    @Test
    @DisplayName("单条规则失败只降级该规则，其余规则继续求值")
    void ruleFailureDoesNotAbortOthers() {
        StubRuleExpressionEvaluator evaluator = new StubRuleExpressionEvaluator()
                .failing("bad")
                .when("good", true);
        RuleEngine engine = engine(evaluator,
                rule(1, "PAYMENT.A.01", "PAYMENT", RuleScopeEnum.CLAUSE, "bad", RiskLevelEnum.HIGH, true),
                rule(2, "PAYMENT.B.02", "PAYMENT", RuleScopeEnum.CLAUSE, "good", RiskLevelEnum.MEDIUM, true));

        RuleEvaluationResult result = engine.evaluateClause(1L, RuleContext.forClause("条款", "PAYMENT", Set.of()));

        assertThat(result.matches()).extracting("ruleCode").containsExactly("PAYMENT.B.02");
        assertThat(result.failedRuleCodes()).containsExactly("PAYMENT.A.01");
        assertThat(result.degraded()).isTrue();
    }

    @Test
    @DisplayName("合同级规则读取 presentTypes 判定必备条款缺失")
    void evaluatesContractScopeRules() {
        StubRuleExpressionEvaluator evaluator = new StubRuleExpressionEvaluator()
                .when("!include(presentTypes, \"CONFIDENTIALITY\")", true);
        RuleEngine engine = engine(evaluator,
                rule(1, "CONTRACT.MISSING.01", "CONTRACT", RuleScopeEnum.CONTRACT,
                        "!include(presentTypes, \"CONFIDENTIALITY\")", RiskLevelEnum.MEDIUM, true));

        RuleEvaluationResult result = engine.evaluateContract(1L, Set.of("PAYMENT", "BREACH"));

        assertThat(result.matches()).extracting("ruleCode").containsExactly("CONTRACT.MISSING.01");
    }
}
