package com.crag.audit.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crag.audit.application.port.PermissionRepository;
import com.crag.audit.domain.model.DocScopeEnum;
import com.crag.audit.domain.model.UserPermissionRecord;
import com.crag.audit.domain.model.UserRoleEnum;
import com.crag.audit.infrastructure.persistence.entity.UserPermissionEntity;
import com.crag.audit.infrastructure.persistence.mapper.UserPermissionMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 权限仓储实现：租户 + 用户唯一定位，租户条件由拦截器与显式条件双重保障（规范 4.4）。
 */
@Repository
public class MybatisPermissionRepository implements PermissionRepository {

    private final UserPermissionMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 权限 Mapper
     */
    public MybatisPermissionRepository(UserPermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserPermissionRecord> find(long tenantId, long userId) {
        UserPermissionEntity entity = mapper.selectOne(Wrappers.<UserPermissionEntity>query()
                .eq("tenant_id", tenantId).eq("user_id", userId).eq("deleted", 0).last("limit 1"));
        if (entity == null) {
            return Optional.empty();
        }
        UserRoleEnum role = UserRoleEnum.fromCode(entity.role);
        if (role == null) {
            return Optional.empty();
        }
        return Optional.of(new UserPermissionRecord(tenantId, userId, role, DocScopeEnum.fromCode(entity.docScope)));
    }
}
