package com.crag.retrieval.application;

import com.crag.retrieval.domain.model.RetrievalResult;

/**
 * 检索唯一出口（规范 6.1）：其他模块禁止自行编写检索 SQL，禁止绕过本接口。
 */
public interface RetrievalService {

    /**
     * 检索风控依据。
     *
     * @param query      查询文本
     * @param clauseType 条款类型，可为空表示不限
     * @param tenantId   租户
     * @return 检索结果
     */
    RetrievalResult retrieve(String query, String clauseType, long tenantId);
}
