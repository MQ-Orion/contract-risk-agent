package com.crag.kb.config;

import com.crag.kb.application.port.EmbeddingPort;
import com.crag.kb.application.port.KbSyncLockPort;
import com.crag.kb.infrastructure.embedding.DashScopeEmbeddingClient;
import com.crag.kb.infrastructure.lock.RedisKbSyncLock;
import java.util.concurrent.ThreadPoolExecutor;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 知识库模块装配：向量化客户端、同步锁、同步线程池与定时任务互斥。
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@EnableConfigurationProperties(EmbeddingProperties.class)
@MapperScan("com.crag.kb.infrastructure.persistence.mapper")
public class KbModuleConfig {

    /**
     * 向量化客户端；密钥缺失时启动失败，避免带着空密钥跑到线上（规范 7.7、11.5）。
     *
     * @param properties 配置
     * @return 向量化端口
     */
    @Bean
    public EmbeddingPort embeddingPort(EmbeddingProperties properties) {
        return new DashScopeEmbeddingClient(properties);
    }

    /**
     * 增量同步锁。
     *
     * @param redisTemplate Redis 模板
     * @return 锁实现
     */
    @Bean
    public KbSyncLockPort kbSyncLockPort(StringRedisTemplate redisTemplate) {
        return new RedisKbSyncLock(redisTemplate);
    }

    /**
     * 定时任务互斥锁（规范 3.9）：复用 Redis，不引入新的部署单元。
     *
     * @param connectionFactory Redis 连接工厂
     * @return ShedLock 锁提供者
     */
    @Bean
    public net.javacrumbs.shedlock.core.LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }

    /**
     * 知识库同步线程池：与解析池分离，拒绝策略为调用者执行（规范 3.9）。
     *
     * @return 线程池
     */
    @Bean("kbSyncExecutor")
    public ThreadPoolTaskExecutor kbSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("crag-kbsync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
