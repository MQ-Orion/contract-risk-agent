package com.crag.agent.tool;

/**
 * 工具描述（规范 7.1 表 10：名称、超时、幂等）。
 *
 * @param name         工具名，固定为 rule_check_tool / risk_retrieval_tool / report_export_tool
 * @param timeoutMillis 超时（毫秒）
 * @param idempotent   是否幂等
 * @param description  用途说明
 */
public record ToolDescriptor(String name, long timeoutMillis, boolean idempotent, String description) {
}
