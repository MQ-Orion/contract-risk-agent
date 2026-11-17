package com.crag.doc.infrastructure.lock;

import com.crag.doc.application.port.ParseLockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 的解析锁（规范 5.2、5.5）：SET NX PX 获取，Lua 比对持有者后释放。
 *
 * <p>释放必须比对持有者，避免超时后误删他人的锁；锁值使用实例级令牌，足够区分同键的并发持有者。</p>
 *
 * <p>为什么用 SET NX PX 而不是“先 get 再 set”：两条命令之间存在竞态，两个实例可能同时认为自己拿到了锁。
 * 为什么必须带 TTL：进程崩溃时锁不会永久残留，下一个解析任务仍能继续（规范 5.2）。
 * 代价是 TTL 到期后可能被他人获取，因此释放用 Lua 比对令牌，不能无条件 DEL。</p>
 */
public class RedisParseLock implements ParseLockPort {

    private static final Logger log = LoggerFactory.getLogger(RedisParseLock.class);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String instanceToken = "crag-" + UUID.randomUUID();

    /**
     * 构造锁实现。
     *
     * @param redisTemplate Redis 模板
     */
    public RedisParseLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, instanceToken, ttl);
        boolean ok = Boolean.TRUE.equals(acquired);
        if (!ok) {
            log.debug("锁已存在 lockKey={}", lockKey);
        }
        return ok;
    }

    @Override
    public void unlock(String lockKey) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, java.util.List.of(lockKey), instanceToken);
        } catch (RuntimeException e) {
            log.warn("释放锁失败 lockKey={}", lockKey, e);
        }
    }

    /**
     * 单机内存锁：仅用于单元测试与本地无 Redis 场景，不可用于多实例部署。
     */
    public static class InMemory implements ParseLockPort {

        private final Map<String, Instant> held = new ConcurrentHashMap<>();

        @Override
        public boolean tryLock(String lockKey, Duration ttl) {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(ttl);
            Instant previous = held.putIfAbsent(lockKey, expiresAt);
            if (previous == null) {
                return true;
            }
            if (previous.isBefore(now)) {
                return held.replace(lockKey, previous, expiresAt);
            }
            return false;
        }

        @Override
        public void unlock(String lockKey) {
            held.remove(lockKey);
        }
    }
}
