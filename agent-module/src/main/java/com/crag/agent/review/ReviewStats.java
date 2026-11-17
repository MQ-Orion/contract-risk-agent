package com.crag.agent.review;

import java.util.List;

/**
 * 初审预演统计（用于验收证据与报告元信息）。
 *
 * @param clauseCount        条款数
 * @param toolCalls          工具调用次数（规范 7.2 上限：条款数×3+5）
 * @param modelCalls         模型调用次数
 * @param ruleFailures       规则执行失败条数
 * @param retrievalDegraded  检索是否发生降级
 * @param modelDegraded      模型是否降级
 * @param budgetExhausted    令牌预算是否耗尽
 * @param converged          是否因达到调用上限而强制收敛
 * @param manualRequired     是否整体转人工
 * @param elapsedMillis      总耗时
 * @param promptVersion      Prompt 版本
 * @param modelVersion       模型版本
 * @param degradeReasons     降级原因列表
 */
public record ReviewStats(int clauseCount,
                          int toolCalls,
                          int modelCalls,
                          int ruleFailures,
                          boolean retrievalDegraded,
                          boolean modelDegraded,
                          boolean budgetExhausted,
                          boolean converged,
                          boolean manualRequired,
                          long elapsedMillis,
                          String promptVersion,
                          String modelVersion,
                          List<String> degradeReasons) {

    /**
     * 构造统计，原因列表做不可变复制。
     */
    public ReviewStats {
        degradeReasons = degradeReasons == null ? List.of() : List.copyOf(degradeReasons);
    }
}
