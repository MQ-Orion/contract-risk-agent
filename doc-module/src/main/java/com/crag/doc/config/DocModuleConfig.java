package com.crag.doc.config;

import com.crag.doc.application.port.ClauseTypeFallbackPort;
import com.crag.doc.application.port.ContractFileStorage;
import com.crag.doc.application.port.OcrService;
import com.crag.doc.application.port.ParseLockPort;
import com.crag.doc.application.port.ParseTaskDispatcher;
import com.crag.doc.application.ParsePipelineService;
import com.crag.doc.domain.validation.DocumentFormatDetector;
import com.crag.doc.domain.validation.TextQualityChecker;
import com.crag.doc.infrastructure.async.AsyncParseTaskDispatcher;
import com.crag.doc.infrastructure.lock.RedisParseLock;
import com.crag.doc.infrastructure.ocr.UnsupportedOcrService;
import com.crag.doc.infrastructure.storage.LocalContractFileStorage;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文档模块装配：切分参数、解析器、存储、锁与解析线程池。
 *
 * <p>所有阈值来自 {@link DocProperties}，不在业务代码中硬编码（规范 3.10）。</p>
 */
@Configuration
@EnableConfigurationProperties(DocProperties.class)
@MapperScan("com.crag.doc.infrastructure.persistence.mapper")
public class DocModuleConfig {

    /**
     * 文本质量校验器。
     *
     * @return 校验器
     */
    @Bean
    public TextQualityChecker textQualityChecker() {
        return new TextQualityChecker();
    }

    /**
     * 文件格式校验器。
     *
     * @return 校验器
     */
    @Bean
    public DocumentFormatDetector documentFormatDetector() {
        return new DocumentFormatDetector();
    }

    /**
     * 合同文件存储。
     *
     * @param properties 文档模块配置
     * @param clock      统一时钟
     * @return 存储实现
     */
    @Bean
    public ContractFileStorage contractFileStorage(DocProperties properties, Clock clock) {
        return new LocalContractFileStorage(Path.of(properties.getStorageRoot()), clock);
    }

    /**
     * 解析锁：默认 Redis，本地单实例可切换为内存实现。
     *
     * @param redisTemplate Redis 模板
     * @return 锁实现
     */
    @Bean
    @ConditionalOnProperty(name = "crag.doc.lock-mode", havingValue = "redis", matchIfMissing = true)
    public ParseLockPort redisParseLock(StringRedisTemplate redisTemplate) {
        return new RedisParseLock(redisTemplate);
    }

    /**
     * 内存解析锁，仅限本地单实例调试。
     *
     * @return 锁实现
     */
    @Bean
    @ConditionalOnProperty(name = "crag.doc.lock-mode", havingValue = "memory")
    public ParseLockPort inMemoryParseLock() {
        return new RedisParseLock.InMemory();
    }

    /**
     * 模型兜底分类端口占位；agent-module 接入模型后覆盖本 Bean（规范 5.3）。
     *
     * @return 兜底端口
     */
    @Bean
    @ConditionalOnMissingBean(ClauseTypeFallbackPort.class)
    public ClauseTypeFallbackPort clauseTypeFallbackPort() {
        return ClauseTypeFallbackPort.noop();
    }

    /**
     * OCR 占位实现。
     *
     * @return OCR 服务
     */
    @Bean
    public OcrService ocrService() {
        return new UnsupportedOcrService();
    }

    /**
     * 解析线程池：按用途分离，拒绝策略为调用者执行（规范 3.9）。
     *
     * @param properties 文档模块配置
     * @return 线程池
     */
    @Bean("parseExecutor")
    public ThreadPoolTaskExecutor parseExecutor(DocProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getParsePoolCoreSize());
        executor.setMaxPoolSize(properties.getParsePoolMaxSize());
        executor.setQueueCapacity(properties.getParsePoolQueueCapacity());
        executor.setThreadNamePrefix("crag-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 解析任务调度器。
     *
     * @param executor        解析线程池
     * @param pipelineService 解析流水线
     * @return 调度器
     */
    @Bean
    public ParseTaskDispatcher parseTaskDispatcher(
            @org.springframework.beans.factory.annotation.Qualifier("parseExecutor") ThreadPoolTaskExecutor executor,
            ParsePipelineService pipelineService) {
        return new AsyncParseTaskDispatcher(executor, pipelineService);
    }
}
