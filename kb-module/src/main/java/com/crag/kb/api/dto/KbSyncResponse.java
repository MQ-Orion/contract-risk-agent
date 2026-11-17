package com.crag.kb.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 增量同步结果响应（规范 5.6、16.2）。
 *
 * @param inserted 新增切片数
 * @param updated  重写向量数
 * @param skipped  哈希未变化而跳过的切片数
 * @param deleted  删除的过期切片数
 * @param skipRatio 向量化跳过率
 */
@Schema(description = "增量同步结果")
public record KbSyncResponse(int inserted, int updated, int skipped, int deleted, double skipRatio) {
}
