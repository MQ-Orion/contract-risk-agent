package com.crag.doc.domain.model;

import java.time.Instant;

/**
 * 合同主记录（表 contract_main，归属 doc-module，规范 4.2）。
 *
 * @param id          合同标识
 * @param tenantId    租户标识
 * @param contractNo  合同编号，可为空
 * @param name        合同名称
 * @param fileHash    文件哈希（SHA-256），同租户唯一，用于秒传与去重
 * @param filePath    存储相对路径，不含原始文件名
 * @param format      文档格式
 * @param fileSize    文件字节数
 * @param pageCount   页数
 * @param uploaderId  上传人
 * @param auditStatus 审核状态，解析阶段恒为 null
 * @param versionNo   版本号，从 1 开始
 * @param createdAt   创建时间（UTC）
 */
public record ContractRecord(long id,
                             long tenantId,
                             String contractNo,
                             String name,
                             String fileHash,
                             String filePath,
                             DocumentFormatEnum format,
                             long fileSize,
                             int pageCount,
                             long uploaderId,
                             String auditStatus,
                             int versionNo,
                             Instant createdAt) {

    /**
     * 生成新合同记录。
     *
     * @param id         合同标识
     * @param tenantId   租户
     * @param name       名称
     * @param fileHash   哈希
     * @param filePath   路径
     * @param format     格式
     * @param fileSize   大小
     * @param uploaderId 上传人
     * @param now        当前时间（统一时钟注入，规范 3.10）
     * @return 记录
     */
    public static ContractRecord created(long id, long tenantId, String name, String fileHash, String filePath,
                                        DocumentFormatEnum format, long fileSize, long uploaderId, Instant now) {
        return new ContractRecord(id, tenantId, null, name, fileHash, filePath, format, fileSize, 0,
                uploaderId, null, 1, now);
    }

    /**
     * 回填页数。
     *
     * @param parsedPages 实际页数
     * @return 新记录
     */
    public ContractRecord withPageCount(int parsedPages) {
        return new ContractRecord(id, tenantId, contractNo, name, fileHash, filePath, format, fileSize,
                parsedPages, uploaderId, auditStatus, versionNo, createdAt);
    }
}
