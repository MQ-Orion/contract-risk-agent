package com.crag.kb.application.port;

import com.crag.kb.domain.model.RetrievedChunk;
import java.util.List;

/**
 * 知识库切片只读端口：双路召回 SQL 的唯一实现位置（规范 6.2）。
 *
 * <p>检索模块只调用这两个方法，融合与降级在检索模块内完成；禁止把召回 SQL 写到检索模块，
 * 否则等于绕过属主模块，与规范 2.2 的数据所有权冲突。</p>
 */
public interface KbChunkReadPort {

    /**
     * 向量路召回。
     *
     * @param queryVector 查询向量，PG 向量字面量形式
     * @param clauseType  条款类型，为空表示不过滤
     * @param tenantId    租户
     * @param limit       返回条数上限
     * @return 按相似度降序的切片
     */
    List<RetrievedChunk> vectorSearch(String queryVector, String clauseType, long tenantId, int limit);

    /**
     * 关键词路召回。
     *
     * @param query      查询文本
     * @param clauseType 条款类型，为空表示不过滤
     * @param tenantId   租户
     * @param limit      返回条数上限
     * @return 按全文相关度降序的切片
     */
    List<RetrievedChunk> keywordSearch(String query, String clauseType, long tenantId, int limit);
}
