package com.crag.audit.domain.model;

/**
 * 用户角色（规范 11.1：角色决定可执行的动作）。
 *
 * <p>角色只回答“能不能做”，数据域回答“能看谁的数据”，两者必须同时校验。</p>
 */
public enum UserRoleEnum {

    /** 管理员：规则配置、权限变更、全部审核动作。 */
    ADMIN,
    /** 审核员：发起审核、查看报告、提交复核结论。 */
    AUDITOR,
    /** 复核员：复核风险项。 */
    REVIEWER,
    /** 只读：仅查看。 */
    VIEWER;

    /**
     * 是否允许管理规则与权限。
     *
     * @return ADMIN 返回 true
     */
    public boolean canManageConfig() {
        return this == ADMIN;
    }

    /**
     * 是否允许发起审核。
     *
     * @return ADMIN 或 AUDITOR
     */
    public boolean canStartAudit() {
        return this == ADMIN || this == AUDITOR;
    }

    /**
     * 是否允许复核。
     *
     * @return ADMIN 或 REVIEWER
     */
    public boolean canReview() {
        return this == ADMIN || this == REVIEWER;
    }

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名
     * @return 枚举；未知返回 null
     */
    public static UserRoleEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (UserRoleEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
