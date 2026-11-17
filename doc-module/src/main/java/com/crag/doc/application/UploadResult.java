package com.crag.doc.application;

/**
 * 上传结果。
 *
 * @param contractId   合同标识
 * @param taskId       解析任务标识
 * @param deduplicated 是否命中秒传（同租户同哈希复用已有合同）
 */
public record UploadResult(long contractId, long taskId, boolean deduplicated) {
}
