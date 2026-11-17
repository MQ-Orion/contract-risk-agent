package com.crag.doc.domain.split;

import com.crag.common.text.SplitConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定窗口切分器：仅用于切分策略对比实验（规范 12.2），不参与线上入库。
 *
 * <p>按固定字符窗口滑动，不考虑条款边界，因此会把一条完整条款切在两段——这正是本方案要对比的基线做法。</p>
 */
public final class FixedWindowSplitter {

    private final SplitConfig config;

    /**
     * 构造切分器。
     *
     * @param config 切分参数，复用条款级切分的窗口与重叠参数
     */
    public FixedWindowSplitter(SplitConfig config) {
        this.config = config;
    }

    /**
     * 切分。
     *
     * @param text 原文
     * @return 窗口列表
     */
    public List<Window> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int max = config.maxClauseLengthChars();
        int step = config.stepChars();
        List<Window> windows = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int end = Math.min(start + max, text.length());
            windows.add(new Window(index++, start, end, text.substring(start, end)));
            if (end >= text.length()) {
                break;
            }
            start += step;
        }
        return List.copyOf(windows);
    }

    /**
     * 固定窗口。
     *
     * @param index 序号
     * @param start 起始偏移（含）
     * @param end   结束偏移（不含）
     * @param text  文本
     */
    public record Window(int index, int start, int end, String text) {
    }
}
