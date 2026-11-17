package com.crag.agent.model;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 模型调用熔断器（规范 7.6：一分钟内失败 10 次即熔断五分钟，熔断期间整体降级为纯规则模式）。
 *
 * <p>为什么用滑动窗口而不是连续失败计数：间歇性失败（每几秒一次超时）同样会把延迟与成本放大，
 * 连续计数会被一次成功清零，永远触发不了熔断。</p>
 *
 * <p>线程安全性：所有状态读写都在同一把锁内，调用频率是“每次模型调用”，不构成热点。</p>
 */
public class ModelCircuitBreaker {

    private final int failureThreshold;
    private final long windowMillis;
    private final long openMillis;
    private final Clock clock;
    private final Deque<Long> failures = new ArrayDeque<>();
    private long openUntilMillis;

    /**
     * 构造熔断器。
     *
     * @param failureThreshold 窗口内失败阈值
     * @param windowMillis     统计窗口
     * @param openMillis       熔断持续时间
     * @param clock            统一时钟
     */
    public ModelCircuitBreaker(int failureThreshold, long windowMillis, long openMillis, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.windowMillis = windowMillis;
        this.openMillis = openMillis;
        this.clock = clock;
    }

    /**
     * 是否允许发起调用。
     *
     * @return 熔断中返回 false
     */
    public boolean allowRequest() {
        synchronized (this) {
            long now = clock.millis();
            if (openUntilMillis > now) {
                return false;
            }
            if (openUntilMillis != 0L && openUntilMillis <= now) {
                // 熔断到期：清空窗口重新统计，避免半开时立刻被旧失败打回熔断
                failures.clear();
                openUntilMillis = 0L;
            }
            return true;
        }
    }

    /**
     * 记录一次成功。
     */
    public synchronized void recordSuccess() {
        failures.clear();
    }

    /**
     * 记录一次失败，达到阈值则进入熔断。
     */
    public synchronized void recordFailure() {
        long now = clock.millis();
        failures.addLast(now);
        while (!failures.isEmpty() && now - failures.peekFirst() > windowMillis) {
            failures.removeFirst();
        }
        if (failures.size() >= failureThreshold) {
            openUntilMillis = now + openMillis;
            failures.clear();
        }
    }

    /**
     * 是否处于熔断状态，供报告标注。
     *
     * @return 熔断中返回 true
     */
    public synchronized boolean isOpen() {
        return openUntilMillis > clock.millis();
    }
}
