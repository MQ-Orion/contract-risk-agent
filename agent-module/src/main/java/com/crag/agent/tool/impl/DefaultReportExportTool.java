package com.crag.agent.tool.impl;

import com.crag.agent.report.DocxReportRenderer;
import com.crag.agent.report.ReportFileStorage;
import com.crag.agent.review.ReportMeta;
import com.crag.agent.review.RiskItem;
import com.crag.agent.tool.ReportExportResult;
import com.crag.agent.tool.ReportExportTool;
import com.crag.agent.tool.ToolDescriptor;
import com.crag.agent.tool.ToolOutcome;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 报告导出工具实现（规范 7.1：超时 10 秒，按任务覆盖）。
 *
 * <p>确定性代码：渲染 + 落盘，不调用模型。失败返回结构化原因，由编排层决定报告缺失时的降级方式。</p>
 */
public class DefaultReportExportTool implements ReportExportTool {

    private static final Logger log = LoggerFactory.getLogger(DefaultReportExportTool.class);

    private static final ToolDescriptor DESCRIPTOR =
            new ToolDescriptor("report_export_tool", 10_000L, true, "按风险明细实时渲染 DOCX 报告并覆盖落盘");

    private final DocxReportRenderer renderer;
    private final ReportFileStorage storage;

    /**
     * 构造工具。
     *
     * @param renderer 渲染器
     * @param storage  文件存储
     */
    public DefaultReportExportTool(DocxReportRenderer renderer, ReportFileStorage storage) {
        this.renderer = renderer;
        this.storage = storage;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolOutcome<ReportExportResult> export(long tenantId, long contractId, List<RiskItem> risks,
                                                  ReportMeta meta) {
        try {
            byte[] content = renderer.render(risks, meta);
            return ToolOutcome.ok(storage.write(tenantId, contractId, content, risks.size()));
        } catch (IOException e) {
            log.error("报告导出失败 tenantId={} contractId={}", tenantId, contractId, e);
            return ToolOutcome.failure("40002", "report_export_failed");
        }
    }
}
