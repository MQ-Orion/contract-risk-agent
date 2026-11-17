package com.crag.kb.infrastructure.embedding;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.kb.application.port.EmbeddingPort;
import com.crag.kb.config.EmbeddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI 兼容协议的向量化客户端（规范 7.7）。
 *
 * <p>只在“变化的切片”上调用，未变化切片由上游跳过（规范 4.3），因此调用量随变更量而非文档量增长。
 * 失败按规范 7.6 重试两次（1s、3s），仍失败则抛业务异常，由同步状态机标记为失败。</p>
 */
public class DashScopeEmbeddingClient implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmbeddingProperties properties;
    private final HttpClient httpClient;

    /**
     * 构造客户端。
     *
     * @param properties 配置
     */
    public DashScopeEmbeddingClient(EmbeddingProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "未配置 crag.embedding.api-key，请通过环境变量 DASHSCOPE_API_KEY 注入（规范 11.5）");
        }
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, properties.getBatchSize());
        for (int from = 0; from < texts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, texts.size());
            vectors.addAll(embedBatch(texts.subList(from, to)));
        }
        return vectors;
    }

    @Override
    public int dimension() {
        return properties.getDimension();
    }

    private List<float[]> embedBatch(List<String> batch) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.getRetryTimes(); attempt++) {
            try {
                return callOnce(batch);
            } catch (RuntimeException e) {
                last = e;
                if (attempt < properties.getRetryTimes()) {
                    long backoffMillis = 1000L * (1L << attempt);
                    log.warn("向量化调用失败，{}ms 后重试 attempt={} size={}", backoffMillis, attempt + 1, batch.size());
                    sleep(backoffMillis);
                }
            }
        }
        log.error("向量化最终失败 size={}", batch.size(), last);
        throw BizException.of(ErrorCode.EMBEDDING_FAILED, "embedding failed: " + (last == null ? "" : last.getMessage()));
    }

    private List<float[]> callOnce(List<String> batch) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", properties.getModel());
        ArrayNode input = body.putArray("input");
        batch.forEach(input::add);
        String payload;
        try {
            payload = MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.EMBEDDING_FAILED, "serialize request failed");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(properties.getBaseUrl()) + "/embeddings"))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw BizException.of(ErrorCode.EMBEDDING_FAILED,
                        "http " + response.statusCode() + ": " + truncate(response.body()));
            }
            return parse(response.body(), batch.size());
        } catch (IOException e) {
            throw BizException.of(ErrorCode.EMBEDDING_FAILED, "io: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(ErrorCode.EMBEDDING_FAILED, "interrupted");
        }
    }

    private List<float[]> parse(String body, int expected) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != expected) {
                throw BizException.of(ErrorCode.EMBEDDING_FAILED, "unexpected data size");
            }
            List<float[]> vectors = new ArrayList<>(expected);
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                if (embedding.size() != properties.getDimension()) {
                    throw BizException.of(ErrorCode.EMBEDDING_FAILED,
                            "dimension mismatch: expected " + properties.getDimension() + " got " + embedding.size());
                }
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                vectors.add(vector);
            }
            JsonNode usage = root.path("usage");
            log.info("向量化完成 size={} tokens={}", expected, usage.path("total_tokens").asInt(0));
            return vectors;
        } catch (IOException e) {
            throw BizException.of(ErrorCode.EMBEDDING_FAILED, "parse response failed");
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
