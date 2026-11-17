package com.crag.common.text;


/**
 * 条款类型判定结果（规范 5.3）。
 *
 * @param clauseType 条款类型
 * @param typeSource 标签来源
 */
public record TypeClassification(ClauseTypeEnum clauseType, TypeSourceEnum typeSource) {

    /**
     * 是否需要模型兜底分类。
     *
     * @return 未命中任何关键词时返回 true
     */
    public boolean requiresModelFallback() {
        return typeSource == TypeSourceEnum.DEFAULT;
    }
}
