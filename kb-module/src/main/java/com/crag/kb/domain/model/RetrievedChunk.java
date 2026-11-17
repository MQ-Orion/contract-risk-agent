package com.crag.kb.domain.model;

import com.crag.common.text.ClauseTypeEnum;

/**
 * 召回结果切片（规范 6.1 返回结构中的 chunks 元素）。
 *
 * @param chunkId       切片标识
 * @param chunkKey      切片业务键
 * @param text          切片文本
 * @param sourceDocId   来源文档标识
 * @param sourceDocTitle 来源文档标题
 * @param chunkType     条款类型
 * @param score         分数：召回阶段为原始分，融合后为归一化分
 */
public record RetrievedChunk(long chunkId,
                             String chunkKey,
                             String text,
                             long sourceDocId,
                             String sourceDocTitle,
                             ClauseTypeEnum chunkType,
                             double score) {

    /**
     * 替换分数。
     *
     * @param newScore 新分数
     * @return 新结果
     */
    public RetrievedChunk withScore(double newScore) {
        return new RetrievedChunk(chunkId, chunkKey, text, sourceDocId, sourceDocTitle, chunkType, newScore);
    }
}
