package com.crag.agent.rule.application;

/**
 * 规则执行异常（对应错误码 40002）。
 *
 * <p>规则失败不阻断整份审核：单条规则失败记为降级并继续其余规则（规范 8.2“求值超时判为规则执行失败并降级”）。
 * 因此该异常由引擎捕获并计数，只有保存前的试算会把它抛给调用方。
 */
public class RuleExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param message 原因
     */
    public RuleExecutionException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 原因
     * @param cause   原始异常
     */
    public RuleExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
