package com.crag.common.api;

/**
 * 错误码集中定义（规范 10.2）。
 *
 * <p>码段约定：0 成功、1xxxx 通用、2xxxx 文档处理、3xxxx 知识库、4xxxx 审核、5xxxx 安全。
 * 面向用户的消息不得包含内部实现细节；详细原因写入日志（规范 3.6、10.2）。</p>
 */
public enum ErrorCode {

    /** 成功。 */
    SUCCESS(0, "成功"),

    /** 参数校验失败。 */
    PARAM_INVALID(10001, "参数校验失败"),
    /** 未认证。 */
    UNAUTHENTICATED(10002, "未认证或登录已过期"),
    /** 无权限（角色级动作不允许）。资源级越权统一返回 10004，见规范 4.4、11.1。 */
    PERMISSION_DENIED(10003, "无权限"),
    /** 资源不存在；跨租户访问同样返回本码，防止资源存在性枚举。 */
    RESOURCE_NOT_FOUND(10004, "资源不存在"),
    /** 触发限流。 */
    RATE_LIMITED(10005, "请求过于频繁，请稍后重试"),
    /** 系统异常。 */
    SYSTEM_ERROR(10006, "系统繁忙，请稍后重试"),

    /** 文件类型不支持。 */
    FILE_TYPE_UNSUPPORTED(20001, "文件类型不支持，仅接受 PDF、DOC、DOCX"),
    /** 超出大小或页数限制。 */
    FILE_LIMIT_EXCEEDED(20002, "文件超出大小或页数限制"),
    /** 解析失败。 */
    PARSE_FAILED(20003, "文档解析失败，请重试或更换文件"),
    /** 切分失败。 */
    SPLIT_FAILED(20004, "条款切分失败"),
    /** OCR 未实现：接口签名固定，实现体抛出本错误（规范 5.2）。 */
    OCR_UNSUPPORTED(20005, "暂不支持扫描件识别，请上传可编辑版本"),

    /** 知识库文档版本不存在。 */
    KB_VERSION_NOT_FOUND(30001, "知识库文档版本不存在"),
    /** 知识库文档已下架。 */
    KB_DOC_OFFLINE(30002, "知识库文档已下架"),
    /** 向量化失败。 */
    EMBEDDING_FAILED(30003, "向量化失败"),

    /** 任务状态非法。 */
    TASK_STATE_ILLEGAL(40001, "任务状态不允许该操作"),
    /** 规则执行失败。 */
    RULE_EXEC_FAILED(40002, "规则执行失败"),
    /** 检索降级。 */
    RETRIEVAL_DEGRADED(40003, "检索已降级"),
    /** 模型不可用。 */
    MODEL_UNAVAILABLE(40004, "模型服务不可用"),
    /** 引用校验失败。 */
    CITATION_INVALID(40005, "引用校验失败"),
    /** 评估未通过。 */
    EVAL_NOT_PASSED(40006, "评估未通过"),

    /** 跨租户访问拒绝。 */
    CROSS_TENANT_DENIED(50001, "跨租户访问被拒绝");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 业务码。
     *
     * @return 业务码
     */
    public int code() {
        return code;
    }

    /**
     * 面向用户的默认消息。
     *
     * @return 消息
     */
    public String message() {
        return message;
    }
}
