package com.crag.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索参数（规范 6.2–6.4、6.6）。
 */
@ConfigurationProperties(prefix = "crag.retrieval")
public class RetrievalProperties {

    /** 融合后送入上层的条数（规范 6.3：默认 6）。 */
    private int topK = 6;

    /** 单路召回条数（规范 6.2：前 20）。 */
    private int perPathLimit = 20;

    /** 单路超时毫秒数，超时降级为单路结果（规范 6.2：300ms）。 */
    private long timeoutMillis = 300L;

    /** 置信度阈值，低于该值降级为仅规则结论（规范 6.4：初值 0.35，需在开发集标定）。 */
    private double minScore = 0.35D;

    /** RRF 参数 k（规范 6.3：60）。 */
    private int rrfK = 60;

    /** 检索缓存有效期（分钟，规范 6.6：30 分钟）。 */
    private long cacheTtlMinutes = 30L;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getPerPathLimit() {
        return perPathLimit;
    }

    public void setPerPathLimit(int perPathLimit) {
        this.perPathLimit = perPathLimit;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public long getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(long cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }
}
