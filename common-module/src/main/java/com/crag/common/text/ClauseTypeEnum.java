package com.crag.common.text;

/**
 * 条款类型：固定六类枚举（规范 5.3）。新增类型必须同时更新规则配置与检索映射。
 *
 * <p>枚举以 code 存库、中文仅用于展示（规范 3.4、4.1）。</p>
 */
public enum ClauseTypeEnum {

    /** 违约责任。 */
    BREACH("违约责任"),
    /** 保密。 */
    CONFIDENTIALITY("保密"),
    /** 付款。 */
    PAYMENT("付款"),
    /** 管辖。 */
    JURISDICTION("管辖"),
    /** 免责。 */
    DISCLAIMER("免责"),
    /** 其他；未命中任何关键词时的兜底类型，可被模型兜底分类修正。 */
    OTHER("其他");

    private final String label;

    ClauseTypeEnum(String label) {
        this.label = label;
    }

    /**
     * 中文展示名。
     *
     * @return 展示名
     */
    public String label() {
        return label;
    }

    /**
     * 由存库 code 还原枚举。
     *
     * @param code 枚举名
     * @return 枚举；未知值返回 {@link #OTHER}
     */
    public static ClauseTypeEnum fromCode(String code) {
        for (ClauseTypeEnum value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return OTHER;
    }
}
