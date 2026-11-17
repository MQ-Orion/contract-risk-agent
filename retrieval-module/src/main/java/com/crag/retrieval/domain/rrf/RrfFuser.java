package com.crag.retrieval.domain.rrf;

import com.crag.kb.domain.model.RetrievedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF 融合（规范 6.3）：按名次而非分数融合，规避两路分数量纲不一致。
 *
 * <p>rrf_raw(d) = Σ 1 / (k + rank_i(d))，去重键为切片标识，同一 chunk 在两路出现时分数相加；
 * 融合后再按本次结果集最大值归一化，归一化只在检索模块内做一次。</p>
 *
 * <p>线程安全性：无状态，可共享。</p>
 *
 * <p>为什么按 chunkId 去重而不是按文本：同一文本可能来自不同文档（制度与判例），合并会丢掉来源文档，
 * 溯源时无法指回原始出处（规范 9.3）。为什么名次从 1 开始：1/(k+0) 会让首位权重异常突出，标准 RRF 取 1 起始。</p>
 */
public final class RrfFuser {

    private final int k;

    /**
     * 构造融合器。
     *
     * @param k RRF 参数，规范取 60
     */
    public RrfFuser(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("rrf k must be positive");
        }
        this.k = k;
    }

    /**
     * 融合两路召回结果。
     *
     * @param vectorResults  向量路结果（已按相似度降序）
     * @param keywordResults 关键词路结果（已按相关度降序）
     * @return 融合并按最大值归一化后的结果，按分数降序
     */
    public List<RetrievedChunk> fuse(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults) {
        Map<Long, Double> rawScores = new LinkedHashMap<>();
        Map<Long, RetrievedChunk> byId = new LinkedHashMap<>();
        accumulate(vectorResults, rawScores, byId);
        accumulate(keywordResults, rawScores, byId);
        if (rawScores.isEmpty()) {
            return List.of();
        }
        double max = rawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        List<RetrievedChunk> fused = new ArrayList<>(rawScores.size());
        for (Map.Entry<Long, Double> entry : rawScores.entrySet()) {
            RetrievedChunk chunk = byId.get(entry.getKey());
            double normalized = max <= 0D ? 0D : entry.getValue() / max;
            fused.add(chunk.withScore(normalized));
        }
        fused.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return List.copyOf(fused);
    }

    private void accumulate(List<RetrievedChunk> results, Map<Long, Double> rawScores,
                            Map<Long, RetrievedChunk> byId) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            RetrievedChunk chunk = results.get(i);
            int rank = i + 1;
            rawScores.merge(chunk.chunkId(), 1D / (k + rank), Double::sum);
            byId.putIfAbsent(chunk.chunkId(), chunk);
        }
    }
}
