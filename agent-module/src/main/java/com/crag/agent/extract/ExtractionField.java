package com.crag.agent.extract;

/**
 * 单个抽取字段的值与原文片段（规范 7.4：抽取字段必须带原文片段）。
 *
 * @param name      字段名
 * @param value     值，缺失为 null
 * @param span      原文片段，缺失为 null
 * @param verified  回验是否通过
 * @param reason    未通过原因
 */
public record ExtractionField(String name, Object value, String span, boolean verified, String reason) {

    /**
     * 是否可用于业务判断：有值且回验通过。
     *
     * @return 可用返回 true
     */
    public boolean usable() {
        return value != null && verified;
    }

    /**
     * 是否待人工确认：有值但无片段或回验失败。
     *
     * @return 待确认返回 true
     */
    public boolean pendingManual() {
        return value != null && !verified;
    }
}
