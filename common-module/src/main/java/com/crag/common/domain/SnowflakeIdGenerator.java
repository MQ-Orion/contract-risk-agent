package com.crag.common.domain;

/**
 * 雪花标识生成器（规范 4.1：主键统一为长整型雪花标识）。
 *
 * <p>结构：1 位符号 + 41 位毫秒时间戳 + 10 位工作节点 + 12 位序列，单节点每毫秒 4096 个。
 * 时钟回拨不超过 5 毫秒时等待，超过则拒绝生成，避免产生重复标识。</p>
 *
 * <p>线程安全性：{@link #nextId()} 为 synchronized，可被多线程共享。</p>
 */
public final class SnowflakeIdGenerator {

    /** 纪元：2024-01-01T00:00:00Z。 */
    private static final long EPOCH_MILLIS = 1_704_067_200_000L;
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 允许的时钟回拨毫秒数。 */
    private static final long CLOCK_BACKWARD_TOLERANCE_MILLIS = 5L;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * 构造生成器。
     *
     * @param workerId 工作节点编号，取值 0 至 1023
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be in [0, " + MAX_WORKER_ID + "]");
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个标识。
     *
     * @return 雪花标识
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > CLOCK_BACKWARD_TOLERANCE_MILLIS) {
                throw new IllegalStateException("clock moved backwards by " + offset + "ms, refusing to generate id");
            }
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH_MILLIS) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitUntil(long targetMillis) {
        long timestamp = System.currentTimeMillis();
        while (timestamp < targetMillis) {
            Thread.onSpinWait();
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
