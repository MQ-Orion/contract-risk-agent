package com.crag.doc.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 上传响应（规范 10.5：返回合同标识与任务标识，客户端凭任务标识订阅进度）。
 *
 * @param contractId   合同标识（字符串化，避免前端精度丢失）
 * @param taskId       解析任务标识（字符串化）
 * @param deduplicated 是否命中秒传
 */
@Schema(description = "合同上传结果")
public record ContractUploadResponse(
        @Schema(description = "合同标识", example = "1000000000000000001") String contractId,
        @Schema(description = "解析任务标识", example = "1000000000000000002") String taskId,
        @Schema(description = "是否命中秒传") boolean deduplicated) {
}
