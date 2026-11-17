package com.crag.common.domain;

import com.crag.common.api.ErrorCode;

/**
 * 业务异常：携带错误码，由全局异常处理器转换为统一响应（规范 3.6）。
 *
 * <p>异常中的 {@code detail} 只用于日志，禁止拼入对外消息。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String detail;

    /**
     * 构造业务异常。
     *
     * @param errorCode 错误码
     * @param detail    仅用于日志的详细原因
     * @param cause     原始异常，可为 null
     */
    public BizException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * 构造业务异常。
     *
     * @param errorCode 错误码
     * @param detail    仅用于日志的详细原因
     */
    public BizException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    /**
     * 以错误码默认文案构造业务异常。
     *
     * @param errorCode 错误码
     * @return 业务异常
     */
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode, null);
    }

    /**
     * 以错误码与日志细节构造业务异常。
     *
     * @param errorCode 错误码
     * @param detail    仅用于日志的详细原因
     * @return 业务异常
     */
    public static BizException of(ErrorCode errorCode, String detail) {
        return new BizException(errorCode, detail);
    }

    /**
     * 错误码。
     *
     * @return 错误码
     */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 日志细节。
     *
     * @return 详细原因，可能为 null
     */
    public String detail() {
        return detail;
    }
}
