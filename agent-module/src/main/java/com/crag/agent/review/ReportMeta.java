package com.crag.agent.review;

import java.time.Instant;

/**
 * 报告元信息（规范 9.3：导出报告必须包含生成时间、模型版本、Prompt 版本、检索降级标记、评估状态）。
 *
 * @param contractId            合同标识
 * @param contractName          合同名称
 * @param generatedAt           生成时间
 * @param modelVersion          模型版本
 * @param promptVersion         Prompt 版本
 * @param ruleCacheVersion      规则缓存版本
 * @param retrievalDegraded     检索是否降级
 * @param modelDegraded         模型是否降级
 * @param extractionStatus      抽取状态
 * @param extractionVerifiedRate 抽取回验通过率
 * @param evaluationStatus      评估状态描述（未实测的指标不得给数字，规范 12.6）
 * @param clauseCount           条款数
 */
public record ReportMeta(long contractId,
                         String contractName,
                         Instant generatedAt,
                         String modelVersion,
                         String promptVersion,
                         long ruleCacheVersion,
                         boolean retrievalDegraded,
                         boolean modelDegraded,
                         String extractionStatus,
                         double extractionVerifiedRate,
                         String evaluationStatus,
                         int clauseCount) {
}
