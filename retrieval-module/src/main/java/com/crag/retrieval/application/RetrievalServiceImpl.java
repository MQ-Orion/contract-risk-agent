package com.crag.retrieval.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import com.crag.kb.application.port.EmbeddingPort;
import com.crag.kb.application.port.KbChunkReadPort;
import com.crag.kb.domain.model.RetrievedChunk;
import com.crag.retrieval.application.port.RetrievalCachePort;
import com.crag.retrieval.config.RetrievalProperties;
import com.crag.retrieval.domain.model.RetrievalResult;
import com.crag.retrieval.domain.rrf.RrfFuser;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 检索实现（规范 6.1–6.4、6.6）：双路并行召回 → 单路超时降级 → 类型回退一次 → RRF 融合归一化 → 置信度判定 → 缓存。
 *
 * <p>并发与超时：两路各取前 20，并行执行，单路超过 300ms 即丢弃该路结果并标记降级原因（规范 6.2）。
 * 置信度：取向量路首位余弦相似度作为“最高分”，低于阈值时降级为仅规则结论（规范 6.4）。
 * 说明：RRF 归一化后首位恒为 1，直接以归一化分与 0.35 比较永远不成立，因此阈值作用于向量路原始相似度，
 * 该解释写入 ADR 0004 与评估报告。</p>
 *
 * <p>调用链：{@code RiskRetrievalTool} → 本方法 → 缓存查询 → {@code attempt} → 嵌入接口 + {@code KbChunkReadPort}
 * 两路并行 → {@code RrfFuser.fuse} → 截断 top-k → 缓存写入。本方法无事务、不写库。</p>
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    private final KbChunkReadPort readPort;
    private final EmbeddingPort embeddingPort;
    private final RetrievalCachePort cachePort;
    private final RrfFuser fuser;
    private final RetrievalProperties properties;
    private final Executor executor;

    /**
     * 构造检索服务。
     *
     * @param readPort      知识库切片只读端口
     * @param embeddingPort 向量化端口（仅内部使用，不向上暴露）
     * @param cachePort     检索缓存
     * @param fuser         RRF 融合器
     * @param properties    检索参数
     * @param executor      检索线程池
     */
    public RetrievalServiceImpl(KbChunkReadPort readPort,
                                EmbeddingPort embeddingPort,
                                RetrievalCachePort cachePort,
                                RrfFuser fuser,
                                RetrievalProperties properties,
                                @Qualifier("retrievalExecutor") Executor executor) {
        this.readPort = readPort;
        this.embeddingPort = embeddingPort;
        this.cachePort = cachePort;
        this.fuser = fuser;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public RetrievalResult retrieve(String query, String clauseType, long tenantId) {
        // 检索是唯一出口，也是跨租户越权的高风险入口：租户必须与上下文一致，
        // 不一致一律按“资源不存在”处理，避免用状态码差异探测他人租户（规范 4.4、11.1）
        if (TenantContext.require() != tenantId) {
            throw com.crag.common.domain.BizException.of(com.crag.common.api.ErrorCode.RESOURCE_NOT_FOUND,
                    "tenant mismatch");
        }
        if (query == null || query.isBlank()) {
            return RetrievalResult.empty(cachePort.indexVersion(tenantId));
        }
        String version = cachePort.indexVersion(tenantId);
        RetrievalResult cached = cachePort.get(tenantId, clauseType, query, version);
        if (cached != null) {
            return cached;
        }
        Attempt attempt = attempt(query, clauseType, tenantId);
        boolean fallback = false;
        // 规范 6.4：类型回退只做一次。回退不是“多查一次兜底”，而是承认分类可能错；
        // 无限回退会把空结果也变成一次全类型检索，延迟与噪声同时上升。
        if (attempt.fused().isEmpty() && clauseType != null && !ClauseTypes.OTHER.equals(clauseType)) {
            attempt = attempt(query, ClauseTypes.OTHER, tenantId);
            fallback = true;
            log.info("条款类型回退为 OTHER tenantId={} clauseType={}", tenantId, clauseType);
        }
        List<RetrievedChunk> top = attempt.fused().stream().limit(properties.getTopK()).toList();
        boolean degraded = attempt.degraded();
        String reason = attempt.degradeReason();
        if (!top.isEmpty() && attempt.confidence() != null && attempt.confidence() < properties.getMinScore()) {
            degraded = true;
            reason = "low_confidence";
        }
        RetrievalResult result = new RetrievalResult(top, degraded, reason, fallback, version);
        // 空结果不写缓存：空结果常出现在知识库尚未同步完成时，缓存后即使索引随后就绪也会继续返回空，
        // 直到 TTL 到期或版本变化，用户会看到“明明有文档却检索不到”（规范 6.6）。
        if (!result.isEmpty()) {
            cachePort.put(tenantId, clauseType, query, version, result);
        }
        log.info("检索完成 tenantId={} clauseType={} chunks={} degraded={} fallback={} reason={}",
                tenantId, clauseType, top.size(), degraded, fallback, reason);
        return result;
    }

    private Attempt attempt(String query, String clauseType, long tenantId) {
        CompletableFuture<List<RetrievedChunk>> vectorFuture = CompletableFuture.completedFuture(List.of());
        Double confidence = null;
        boolean degraded = false;
        String reason = null;
        float[] queryVector;
        try {
            queryVector = embeddingPort.embed(List.of(query)).get(0);
        } catch (RuntimeException e) {
            log.warn("查询向量化失败，降级为关键词单路: {}", e.getMessage());
            vectorFuture = CompletableFuture.completedFuture(List.of());
            queryVector = null;
            degraded = true;
            reason = "embedding_unavailable";
        }
        if (queryVector != null) {
            String literal = toVectorLiteral(queryVector);
            vectorFuture = CompletableFuture.supplyAsync(
                    () -> readPort.vectorSearch(literal, clauseType, tenantId, properties.getPerPathLimit()), executor);
        }
        CompletableFuture<List<RetrievedChunk>> keywordFuture = CompletableFuture.supplyAsync(
                () -> readPort.keywordSearch(query, clauseType, tenantId, properties.getPerPathLimit()), executor);

        List<RetrievedChunk> vectorResults = await(vectorFuture, "vector");
        List<RetrievedChunk> keywordResults = await(keywordFuture, "keyword");
        if (vectorResults == null) {
            degraded = true;
            reason = reason == null ? "vector_timeout" : reason;
            vectorResults = List.of();
        }
        if (keywordResults == null) {
            degraded = true;
            reason = reason == null ? "keyword_timeout" : reason;
            keywordResults = List.of();
        }
        if (!vectorResults.isEmpty()) {
            confidence = vectorResults.get(0).score();
        }
        List<RetrievedChunk> fused = fuser.fuse(vectorResults, keywordResults);
        return new Attempt(fused, degraded, reason, confidence);
    }

    private List<RetrievedChunk> await(CompletableFuture<List<RetrievedChunk>> future, String path) {
        try {
            return future.get(properties.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("召回超时 path={} timeout={}ms", path, properties.getTimeoutMillis());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.warn("召回失败 path={}", path, e.getCause());
            return null;
        }
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    /**
     * 单次召回尝试的结果。
     *
     * @param fused         融合后的切片（已归一化）
     * @param degraded      是否降级
     * @param degradeReason 降级原因
     * @param confidence    置信度（向量路首位相似度），无向量路时为 null
     */
    private record Attempt(List<RetrievedChunk> fused, boolean degraded, String degradeReason, Double confidence) {
    }

    /**
     * 条款类型常量，避免与字符串字面量散落。
     */
    private static final class ClauseTypes {
        private static final String OTHER = "OTHER";

        private ClauseTypes() {
        }
    }
}
