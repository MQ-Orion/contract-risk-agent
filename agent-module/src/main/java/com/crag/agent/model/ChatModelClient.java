package com.crag.agent.model;

/**
 * 对话模型客户端端口（规范 7.6、7.7）。
 *
 * <p>实现必须自带超时、重试、并发限制与熔断；调用方只关心“拿到内容或拿到降级原因”。</p>
 */
public interface ChatModelClient {

    /**
     * 发起一次对话调用。
     *
     * @param request 请求
     * @return 响应；失败时 {@code degraded=true} 且 content 为空
     */
    ChatResponse complete(ChatRequest request);
}
