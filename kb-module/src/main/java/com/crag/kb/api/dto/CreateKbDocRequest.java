package com.crag.kb.api.dto;

import com.crag.kb.domain.model.KbDocTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新建知识库文档请求。
 *
 * @param title    标题
 * @param docType  文档类型
 * @param content  正文（已解析文本）
 * @param activate 是否立即上架
 */
@Schema(description = "新建知识库文档")
public record CreateKbDocRequest(
        @NotBlank @Schema(description = "标题", example = "合同管理办法") String title,
        @NotNull @Schema(description = "文档类型") KbDocTypeEnum docType,
        @NotBlank @Schema(description = "正文") String content,
        @Schema(description = "是否立即上架") boolean activate) {
}
