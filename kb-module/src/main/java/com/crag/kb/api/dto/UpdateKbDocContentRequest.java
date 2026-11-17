package com.crag.kb.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 更新知识库文档正文请求。
 *
 * @param content 新正文
 */
@Schema(description = "更新知识库正文")
public record UpdateKbDocContentRequest(
        @NotBlank @Schema(description = "新正文") String content) {
}
