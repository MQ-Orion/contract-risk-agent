package com.crag.agent.review;

import com.crag.agent.extract.ExtractionResult;

/**
 * 初审预演结果（第 5–7 周无审核任务表，故称“预演”；第 8–9 周由审核任务承载并落库）。
 *
 * @param contractId 合同标识
 * @param extraction 抽取结果
 * @param risks      风险清单
 * @param reportPath 报告相对路径，导出失败时为 null
 * @param stats      运行统计
 */
public record ReviewOutcome(long contractId, ExtractionResult extraction, java.util.List<RiskItem> risks,
                            String reportPath, ReviewStats stats) {
}
