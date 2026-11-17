package com.crag.kb.application.port;

import java.util.List;

/**
 * 向量化端口（规范 4.3、7.7：模型通过 OpenAI 兼容协议接入，维度集中配置）。
 */
public interface EmbeddingPort {

    /**
     * 批量向量化。
     *
     * @param texts 文本列表
     * @return 与输入顺序一致的向量列表
     */
    List<float[]> embed(List<String> texts);

    /**
     * 向量维度。
     *
     * @return 维度
     */
    int dimension();
}
