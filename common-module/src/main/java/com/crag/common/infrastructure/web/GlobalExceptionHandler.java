package com.crag.common.infrastructure.web;

import com.crag.common.api.ApiResponse;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TraceContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理器：把所有异常收敛为统一响应体（规范 3.6、10.2）。
 *
 * <p>对外消息只取错误码默认文案，堆栈、SQL、内部路径、模型原始返回一律不进入响应；
 * 详细原因按 ERROR/WARN 级写入日志并携带链路标识。</p>
 *
 * <p>HTTP 状态码恒为 200，业务结果由响应体 {@code code} 表达；这样跨租户访问与资源不存在
 * 返回完全一致的响应，不暴露资源存在性（规范 4.4、11.1）。</p>
 *
 * <p>为什么不用 4xx/5xx：状态码本身就是侧信道——越权返回 403、不存在返回 404，攻击者可据此枚举资源。
 * 统一 200 后，调用方只能依据业务码判断，且客户端无需区分传输层与业务层错误（规范 10.2）。</p>
 *
 * <p>消息策略：对外只用 {@link ErrorCode#message()}，{@code BizException.detail()} 只进日志。
 * detail 里常有资源标识与内部原因，直接回显等于把实现细节暴露给调用方（规范 3.6、10.2）。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常。
     *
     * @param ex 业务异常
     * @return 统一响应
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException ex) {
        log.warn("业务异常 code={} traceId={} detail={}", ex.errorCode().code(), TraceContext.current(), ex.detail());
        return ApiResponse.fail(ex.errorCode());
    }

    /**
     * 请求体参数校验失败。
     *
     * @param ex 校验异常
     * @return 统一响应
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResponse<Void> handleBindingException(Exception ex) {
        String field = firstField(ex);
        log.warn("参数校验失败 traceId={} field={}", TraceContext.current(), field);
        return ApiResponse.fail(ErrorCode.PARAM_INVALID);
    }

    /**
     * 方法级参数校验失败。
     *
     * @param ex 校验异常
     * @return 统一响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("参数校验失败 traceId={} violations={}", TraceContext.current(), ex.getConstraintViolations().size());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID);
    }

    /**
     * 路径/查询参数类型不匹配（如规则标识传了非法字符串）。
     *
     * <p>调用链：Spring MVC 绑定参数 → 本方法 → 统一返回 10001。若不单独处理，会落到兜底分支返回
     * 10006（系统繁忙），把“调用方参数错”误报成“服务端故障”，影响排查（规范 10.2）。</p>
     *
     * @param ex 类型转换异常
     * @return 统一响应
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配 traceId={} name={} value={}", TraceContext.current(), ex.getName(), ex.getValue());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID);
    }

    /**
     * 缺少请求参数或上传部件。
     *
     * @param ex 缺参异常
     * @return 统一响应
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ApiResponse<Void> handleMissingParameter(Exception ex) {
        log.warn("缺少请求参数 traceId={} type={}", TraceContext.current(), ex.getClass().getSimpleName());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID);
    }

    /**
     * 请求体不可解析。
     *
     * @param ex 解析异常
     * @return 统一响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体不可解析 traceId={}", TraceContext.current(), ex);
        return ApiResponse.fail(ErrorCode.PARAM_INVALID);
    }

    /**
     * 上传体积超出容器限制。
     *
     * @param ex 超限异常
     * @return 统一响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("上传体积超出限制 traceId={} maxUploadSize={}", TraceContext.current(), ex.getMaxUploadSize());
        return ApiResponse.fail(ErrorCode.FILE_LIMIT_EXCEEDED);
    }

    /**
     * 兜底异常。
     *
     * @param ex 未预期异常
     * @return 统一响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception ex) {
        log.error("未预期异常 traceId={} type={}", TraceContext.current(), ex.getClass().getName(), ex);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR);
    }

    private String firstField(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manve && manve.getBindingResult().getFieldError() != null) {
            FieldError fieldError = manve.getBindingResult().getFieldError();
            return fieldError == null ? "unknown" : fieldError.getField();
        }
        if (ex instanceof BindException be && be.getBindingResult().getFieldError() != null) {
            FieldError fieldError = be.getBindingResult().getFieldError();
            return fieldError == null ? "unknown" : fieldError.getField();
        }
        return "unknown";
    }
}
