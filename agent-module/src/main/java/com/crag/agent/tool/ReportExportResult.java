package com.crag.agent.tool;

/**
 * 报告导出结果。
 *
 * @param filePath    相对路径（对外展示与 report_url 使用）
 * @param absolutePath 绝对路径（本地排查用，不对外输出）
 * @param riskCount   写入的风险条数
 */
public record ReportExportResult(String filePath, String absolutePath, int riskCount) {
}
