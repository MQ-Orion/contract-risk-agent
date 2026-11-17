package com.crag.agent.model;

/**
 * 模型调用请求。
 *
 * @param tenantId      租户（调用日志按租户留存，异步线程也必须有值）
 * @param purpose       用途
 * @param promptVersion Prompt 版本（规范 7.3 要求随任务记录）
 * @param systemPrompt  系统提示
 * @param userPrompt    用户提示
 * @param contractId    合同标识，可为 null（无合同上下文的调用）
 * @param clauseNo      条款序号，可为 null
 * @param maxTokens     本次最大输出 token
 */
public record ChatRequest(long tenantId,
                          ModelCallPurposeEnum purpose,
                          String promptVersion,
                          String systemPrompt,
                          String userPrompt,
                          Long contractId,
                          String clauseNo,
                          int maxTokens) {
}
