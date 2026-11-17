package com.crag.agent.rule.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * 规则回放用例（表 rule_config 的 positive_example / negative_example，规范 8.3）。
 *
 * <p>为什么用例要带完整上下文：只存一段条款文本，无法复现“账期 120 天”“含连带责任”这类求值条件，
 * 回放就退化成人工阅读。这里把 7 个变量与期望结果一起落库，回放用例可以直接断言。</p>
 *
 * @param clauseText       条款文本
 * @param clauseType       条款类型
 * @param contractAmount   合同金额
 * @param paymentDays      账期天数
 * @param jurisdiction     管辖地
 * @param hasJointLiability 是否含连带责任
 * @param presentTypes     条款类型集合
 * @param expected         期望是否命中
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleExample(String clauseText,
                          String clauseType,
                          BigDecimal contractAmount,
                          Integer paymentDays,
                          String jurisdiction,
                          Boolean hasJointLiability,
                          List<String> presentTypes,
                          Boolean expected) {

    /**
     * 转为求值上下文。
     *
     * @return 上下文
     */
    public RuleContext toContext() {
        return new RuleContext(clauseText, clauseType, contractAmount, paymentDays, jurisdiction,
                hasJointLiability, presentTypes == null ? java.util.Set.of() : java.util.Set.copyOf(presentTypes));
    }

    /**
     * 期望是否命中；缺失视为 false，由调用方决定是否视为校验失败。
     *
     * @return 期望值
     */
    public boolean expectedValue() {
        return Boolean.TRUE.equals(expected);
    }
}
