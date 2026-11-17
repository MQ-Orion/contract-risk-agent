package com.crag.kb.infrastructure.lock;

import com.crag.kb.application.port.KbSyncLockPort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 增量同步锁的 Redis 实现（规范 5.5：锁键 {@code lock:embedding:{docId}}）。
 */
public class RedisKbSyncLock implements KbSyncLockPort {

    private static final Logger log = LoggerFactory.getLogger(RedisKbSyncLock.class);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String instanceToken = "crag-kb-" + UUID.randomUUID();

    /**
     * 构造锁实现。
     *
     * @param redisTemplate Redis 模板
     */
    public RedisKbSyncLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, instanceToken, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void unlock(String lockKey) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), instanceToken);
        } catch (RuntimeException e) {
            log.warn("释放增量同步锁失败 lockKey={}", lockKey, e);
        }
    }
}
