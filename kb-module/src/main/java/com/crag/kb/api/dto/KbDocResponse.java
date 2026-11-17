package com.crag.kb.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 知识库文档响应。
 *
 * @param docId         文档标识
 * @param docKey        逻辑文档标识
 * @param title         标题
 * @param docType       文档类型
 * @param versionNo     版本号
 * @param active        是否上架
 * @param syncStatus    同步状态
 * @param lastSyncedAt  上次同步完成时间
 * @param updatedAt     更新时间
 */
@Schema(description = "知识库文档")
public record KbDocResponse(String docId,
                            String docKey,
                            String title,
                            String docType,
                            int versionNo,
                            boolean active,
                            String syncStatus,
                            Instant lastSyncedAt,
                            Instant updatedAt) {
}
