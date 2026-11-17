package com.crag.doc.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 合同详情响应。
 *
 * @param contractId 合同标识
 * @param name       合同名称
 * @param format     文档格式
 * @param fileSize   文件字节数
 * @param pageCount  页数
 * @param versionNo  版本号
 * @param createdAt  创建时间
 */
@Schema(description = "合同详情")
public record ContractDetailResponse(
        @Schema(description = "合同标识") String contractId,
        @Schema(description = "合同名称") String name,
        @Schema(description = "文档格式") String format,
        @Schema(description = "文件字节数") long fileSize,
        @Schema(description = "页数") int pageCount,
        @Schema(description = "版本号") int versionNo,
        @Schema(description = "创建时间") Instant createdAt) {
}
