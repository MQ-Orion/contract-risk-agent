package com.crag.doc.application.port;

import java.time.Duration;

/**
 * 分布式锁端口（规范 5.2、5.5：同一资源并发处理必须加锁，锁键按用途分离）。
 */
public interface ParseLockPort {

    /**
     * 尝试获取锁。
     *
     * @param lockKey 锁键
     * @param ttl     有效期，到期自动释放
     * @return 获取成功返回 true
     */
    boolean tryLock(String lockKey, Duration ttl);

    /**
     * 释放锁。
     *
     * @param lockKey 锁键
     */
    void unlock(String lockKey);
}
