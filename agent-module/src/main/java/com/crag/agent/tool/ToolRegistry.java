package com.crag.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心（规范 7.1：工具必须由注册中心统一注册，禁止在编排代码内直接构造）。
 *
 * <p>注册在构造期一次完成；编排器只通过本类取工具，因此新增或替换工具不需要改编排代码。
 * 工具内部禁止调用其他工具——本类不向工具暴露自身，从结构上避免隐式链式调用（规范 7.1）。</p>
 *
 * <p>线程安全性：构造后只读，可共享。</p>
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * 构造注册中心。
     *
     * @param ruleCheckTool     规则校验工具
     * @param riskRetrievalTool 检索工具
     * @param reportExportTool  报告导出工具
     */
    public ToolRegistry(RuleCheckTool ruleCheckTool, RiskRetrievalTool riskRetrievalTool,
                        ReportExportTool reportExportTool) {
        register(ruleCheckTool);
        register(riskRetrievalTool);
        register(reportExportTool);
    }

    private void register(AgentTool tool) {
        String name = tool.descriptor().name();
        if (tools.putIfAbsent(name, tool) != null) {
            throw new IllegalStateException("tool already registered: " + name);
        }
    }

    /**
     * 全部工具描述，供接口展示与契约测试。
     *
     * @return 描述列表
     */
    public List<ToolDescriptor> descriptors() {
        return tools.values().stream().map(AgentTool::descriptor).toList();
    }

    /**
     * 取规则校验工具。
     *
     * @return 工具
     */
    public RuleCheckTool ruleCheck() {
        return (RuleCheckTool) require("rule_check_tool");
    }

    /**
     * 取检索工具。
     *
     * @return 工具
     */
    public RiskRetrievalTool retrieval() {
        return (RiskRetrievalTool) require("risk_retrieval_tool");
    }

    /**
     * 取报告导出工具。
     *
     * @return 工具
     */
    public ReportExportTool reportExport() {
        return (ReportExportTool) require("report_export_tool");
    }

    private AgentTool require(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalStateException("tool not registered: " + name);
        }
        return tool;
    }
}
