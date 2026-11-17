package com.crag.kb.domain.model;

import java.time.Instant;

/**
 * 知识库文档记录（表 kb_doc，规范 4.2、5.6）。
 *
 * @param id              文档标识
 * @param tenantId        租户
 * @param docKey          逻辑文档标识，跨版本稳定
 * @param title           标题
 * @param docType         文档类型
 * @param versionNo       版本号，从 1 开始
 * @param active          是否上架
 * @param sourceHash      源文本哈希
 * @param syncedFileHash  上次同步完成时的源哈希快照
 * @param lastSyncedAt    上次同步完成时间
 * @param syncStatus      同步状态
 * @param contentText     已解析正文，对账与重建只读本列
 * @param createdAt       创建时间
 */
public record KbDocRecord(long id,
                          long tenantId,
                          String docKey,
                          String title,
                          KbDocTypeEnum docType,
                          int versionNo,
                          boolean active,
                          String sourceHash,
                          String syncedFileHash,
                          Instant lastSyncedAt,
                          KbSyncStatusEnum syncStatus,
                          String contentText,
                          Instant createdAt) {

    /**
     * 状态流转。
     *
     * @param target 目标状态
     * @return 新记录
     */
    public KbDocRecord withStatus(KbSyncStatusEnum target) {
        syncStatus.checkTransition(target);
        return new KbDocRecord(id, tenantId, docKey, title, docType, versionNo, active, sourceHash,
                syncedFileHash, lastSyncedAt, target, contentText, createdAt);
    }

    /**
     * 同步成功：记录同步快照并置为就绪。
     *
     * @param syncedHash 本次同步使用的源哈希
     * @param syncedAt   完成时间
     * @return 新记录
     */
    public KbDocRecord synced(String syncedHash, Instant syncedAt) {
        return new KbDocRecord(id, tenantId, docKey, title, docType, versionNo, active, sourceHash,
                syncedHash, syncedAt, KbSyncStatusEnum.READY, contentText, createdAt);
    }

    /**
     * 上下架切换。
     *
     * @param target 目标状态
     * @return 新记录
     */
    public KbDocRecord withActive(boolean target) {
        return new KbDocRecord(id, tenantId, docKey, title, docType, versionNo, target, sourceHash,
                syncedFileHash, lastSyncedAt, syncStatus, contentText, createdAt);
    }

    /**
     * 更新正文：重置同步状态为待向量化。
     *
     * @param newContent 新正文
     * @param newHash    新正文哈希
     * @return 新记录
     */
    public KbDocRecord withContent(String newContent, String newHash) {
        return new KbDocRecord(id, tenantId, docKey, title, docType, versionNo, active, newHash,
                syncedFileHash, lastSyncedAt, KbSyncStatusEnum.PENDING, newContent, createdAt);
    }
}
