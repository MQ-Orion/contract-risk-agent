package com.crag.agent.tool;

/**
 * Agent 工具标记接口（规范 7.1）：所有工具必须由 {@link ToolRegistry} 统一注册。
 */
public interface AgentTool {

    /**
     * 工具描述（名称、超时、幂等）。
     *
     * @return 描述
     */
    ToolDescriptor descriptor();
}
