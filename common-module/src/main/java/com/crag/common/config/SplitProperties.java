package com.crag.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 条款切分参数（规范 5.3、5.4：参数集中配置，知识库与合同共用同一份配置）。
 */
@ConfigurationProperties(prefix = "crag.split")
public class SplitProperties {

    /** 单条条款最大字符数，超过触发二次切分（单位：字符）。 */
    private int maxChars = 800;

    /** 二次切分窗口重叠比例（单位：比例，0.10 表示 10%）。 */
    private double overlapRatio = 0.10D;

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public double getOverlapRatio() {
        return overlapRatio;
    }

    public void setOverlapRatio(double overlapRatio) {
        this.overlapRatio = overlapRatio;
    }
}
