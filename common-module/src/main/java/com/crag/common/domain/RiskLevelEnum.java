package com.crag.common.domain;

/**
 * 风险等级（规范 9.2 表 11）。
 *
 * <p>等级是结论口径而非展示字段：高/中风险必须人工复核，低风险可批量复核。
 * {@link #severity()} 数值越小越严重，用于排序与冲突时取更严结论。</p>
 */
public enum RiskLevelEnum {

    /** 高：可能导致重大经济损失或责任无限扩大，必须人工复核。 */
    HIGH(1, "高"),
    /** 中：与内部制度上限冲突或必备条款缺失，必须人工复核。 */
    MEDIUM(2, "中"),
    /** 低：表述瑕疵或可优化条款，可批量复核。 */
    LOW(3, "低");

    private final int severity;
    private final String label;

    RiskLevelEnum(int severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    /**
     * 严重度权重。
     *
     * @return 1 高、2 中、3 低
     */
    public int severity() {
        return severity;
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
     * 是否需要人工复核。
     *
     * @return 高、中返回 true
     */
    public boolean requiresManualReview() {
        return this != LOW;
    }

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名，忽略大小写
     * @return 枚举；未知值返回 null
     */
    public static RiskLevelEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RiskLevelEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
