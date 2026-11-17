package com.crag.retrieval.application.port;

import com.crag.retrieval.domain.model.RetrievalResult;

/**
 * 检索缓存端口（规范 6.6）。
 */
public interface RetrievalCachePort {

    /**
     * 读取缓存。
     *
     * @param tenantId   租户
     * @param clauseType 条款类型
     * @param query      查询
     * @param indexVersion 索引版本
     * @return 缓存结果，未命中返回 null
     */
    RetrievalResult get(long tenantId, String clauseType, String query, String indexVersion);

    /**
     * 写入缓存。
     *
     * @param tenantId   租户
     * @param clauseType 条款类型
     * @param query      查询
     * @param indexVersion 索引版本
     * @param result     检索结果
     */
    void put(long tenantId, String clauseType, String query, String indexVersion, RetrievalResult result);

    /**
     * 当前租户的索引版本；不存在时初始化为 0。
     *
     * @param tenantId 租户
     * @return 版本号
     */
    String indexVersion(long tenantId);

    /**
     * 按租户批量失效：递增索引版本，旧缓存自然过期（规范 6.6）。
     *
     * @param tenantId 租户
     */
    void invalidateTenant(long tenantId);
}
