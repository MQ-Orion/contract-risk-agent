package com.crag.kb.application.port;

import java.time.Duration;

/**
 * 增量同步锁端口（规范 5.5：锁键按用途分离，增量同步锁为 {@code lock:embedding:{docId}}）。
 */
public interface KbSyncLockPort {

    /**
     * 尝试获取锁。
     *
     * @param lockKey 锁键
     * @param ttl     有效期
     * @return 成功返回 true
     */
    boolean tryLock(String lockKey, Duration ttl);

    /**
     * 释放锁。
     *
     * @param lockKey 锁键
     */
    void unlock(String lockKey);
}
