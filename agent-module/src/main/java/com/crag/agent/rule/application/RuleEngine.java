package com.crag.agent.rule.application;

import com.crag.agent.rule.domain.RuleContext;
import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.agent.rule.domain.RuleMatch;
import com.crag.agent.rule.domain.RuleScopeEnum;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则引擎：按缓存中的启用规则对条款或整份合同求值（规范 8.1、8.2、8.4）。
 *
 * <p>调用链：{@code RuleCheckTool.execute} → 本类 {@code evaluateClause/evaluateContract} →
 * {@link RuleCache} 取快照 → {@link RuleExpressionEvaluator} 逐条求值。本类不访问数据库、不调用模型。</p>
 *
 * <p>为什么规则失败不抛给调用方：一条规则写错不应让整份合同无法出结论。失败规则记入
 * {@code failedRuleCodes}，报告标注“部分规则未执行”，同时日志带规则编码便于修正（规范 8.2 的降级要求）。</p>
 *
 * <p>排序：高风险在前、同级按规则编码升序。固定顺序让同一输入的输出可复现，评估回归才有可比性。</p>
 *
 * <p>线程安全性：无可变状态，可共享。</p>
 */
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final RuleCache cache;
    private final RuleExpressionEvaluator evaluator;

    /**
     * 构造引擎。
     *
     * @param cache     规则缓存
     * @param evaluator 表达式求值器
     */
    public RuleEngine(RuleCache cache, RuleExpressionEvaluator evaluator) {
        this.cache = cache;
        this.evaluator = evaluator;
    }

    /**
     * 对单条款求值。
     *
     * @param tenantId 租户
     * @param context  条款上下文
     * @return 求值结果
     */
    public RuleEvaluationResult evaluateClause(long tenantId, RuleContext context) {
        String clauseType = context.clauseType();
        List<RuleDefinition> rules = cache.snapshot().enabledRules(tenantId, RuleScopeEnum.CLAUSE, clauseType);
        return evaluate(rules, context);
    }

    /**
     * 对整份合同求值（必备条款缺失类规则）。
     *
     * @param tenantId     租户
     * @param presentTypes 本次切分得到的条款类型集合
     * @return 求值结果
     */
    public RuleEvaluationResult evaluateContract(long tenantId, Set<String> presentTypes) {
        List<RuleDefinition> rules = cache.snapshot().enabledRules(tenantId, RuleScopeEnum.CONTRACT, null);
        return evaluate(rules, RuleContext.forContract(presentTypes));
    }

    private RuleEvaluationResult evaluate(List<RuleDefinition> rules, RuleContext context) {
        if (rules.isEmpty()) {
            return RuleEvaluationResult.empty();
        }
        List<RuleMatch> matches = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (RuleDefinition rule : rules) {
            try {
                if (evaluator.matches(rule.conditionExpr(), context)) {
                    matches.add(RuleMatch.of(rule));
                }
            } catch (RuleExecutionException e) {
                failed.add(rule.ruleCode());
                log.warn("规则执行失败，已跳过 ruleCode={} version={} reason={}",
                        rule.ruleCode(), rule.versionNo(), e.getMessage());
            }
        }
        matches.sort(Comparator.comparingInt((RuleMatch match) -> match.riskLevel().severity())
                .thenComparing(RuleMatch::ruleCode));
        return new RuleEvaluationResult(matches, failed);
    }
}
