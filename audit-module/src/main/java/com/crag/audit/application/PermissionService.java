package com.crag.audit.application;

import com.crag.audit.application.port.PermissionRepository;
import com.crag.audit.domain.model.UserPermissionRecord;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import com.crag.common.security.PermissionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 权限实现（规范 11.1、10.3）：角色 + 数据域双层校验。
 *
 * <p>调用链：各模块 Controller → {@link PermissionPort} 方法 → 本类 → 权限仓储（`user_permission`）。
 * 没有权限记录的用户默认拒绝；角色不足返回 10003，数据域不匹配返回 10004（不暴露资源真实归属，规范 4.4）。</p>
 *
 * <p>为什么角色不足用 10003 而资源越权用 10004：前者是“你不该做这个动作”，与具体资源无关，
 * 泄露不了资源信息；后者是“这个资源对你不可见”，必须与“不存在”同码，否则可被枚举探测。</p>
 */
@Service
public class PermissionService implements PermissionPort {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final PermissionRepository repository;

    /**
     * 构造服务。
     *
     * @param repository 权限仓储
     */
    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void requireManageConfig(long userId) {
        UserPermissionRecord permission = requirePermission(userId);
        if (!permission.role().canManageConfig()) {
            denyAction(userId, "manage-config");
        }
    }

    @Override
    public void requireStartAudit(long userId) {
        UserPermissionRecord permission = requirePermission(userId);
        if (!permission.role().canStartAudit()) {
            denyAction(userId, "start-audit");
        }
    }

    @Override
    public void requireReview(long userId) {
        UserPermissionRecord permission = requirePermission(userId);
        if (!permission.role().canReview()) {
            denyAction(userId, "review");
        }
    }

    @Override
    public void requireContractAccess(long userId, long uploaderId) {
        UserPermissionRecord permission = requirePermission(userId);
        if (!permission.docScope().allowsContract(uploaderId, userId)) {
            // 与“资源不存在”同码：否则可用状态码差异枚举他人合同（规范 4.4）
            throw BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "contract out of data scope user=" + userId);
        }
    }

    private UserPermissionRecord requirePermission(long userId) {
        long tenantId = TenantContext.require();
        return repository.find(tenantId, userId)
                .orElseThrow(() -> BizException.of(ErrorCode.PERMISSION_DENIED,
                        "no permission record tenant=" + tenantId + " user=" + userId));
    }

    private static void denyAction(long userId, String action) {
        log.warn("权限拒绝 user={} action={}", userId, action);
        throw BizException.of(ErrorCode.PERMISSION_DENIED, "action not allowed: " + action);
    }
}
