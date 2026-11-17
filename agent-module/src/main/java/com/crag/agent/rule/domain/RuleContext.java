package com.crag.agent.rule.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 规则求值上下文（规范 8.2 的变量白名单）。
 *
 * <p>变量固定为：合同金额、账期天数、管辖地、是否含连带责任、条款文本、条款类型；
 * 合同级规则额外可读 {@code presentTypes}（本次切分得到的条款类型集合，负责人 2026-11-14 批准）。</p>
 *
 * <p>缺失值语义：数值缺失按 0、布尔缺失按 false、文本缺失按空串。这样“未知”不会误触发阈值规则
 * （阈值命中会产出风险项，宁可漏报待人工复核，也不要用默认值编造结论）。</p>
 *
 * @param clauseText       条款文本
 * @param clauseType       条款类型 code
 * @param contractAmount   合同金额（元）
 * @param paymentDays      账期天数
 * @param jurisdiction     管辖地
 * @param hasJointLiability 是否含连带责任
 * @param presentTypes     全篇出现的条款类型集合，仅合同级规则使用
 */
public record RuleContext(String clauseText,
                          String clauseType,
                          BigDecimal contractAmount,
                          Integer paymentDays,
                          String jurisdiction,
                          Boolean hasJointLiability,
                          Set<String> presentTypes) {

    /**
     * 构造上下文，集合做不可变复制。
     */
    public RuleContext {
        presentTypes = presentTypes == null ? Set.of() : Set.copyOf(presentTypes);
    }

    /**
     * 单条款求值上下文。
     *
     * @param clauseText   条款文本
     * @param clauseType   条款类型
     * @param presentTypes 全篇条款类型集合
     * @return 上下文
     */
    public static RuleContext forClause(String clauseText, String clauseType, Set<String> presentTypes) {
        return new RuleContext(clauseText, clauseType, null, null, null, null, presentTypes);
    }

    /**
     * 合同级求值上下文。
     *
     * @param presentTypes 全篇条款类型集合
     * @return 上下文
     */
    public static RuleContext forContract(Set<String> presentTypes) {
        return new RuleContext("", "CONTRACT", null, null, null, null, presentTypes);
    }

    /**
     * 附加抽取字段（金额、账期、管辖地、连带责任）。
     *
     * @param amount       合同金额
     * @param days         账期天数
     * @param place        管辖地
     * @param jointLiability 是否含连带责任
     * @return 新上下文
     */
    public RuleContext withExtracted(BigDecimal amount, Integer days, String place, Boolean jointLiability) {
        return new RuleContext(clauseText, clauseType, amount, days, place, jointLiability, presentTypes);
    }
}
