package com.crag.kb.support;

import com.crag.kb.application.port.KbSyncLockPort;
import java.time.Duration;

/**
 * 同步锁替身：单测默认放行。
 */
public class AlwaysFreeLock implements KbSyncLockPort {

    @Override
    public boolean tryLock(String lockKey, Duration ttl) {
        return true;
    }

    @Override
    public void unlock(String lockKey) {
        // no-op
    }
}
