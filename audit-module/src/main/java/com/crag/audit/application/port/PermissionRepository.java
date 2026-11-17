package com.crag.audit.application.port;

import com.crag.audit.domain.model.UserPermissionRecord;
import java.util.Optional;

/**
 * 权限仓储端口（表 user_permission，归属 audit-module）。
 */
public interface PermissionRepository {

    /**
     * 查询用户权限。
     *
     * @param tenantId 租户
     * @param userId   用户
     * @return 权限记录
     */
    Optional<UserPermissionRecord> find(long tenantId, long userId);
}
