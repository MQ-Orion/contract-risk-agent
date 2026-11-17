package com.crag.common.text;

/**
 * 切分参数（规范 5.3：阈值、窗口、正则集中配置，禁止散落在多个类中）。
 *
 * @param maxClauseLengthChars 单条条款最大字符数，超过则二次切分，默认 800
 * @param overlapRatio         二次切分窗口重叠比例，默认 0.10（即 10%）
 */
public record SplitConfig(int maxClauseLengthChars, double overlapRatio) {

    /** 默认单条条款最大字符数（单位：字符）。 */
    public static final int DEFAULT_MAX_CLAUSE_LENGTH_CHARS = 800;

    /** 默认窗口重叠比例（单位：比例，0.10 表示 10%）。 */
    public static final double DEFAULT_OVERLAP_RATIO = 0.10D;

    /**
     * 校验参数合法性。
     *
     * @param maxClauseLengthChars 最大字符数
     * @param overlapRatio         重叠比例
     */
    public SplitConfig {
        if (maxClauseLengthChars < 100 || maxClauseLengthChars > 10_000) {
            throw new IllegalArgumentException("maxClauseLengthChars must be in [100, 10000]");
        }
        if (overlapRatio < 0D || overlapRatio >= 0.5D) {
            throw new IllegalArgumentException("overlapRatio must be in [0, 0.5)");
        }
    }

    /**
     * 默认配置。
     *
     * @return 800 字符窗口、10% 重叠
     */
    public static SplitConfig defaults() {
        return new SplitConfig(DEFAULT_MAX_CLAUSE_LENGTH_CHARS, DEFAULT_OVERLAP_RATIO);
    }

    /**
     * 窗口步长（字符）。
     *
     * @return 步长
     */
    public int stepChars() {
        return Math.max(1, (int) Math.round(maxClauseLengthChars * (1D - overlapRatio)));
    }

    /**
     * 重叠字符数。
     *
     * @return 重叠长度
     */
    public int overlapChars() {
        return maxClauseLengthChars - stepChars();
    }
}
