package com.crag.audit.application.port;

import com.crag.common.domain.RiskLevelEnum;

/**
 * 风险输入（agent → audit 的数据契约）。
 *
 * <p>刻意与 agent 内部的 {@code RiskItem} 解耦：audit 只认识自己的契约，agent 变更不会传导到审核表结构。</p>
 *
 * @param riskLevel        风险等级
 * @param riskContent      风险说明
 * @param suggestion       建议
 * @param source           来源：RULE / MODEL / RULE_CONFLICT
 * @param ruleCode         规则编码
 * @param ruleVersion      规则版本
 * @param sourceDocName    来源文档名
 * @param sourceChunkKey   来源切片定位
 * @param sourceExcerpt    引用原文
 * @param citationVerified 引用是否回验通过
 * @param clauseNo         条款序号
 * @param clauseType       条款类型
 * @param clauseText       条款原文
 * @param retrievalSnapshot 召回片段快照 JSON
 */
public record AuditRiskInput(RiskLevelEnum riskLevel,
                             String riskContent,
                             String suggestion,
                             String source,
                             String ruleCode,
                             Integer ruleVersion,
                             String sourceDocName,
                             String sourceChunkKey,
                             String sourceExcerpt,
                             boolean citationVerified,
                             String clauseNo,
                             String clauseType,
                             String clauseText,
                             String retrievalSnapshot) {
}
