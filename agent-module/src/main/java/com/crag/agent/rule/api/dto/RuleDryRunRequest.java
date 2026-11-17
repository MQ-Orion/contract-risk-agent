package com.crag.agent.rule.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则试算请求：只传规范 8.2 的变量，不传任何内部对象。
 *
 * @param clauseText        条款文本
 * @param clauseType        条款类型
 * @param contractAmount    合同金额
 * @param paymentDays       账期天数
 * @param jurisdiction      管辖地
 * @param hasJointLiability 是否含连带责任
 * @param presentTypes      条款类型集合（合同级规则使用）
 */
public record RuleDryRunRequest(String clauseText,
                                String clauseType,
                                BigDecimal contractAmount,
                                Integer paymentDays,
                                String jurisdiction,
                                Boolean hasJointLiability,
                                List<String> presentTypes) {
}
