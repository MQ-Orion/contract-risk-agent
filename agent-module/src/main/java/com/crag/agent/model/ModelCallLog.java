package com.crag.agent.model;

import java.time.Instant;

/**
 * 模型调用日志（表 model_call_log，规范 7.6）。
 *
 * <p>记录五项必需字段：模型名与版本、Prompt 版本、输入输出令牌数、耗时、是否降级。
 * 这些是成本核算与质量排查的唯一依据，缺失就无法回答“这次审核花了多少、为什么慢”。</p>
 *
 * @param id               日志标识
 * @param tenantId         租户
 * @param contractId       合同标识，可为 null
 * @param clauseNo         条款序号，可为 null
 * @param purpose          用途
 * @param modelName        模型名
 * @param modelVersion     模型版本
 * @param promptVersion    Prompt 版本
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param totalTokens      总 token
 * @param latencyMillis    耗时
 * @param degraded         是否降级
 * @param degradeReason    降级原因
 * @param traceId          链路标识
 * @param createdAt        记录时间
 */
public record ModelCallLog(long id,
                           long tenantId,
                           Long contractId,
                           String clauseNo,
                           ModelCallPurposeEnum purpose,
                           String modelName,
                           String modelVersion,
                           String promptVersion,
                           int promptTokens,
                           int completionTokens,
                           int totalTokens,
                           long latencyMillis,
                           boolean degraded,
                           String degradeReason,
                           String traceId,
                           Instant createdAt) {
}
