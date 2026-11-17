package com.crag.retrieval.config;

import com.crag.retrieval.application.port.RetrievalCachePort;
import com.crag.retrieval.domain.rrf.RrfFuser;
import com.crag.retrieval.infrastructure.cache.RedisRetrievalCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 检索模块装配。
 */
@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalModuleConfig {

    /**
     * RRF 融合器。
     *
     * @param properties 检索参数
     * @return 融合器
     */
    @Bean
    public RrfFuser rrfFuser(RetrievalProperties properties) {
        return new RrfFuser(properties.getRrfK());
    }

    /**
     * 检索缓存。
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     * @param properties    检索参数
     * @return 缓存实现
     */
    @Bean
    public RetrievalCachePort retrievalCachePort(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                                 RetrievalProperties properties) {
        return new RedisRetrievalCache(redisTemplate, objectMapper, properties);
    }

    /**
     * 检索线程池：两路召回并行执行，超时控制由调用方完成（规范 3.9、6.2）。
     *
     * @return 线程池
     */
    @Bean("retrievalExecutor")
    public Executor retrievalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("crag-retrieval-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
