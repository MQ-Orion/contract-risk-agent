package com.crag.common.security;

/**
 * 权限端口（规范 11.1：角色决定动作，数据域决定可见范围）。
 *
 * <p>接口放 common-module，是为了让 doc / kb / agent / audit 都能在不反向依赖 audit 的前提下做校验；
 * 实现与 `user_permission` 表由 audit-module 提供（audit → common 允许）。</p>
 *
 * <p>默认拒绝：没有权限记录的用户一律拒绝（规范 10.3「所有接口默认拒绝」）。
 * 角色不足返回 10003（无权限）；资源级越权统一返回 10004（资源不存在），禁止暴露资源真实归属（规范 4.4）。</p>
 */
public interface PermissionPort {

    /**
     * 要求具备配置管理权限（规则、权限）。
     *
     * @param userId 操作人
     */
    void requireManageConfig(long userId);

    /**
     * 要求具备发起审核权限。
     *
     * @param userId 操作人
     */
    void requireStartAudit(long userId);

    /**
     * 要求具备复核权限。
     *
     * @param userId 操作人
     */
    void requireReview(long userId);

    /**
     * 要求对指定合同具备数据域访问权。
     *
     * @param userId     操作人
     * @param uploaderId 合同上传人
     */
    void requireContractAccess(long userId, long uploaderId);
}
