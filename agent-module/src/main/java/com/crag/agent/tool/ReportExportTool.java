package com.crag.agent.tool;

import com.crag.agent.review.ReportMeta;
import com.crag.agent.review.RiskItem;
import java.util.List;

/**
 * 报告导出工具（规范 7.1：report_export_tool，超时 10 秒，按任务覆盖）。
 */
public interface ReportExportTool extends AgentTool {

    /**
     * 导出报告。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param risks      风险清单
     * @param meta       报告元信息
     * @return 导出结果（文件路径）
     */
    ToolOutcome<ReportExportResult> export(long tenantId, long contractId, List<RiskItem> risks, ReportMeta meta);
}
