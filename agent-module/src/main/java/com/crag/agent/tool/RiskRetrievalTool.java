package com.crag.agent.tool;

import com.crag.retrieval.domain.model.RetrievalResult;

/**
 * 风控依据检索工具（规范 7.1：risk_retrieval_tool，超时 5 秒，幂等）。
 *
 * <p>内部只调用检索唯一出口 {@code RetrievalService}，禁止自行编写检索 SQL（规范 6.1）。</p>
 */
public interface RiskRetrievalTool extends AgentTool {

    /**
     * 检索风控依据。
     *
     * @param tenantId   租户
     * @param query      查询文本
     * @param clauseType 条款类型，可为 null
     * @return 检索结果或结构化失败
     */
    ToolOutcome<RetrievalResult> retrieve(long tenantId, String query, String clauseType);
}
