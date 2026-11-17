package com.crag.kb.domain.model;

/**
 * 一次增量同步的结果，用于日志与向量化指标（规范 16.2）。
 *
 * @param inserted 新增切片数
 * @param updated  重写向量数
 * @param skipped  哈希未变化而跳过的切片数
 * @param deleted  被删除的过期切片数
 */
public record SyncOutcome(int inserted, int updated, int skipped, int deleted) {

    /**
     * 总切片数。
     *
     * @return 本次同步涉及的切片总数
     */
    public int total() {
        return inserted + updated + skipped;
    }

    /**
     * 向量化跳过率（规范 16.2）。
     *
     * @return 跳过数除以总切片数；总数为 0 时返回 0
     */
    public double skipRatio() {
        int total = total();
        return total == 0 ? 0D : (double) skipped / total;
    }
}
