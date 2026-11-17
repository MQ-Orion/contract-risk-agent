package com.crag.retrieval.domain.model;

import com.crag.kb.domain.model.RetrievedChunk;
import java.util.List;

/**
 * 检索结果（规范 6.1）：对外结构固定，内部实现可替换而调用方不改代码。
 *
 * @param chunks         召回切片，已归一化分数并降序
 * @param degraded       是否发生降级（单路超时、低置信度、模型不可用等）
 * @param degradeReason  降级原因，供报告标注
 * @param fallback       是否发生条款类型回退
 * @param snapshotVersion 召回时的索引快照版本，供缓存一致性与回溯
 */
public record RetrievalResult(List<RetrievedChunk> chunks,
                              boolean degraded,
                              String degradeReason,
                              boolean fallback,
                              String snapshotVersion) {

    /**
     * 构造结果，切片不可变。
     *
     * @param chunks          切片
     * @param degraded        是否降级
     * @param degradeReason   降级原因
     * @param fallback        是否类型回退
     * @param snapshotVersion 快照版本
     */
    public RetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    /**
     * 空结果：上层必须输出“暂无相关风控依据”（规范 6.4）。
     *
     * @param snapshotVersion 快照版本
     * @return 空结果
     */
    public static RetrievalResult empty(String snapshotVersion) {
        return new RetrievalResult(List.of(), false, null, false, snapshotVersion);
    }

    /**
     * 是否为空结果。
     *
     * @return 无切片返回 true
     */
    public boolean isEmpty() {
        return chunks.isEmpty();
    }
}
