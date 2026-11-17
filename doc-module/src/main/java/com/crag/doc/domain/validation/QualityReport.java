package com.crag.doc.domain.validation;

/**
 * 文本质量校验（规范 5.2）：文本为空、或连续乱码字符占比超过 5%，判定为解析失败。
 *
 * @param acceptable          是否可接受
 * @param suspiciousCharRatio 可疑字符占比
 * @param reason              判定原因，用于日志
 */
public record QualityReport(boolean acceptable, double suspiciousCharRatio, String reason) {

    /**
     * 通过。
     *
     * @param ratio 可疑字符占比
     * @return 报告
     */
    public static QualityReport pass(double ratio) {
        return new QualityReport(true, ratio, "ok");
    }

    /**
     * 不通过。
     *
     * @param ratio  可疑字符占比
     * @param reason 原因
     * @return 报告
     */
    public static QualityReport fail(double ratio, String reason) {
        return new QualityReport(false, ratio, reason);
    }
}
