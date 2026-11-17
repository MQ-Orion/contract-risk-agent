package com.crag.doc.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 条款切片响应（规范 5.3：必须保留条款序号与全文索引，便于回溯原文位置）。
 *
 * @param clauseNo      条款序号
 * @param clauseType    条款类型 code
 * @param typeSource    类型来源
 * @param text          切片文本
 * @param fullTextIndex 全文递增索引
 * @param charStart     原文起始偏移
 * @param charEnd       原文结束偏移
 */
@Schema(description = "条款切片")
public record ClauseResponse(
        @Schema(description = "条款序号", example = "第三条") String clauseNo,
        @Schema(description = "条款类型", example = "PAYMENT") String clauseType,
        @Schema(description = "类型来源", example = "KEYWORD") String typeSource,
        @Schema(description = "切片文本") String text,
        @Schema(description = "全文递增索引") int fullTextIndex,
        @Schema(description = "原文起始偏移") int charStart,
        @Schema(description = "原文结束偏移") int charEnd) {
}
