package com.crag.agent.model;

import com.crag.common.domain.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI 兼容协议的对话模型客户端（规范 7.6、7.7）。
 *
 * <p>工程约束逐条落地：连接 5 秒、读取 60 秒；失败重试 2 次（退避 1 秒、3 秒）；并发上限 8，超出排队，
 * 队列满直接降级；1 分钟内失败 10 次熔断 5 分钟，熔断期间整体降级为纯规则模式。</p>
 *
 * <p>为什么所有失败都返回 {@link ChatResponse} 而不是抛异常：编排层需要把“模型不可用”变成报告上的
 * “模型结论不可用”标注并继续产出规则结论（规范 7.6）。异常会把整份审核打断，反而不符合降级要求。</p>
 *
 * <p>调用日志：每次调用（含降级）都写一条 {@code model_call_log}；写日志失败只告警，不影响审核结论。</p>
 *
 * <p>线程安全性：HTTP 客户端与线程池均可共享；熔断器内部加锁。</p>
 */
public class OpenAiCompatibleChatModelClient implements ChatModelClient, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatModelClient.class);

    private final ChatModelProperties properties;
    private final ObjectMapper objectMapper;
    private final ModelCircuitBreaker breaker;
    private final ModelCallLogRepository logRepository;
    private final Clock clock;
    private final HttpClient httpClient;
    private final ThreadPoolExecutor executor;
    private final long requestTimeoutMillis;

    /**
     * 构造客户端。
     *
     * @param properties   模型配置
     * @param objectMapper JSON 序列化
     * @param logRepository 调用日志仓储
     * @param clock        统一时钟
     */
    public OpenAiCompatibleChatModelClient(ChatModelProperties properties, ObjectMapper objectMapper,
                                           ModelCallLogRepository logRepository, Clock clock) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "未配置 crag.model.api-key，请通过环境变量 DASHSCOPE_API_KEY 注入（规范 11.5）");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
        this.clock = clock;
        this.breaker = new ModelCircuitBreaker(properties.getFailureThreshold(),
                properties.getFailureWindowMillis(), properties.getOpenMillis(), clock);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
        this.executor = new ThreadPoolExecutor(properties.getConcurrency(), properties.getConcurrency(),
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "crag-model-call");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        // 队列已满时不再等待：等待只会把延迟叠到调用方，规范 7.6 要求“队列满直接降级”
        this.executor.prestartAllCoreThreads();
        this.requestTimeoutMillis = properties.getConnectTimeoutMillis() + properties.getReadTimeoutMillis();
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        long startedAt = clock.millis();
        if (!breaker.allowRequest()) {
            ChatResponse response = ChatResponse.degraded(properties.getModel(), "circuit_open", 0L);
            record(request, response, startedAt);
            return response;
        }
        Future<ChatResponse> future;
        try {
            future = executor.submit(() -> callWithRetry(request, startedAt));
        } catch (RejectedExecutionException e) {
            ChatResponse response = ChatResponse.degraded(properties.getModel(), "queue_full", 0L);
            record(request, response, startedAt);
            return response;
        }
        ChatResponse response;
        try {
            response = future.get(requestTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            breaker.recordFailure();
            response = ChatResponse.degraded(properties.getModel(), "call_timeout", clock.millis() - startedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            response = ChatResponse.degraded(properties.getModel(), "interrupted", clock.millis() - startedAt);
        } catch (ExecutionException e) {
            response = ChatResponse.degraded(properties.getModel(), "call_failed", clock.millis() - startedAt);
            log.warn("模型调用异常 model={} reason={}", properties.getModel(), e.getCause() == null
                    ? e.getMessage() : e.getCause().getMessage());
        }
        record(request, response, startedAt);
        return response;
    }

    private ChatResponse callWithRetry(ChatRequest request, long startedAt) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.getRetryTimes(); attempt++) {
            try {
                ChatResponse response = callOnce(request, startedAt);
                breaker.recordSuccess();
                return response;
            } catch (RuntimeException e) {
                last = e;
                breaker.recordFailure();
                if (attempt < properties.getRetryTimes()) {
                    long backoff = attempt == 0 ? 1000L : 3000L;
                    log.warn("模型调用失败，{}ms 后重试 attempt={} reason={}", backoff, attempt + 1, e.getMessage());
                    sleep(backoff);
                }
            }
        }
        return ChatResponse.degraded(properties.getModel(),
                "retry_exhausted:" + (last == null ? "unknown" : last.getMessage()), clock.millis() - startedAt);
    }

    private ChatResponse callOnce(ChatRequest request, long startedAt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("temperature", properties.getTemperature());
        body.put("top_p", properties.getTopP());
        body.put("max_tokens", request.maxTokens() > 0 ? request.maxTokens() : properties.getMaxOutputTokens());
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        messages.addObject().put("role", "user").put("content", request.userPrompt());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("serialize request failed");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(properties.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("io: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("http " + response.statusCode());
        }
        return parse(response.body(), startedAt);
    }

    private ChatResponse parse(String body, long startedAt) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            String model = root.path("model").asText(properties.getModel());
            long latency = clock.millis() - startedAt;
            if (content.isBlank()) {
                return new ChatResponse("", model, properties.effectiveModelVersion(), promptTokens,
                        completionTokens, latency, true, "empty_output", java.util.List.of());
            }
            return new ChatResponse(content, model, properties.effectiveModelVersion(), promptTokens,
                    completionTokens, latency, false, null, java.util.List.of());
        } catch (IOException e) {
            throw new IllegalStateException("parse response failed");
        }
    }

    private void record(ChatRequest request, ChatResponse response, long startedAt) {
        try {
            logRepository.append(new ModelCallLog(0L, request.tenantId(), request.contractId(), request.clauseNo(),
                    request.purpose(), response.model(), response.modelVersion(), request.promptVersion(),
                    response.promptTokens(), response.completionTokens(), response.totalTokens(),
                    response.latencyMillis(), response.degraded(), truncate(response.degradeReason()),
                    TraceContext.current(), clock.instant()));
        } catch (RuntimeException e) {
            log.warn("模型调用日志写入失败 purpose={} reason={}", request.purpose(), e.getMessage());
        }
    }

    /**
     * 当前熔断状态，供报告标注。
     *
     * @return 熔断中返回 true
     */
    public boolean circuitOpen() {
        return breaker.isOpen();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 64 ? text.substring(0, 64) : text;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
