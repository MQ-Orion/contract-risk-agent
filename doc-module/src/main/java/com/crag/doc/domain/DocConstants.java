package com.crag.doc.domain;

import java.time.Duration;

/**
 * 文档解析与切分的硬性限制（规范 5.1、5.2、5.5）。
 *
 * <p>所有阈值集中在本类，禁止散落在业务代码；常量均注明单位。</p>
 */
public final class DocConstants {

    /** 单文件最大字节数：50 MB（规范 5.1）。 */
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;

    /** 单文件最大页数（规范 5.1）。 */
    public static final int MAX_PAGE_COUNT = 300;

    /** 解析失败最大重试次数（规范 5.5）。 */
    public static final int MAX_PARSE_RETRY = 3;

    /** 指数退避间隔，单位：分钟，依次对应第 1、2、3 次重试（规范 5.5）。 */
    public static final int[] RETRY_BACKOFF_MINUTES = {1, 4, 16};

    /** 解析锁有效期，单位：分钟；超时自动释放（规范 5.2）。 */
    public static final Duration PARSE_LOCK_TTL = Duration.ofMinutes(10);

    /** 解析锁键前缀（规范 5.2、5.5）。 */
    public static final String PARSE_LOCK_KEY_PREFIX = "lock:parse:";

    private DocConstants() {
    }

    /**
     * 指定重试次数对应的退避时间。
     *
     * @param retryIndex 已重试次数，从 0 开始
     * @return 退避时长
     */
    public static Duration backoff(int retryIndex) {
        int index = Math.min(Math.max(retryIndex, 0), RETRY_BACKOFF_MINUTES.length - 1);
        return Duration.ofMinutes(RETRY_BACKOFF_MINUTES[index]);
    }
}
