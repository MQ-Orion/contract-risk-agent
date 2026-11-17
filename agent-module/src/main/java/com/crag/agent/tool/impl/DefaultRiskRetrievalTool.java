package com.crag.agent.tool.impl;

import com.crag.agent.tool.RiskRetrievalTool;
import com.crag.agent.tool.ToolDescriptor;
import com.crag.agent.tool.ToolOutcome;
import com.crag.retrieval.application.RetrievalService;
import com.crag.retrieval.domain.model.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 检索工具实现（规范 7.1：超时 5 秒、幂等）。
 *
 * <p>只调用检索唯一出口（规范 6.1），不写 SQL、不直接读 kb 表。</p>
 */
public class DefaultRiskRetrievalTool implements RiskRetrievalTool {

    private static final Logger log = LoggerFactory.getLogger(DefaultRiskRetrievalTool.class);

    private static final ToolDescriptor DESCRIPTOR =
            new ToolDescriptor("risk_retrieval_tool", 5_000L, true, "检索风控依据，输出召回片段");

    private final RetrievalService retrievalService;

    /**
     * 构造工具。
     *
     * @param retrievalService 检索服务
     */
    public DefaultRiskRetrievalTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolOutcome<RetrievalResult> retrieve(long tenantId, String query, String clauseType) {
        try {
            return ToolOutcome.ok(retrievalService.retrieve(query, clauseType, tenantId));
        } catch (RuntimeException e) {
            log.warn("检索工具失败 tenantId={} clauseType={} reason={}", tenantId, clauseType, e.getMessage());
            return ToolOutcome.failure("40003", "retrieval_failed");
        }
    }
}
