package com.crag.kb.application.port;

import com.crag.kb.domain.model.KbChunkRecord;
import com.crag.kb.domain.model.SyncOutcome;
import java.util.List;

/**
 * 知识库切片仓储端口（表 kb_chunk 属主为 kb-module）。
 */
public interface KbChunkRepository {

    /**
     * 查询某文档的全部切片。
     *
     * @param kbDocId 文档标识
     * @return 切片列表
     */
    List<KbChunkRecord> listByDocId(long kbDocId);

    /**
     * 按 chunk_key 查询哈希，用于增量比对。
     *
     * @param kbDocId 文档标识
     * @return 业务键到内容哈希的映射
     */
    java.util.Map<String, String> contentHashes(long kbDocId);

    /**
     * 按逻辑文档标识查询跨版本的哈希，用于版本切换时保持增量（规范 9.5）。
     *
     * @param tenantId 租户
     * @param docKey   逻辑文档标识
     * @return 业务键到内容哈希的映射
     */
    java.util.Map<String, String> contentHashesByDocKey(long tenantId, String docKey);

    /**
     * 条件 upsert：哈希未变化的行不重写向量与更新时间（规范 4.3）。
     *
     * @param chunks 待写入切片（含向量）
     * @return 实际写入行数
     */
    int upsertChanged(List<KbChunkRecord> chunks);

    /**
     * 把未变化切片重绑到新版本文档，供版本切换使用（规范 9.5）。
     *
     * @param tenantId 租户
     * @param kbDocId  新版本文档标识
     * @param chunkKeys 当前版本仍存在的业务键
     * @return 重绑行数
     */
    int rebindDocId(long tenantId, long kbDocId, List<String> chunkKeys);

    /**
     * 通过 chunk_key 物理删除过期切片。
     *
     * <p>切片是正文的派生数据而非业务事实，且规范 4.3 的 uk_kb_chunk_key 为全局唯一索引，
     * 逻辑删除会阻塞同一业务键的重建，因此这里做物理删除。</p>
     *
     * @param chunkKeys 业务键
     * @return 删除行数
     */
    int deleteByKeys(List<String> chunkKeys);

    /**
     * 统计某文档的切片数，用于同步结果核对。
     *
     * @param kbDocId 文档标识
     * @return 切片数
     */
    int countByDocId(long kbDocId);
}
