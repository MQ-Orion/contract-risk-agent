package com.crag.kb.domain.model;

import com.crag.common.text.ClauseTypeEnum;

/**
 * 知识库切片记录（表 kb_chunk，规范 4.2、4.3）。
 *
 * @param id          切片标识
 * @param tenantId    租户
 * @param kbDocId     文档标识
 * @param chunkKey    稳定业务键，由 docKey 与条款序号生成
 * @param clauseNo    条款序号
 * @param chunkText   切片文本
 * @param chunkType   条款类型
 * @param contentHash 规范化文本哈希，用于跳过未变更切片
 * @param embedding   向量，未写入时为 null
 */
public record KbChunkRecord(long id,
                            long tenantId,
                            long kbDocId,
                            String chunkKey,
                            String clauseNo,
                            String chunkText,
                            ClauseTypeEnum chunkType,
                            String contentHash,
                            float[] embedding) {

    /**
     * 是否已写入向量。
     *
     * @return 有向量返回 true
     */
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    /**
     * 附加向量。
     *
     * @param vector 向量
     * @return 新记录
     */
    public KbChunkRecord withEmbedding(float[] vector) {
        return new KbChunkRecord(id, tenantId, kbDocId, chunkKey, clauseNo, chunkText, chunkType, contentHash, vector);
    }

    /**
     * 重绑到文档版本。
     *
     * <p>版本切换时 chunk_key 不变（规范 4.3 的全局唯一键），切片行需要改挂到新版本文档标识，
     * 否则检索按 {@code kb_doc_id} 关联上架版本时会查不到（规范 9.5）。</p>
     *
     * @param newKbDocId 新文档版本标识
     * @return 新记录
     */
    public KbChunkRecord withKbDocId(long newKbDocId) {
        return new KbChunkRecord(id, tenantId, newKbDocId, chunkKey, clauseNo, chunkText, chunkType, contentHash,
                embedding);
    }
}
