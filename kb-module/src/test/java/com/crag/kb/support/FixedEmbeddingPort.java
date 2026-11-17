package com.crag.kb.support;

import com.crag.kb.application.port.EmbeddingPort;
import java.util.ArrayList;
import java.util.List;

/**
 * 确定性向量化替身：单测不访问外部模型。
 */
public class FixedEmbeddingPort implements EmbeddingPort {

    private final int dimension;
    private boolean fail;

    /**
     * 构造替身。
     *
     * @param dimension 维度
     */
    public FixedEmbeddingPort(int dimension) {
        this.dimension = dimension;
    }

    /**
     * 让后续调用抛出异常，用于验证失败路径。
     */
    public void failAlways() {
        this.fail = true;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (fail) {
            throw com.crag.common.domain.BizException.of(
                    com.crag.common.api.ErrorCode.EMBEDDING_FAILED, "synthetic failure");
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                vector[i] = (text.hashCode() + i) % 97 / 97F;
            }
            vectors.add(vector);
        }
        return vectors;
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
