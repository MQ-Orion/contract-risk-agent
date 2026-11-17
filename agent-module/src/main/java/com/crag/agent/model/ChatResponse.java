package com.crag.agent.model;

import java.util.List;

/**
 * 模型调用响应。
 *
 * <p>{@code degraded=true} 表示未取得可用输出（熔断、队列满、重试耗尽、输出为空），
 * 调用方必须按“规则结论 + 人工提示”继续，不能把空内容当作模型结论（规范 7.4、7.6）。</p>
 *
 * @param content          模型输出文本
 * @param model            模型名
 * @param modelVersion     模型版本
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param latencyMillis    耗时
 * @param degraded         是否降级
 * @param degradeReason    降级原因
 * @param warnings         非致命告警（如输出被截断）
 */
public record ChatResponse(String content,
                           String model,
                           String modelVersion,
                           int promptTokens,
                           int completionTokens,
                           long latencyMillis,
                           boolean degraded,
                           String degradeReason,
                           List<String> warnings) {

    /**
     * 构造响应，告警列表做不可变复制。
     */
    public ChatResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 降级响应（未发生实际调用或调用失败）。
     *
     * @param model      模型名
     * @param reason     降级原因
     * @param latencyMillis 已耗时
     * @return 响应
     */
    public static ChatResponse degraded(String model, String reason, long latencyMillis) {
        return new ChatResponse("", model, model, 0, 0, latencyMillis, true, reason, List.of());
    }

    /**
     * 总 token。
     *
     * @return 输入 + 输出
     */
    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
