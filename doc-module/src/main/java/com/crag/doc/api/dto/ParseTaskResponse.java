package com.crag.doc.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 解析任务状态响应（规范 5.5、10.6）。
 *
 * @param taskId      任务标识
 * @param contractId  合同标识
 * @param status      状态：PENDING/PARSING/SPLITTING/EMBEDDING/READY/FAILED
 * @param retryCount  已重试次数
 * @param errorCode   失败错误码
 * @param nextRetryAt 下次可重试时间
 * @param updatedAt   更新时间
 */
@Schema(description = "解析任务状态")
public record ParseTaskResponse(
        @Schema(description = "任务标识") String taskId,
        @Schema(description = "合同标识") String contractId,
        @Schema(description = "状态") String status,
        @Schema(description = "已重试次数") int retryCount,
        @Schema(description = "失败错误码") String errorCode,
        @Schema(description = "下次可重试时间") Instant nextRetryAt,
        @Schema(description = "更新时间") Instant updatedAt) {
}
