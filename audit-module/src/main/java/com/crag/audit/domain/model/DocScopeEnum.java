package com.crag.audit.domain.model;

/**
 * 数据域（规范 11.1：数据域决定可访问的租户与文档范围）。
 */
public enum DocScopeEnum {

    /** 全租户可见（平台管理员）。 */
    ALL,
    /** 本租户可见。 */
    TENANT,
    /** 仅本人上传的合同。 */
    OWN,
    /** 不可见。 */
    NONE;

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名
     * @return 枚举；未知返回 {@link #NONE}
     */
    public static DocScopeEnum fromCode(String code) {
        if (code == null) {
            return NONE;
        }
        for (DocScopeEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return NONE;
    }

    /**
     * 是否允许查看合同。
     *
     * @param uploaderId 合同上传人
     * @param operatorId 当前操作人
     * @return 允许返回 true
     */
    public boolean allowsContract(long uploaderId, long operatorId) {
        return switch (this) {
            case ALL, TENANT -> true;
            case OWN -> uploaderId == operatorId;
            case NONE -> false;
        };
    }
}
