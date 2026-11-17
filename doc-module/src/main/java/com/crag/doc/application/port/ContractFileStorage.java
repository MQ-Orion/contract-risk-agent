package com.crag.doc.application.port;

import com.crag.doc.domain.model.DocumentFormatEnum;
import java.nio.file.Path;

/**
 * 合同文件存储端口（规范 5.1：按租户与年月生成路径，禁止使用原始文件名）。
 */
public interface ContractFileStorage {

    /**
     * 保存文件。
     *
     * @param sourceFile 源文件（临时文件）
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param format     文档格式
     * @return 存储相对路径
     */
    String store(Path sourceFile, long tenantId, long contractId, DocumentFormatEnum format);

    /**
     * 解析存储路径。
     *
     * @param relativePath 存储相对路径
     * @return 绝对路径
     */
    Path resolve(String relativePath);
}
