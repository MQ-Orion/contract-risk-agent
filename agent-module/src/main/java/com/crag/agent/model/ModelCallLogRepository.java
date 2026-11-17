package com.crag.agent.model;

/**
 * 模型调用日志仓储端口（表 model_call_log，归属 agent-module）。
 */
public interface ModelCallLogRepository {

    /**
     * 追加一条调用日志。
     *
     * @param log 日志
     */
    void append(ModelCallLog log);
}
