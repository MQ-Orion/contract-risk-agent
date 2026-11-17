package com.crag.doc.domain.model;

import java.time.Instant;

/**
 * 解析任务记录（规范 5.5）。
 *
 * @param id           任务标识
 * @param contractId   合同标识
 * @param tenantId     租户
 * @param status       当前状态
 * @param retryCount   已重试次数
 * @param nextRetryAt  下次可重试时间（UTC），无则 null
 * @param errorCode    失败错误码，成功为 null
 * @param errorMessage 失败原因（仅日志与运维可见）
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record ParseTaskRecord(long id,
                              long contractId,
                              long tenantId,
                              ParseStatusEnum status,
                              int retryCount,
                              Instant nextRetryAt,
                              String errorCode,
                              String errorMessage,
                              Instant createdAt,
                              Instant updatedAt) {

    /**
     * 新建任务。
     *
     * @param id         任务标识
     * @param contractId 合同标识
     * @param tenantId   租户
     * @param now        当前时间
     * @return 待处理任务
     */
    public static ParseTaskRecord pending(long id, long contractId, long tenantId, Instant now) {
        return new ParseTaskRecord(id, contractId, tenantId, ParseStatusEnum.PENDING, 0, null, null, null, now, now);
    }

    /**
     * 状态流转。
     *
     * @param target 目标状态
     * @param now    当前时间
     * @return 新状态的任务记录
     */
    public ParseTaskRecord moveTo(ParseStatusEnum target, Instant now) {
        status.checkTransition(target);
        return new ParseTaskRecord(id, contractId, tenantId, target, retryCount, nextRetryAt,
                errorCode, errorMessage, createdAt, now);
    }

    /**
     * 记录一次失败。
     *
     * @param errorCode    错误码名称
     * @param errorMessage 失败原因
     * @param now          当前时间
     * @param nextRetryAt  下次重试时间
     * @return 失败态任务记录
     */
    public ParseTaskRecord fail(String errorCode, String errorMessage, Instant now, Instant nextRetryAt) {
        status.checkTransition(ParseStatusEnum.FAILED);
        return new ParseTaskRecord(id, contractId, tenantId, ParseStatusEnum.FAILED, retryCount + 1,
                nextRetryAt, errorCode, errorMessage, createdAt, now);
    }

    /**
     * 失败后重新排队。
     *
     * @param now 当前时间
     * @return 待处理任务记录
     */
    public ParseTaskRecord retry(Instant now) {
        status.checkTransition(ParseStatusEnum.PENDING);
        return new ParseTaskRecord(id, contractId, tenantId, ParseStatusEnum.PENDING, retryCount, null,
                null, null, createdAt, now);
    }
}
