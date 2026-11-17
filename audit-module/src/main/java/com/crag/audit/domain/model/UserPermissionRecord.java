package com.crag.audit.domain.model;

/**
 * 用户权限记录（表 user_permission，规范 11.1）。
 *
 * @param tenantId 租户
 * @param userId   用户
 * @param role     角色
 * @param docScope 数据域
 */
public record UserPermissionRecord(long tenantId, long userId, UserRoleEnum role, DocScopeEnum docScope) {
}
