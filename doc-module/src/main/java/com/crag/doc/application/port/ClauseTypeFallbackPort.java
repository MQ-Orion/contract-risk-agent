package com.crag.doc.application.port;

import com.crag.common.text.TypeClassification;

/**
 * 条款类型模型兜底分类端口（规范 5.3）。
 *
 * <p>doc-module 禁止调用 LLM（规范 2.1），因此模型兜底由 agent-module 实现本端口注入；
 * 默认实现返回“其他 + DEFAULT”，保证未接入模型时链路仍可运行。</p>
 */
public interface ClauseTypeFallbackPort {

    /**
     * 兜底分类。
     *
     * @param headline 条款标题行
     * @param body     条款正文
     * @return 类型判定结果
     */
    TypeClassification classify(String headline, String body);

    /**
     * 默认实现：不调用模型，直接返回其他。
     *
     * @return 默认端口
     */
    static ClauseTypeFallbackPort noop() {
        return (headline, body) -> new TypeClassification(
                com.crag.common.text.ClauseTypeEnum.OTHER,
                com.crag.common.text.TypeSourceEnum.DEFAULT);
    }
}
