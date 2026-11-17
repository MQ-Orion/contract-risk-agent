package com.crag.audit.domain.model;

/**
 * 复核动作（规范 9.4：通过、驳回、待修改三类）。
 *
 * <p>驳回必须选择原因码；原因码是后续定位改进点的唯一入口，缺失就无法把误判回流到规则或检索。</p>
 */
public enum ReviewActionEnum {

    /** 通过。 */
    PASS,
    /** 驳回：必须带原因码。 */
    REJECT,
    /** 待修改：结论暂不采信，退回修改。 */
    NEED_FIX;

    /**
     * 是否必须提供驳回原因码。
     *
     * @return 驳回返回 true
     */
    public boolean requiresReasonCode() {
        return this == REJECT;
    }

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名
     * @return 枚举；未知返回 null
     */
    public static ReviewActionEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReviewActionEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
