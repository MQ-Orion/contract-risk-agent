package com.crag.audit.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.audit.application.port.PermissionRepository;
import com.crag.audit.domain.model.DocScopeEnum;
import com.crag.audit.domain.model.UserPermissionRecord;
import com.crag.audit.domain.model.UserRoleEnum;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 权限模型测试（规范 11.1、4.4、10.3）：角色矩阵、默认拒绝、数据域与状态码口径。
 */
class PermissionServiceTest {

    private static final long USER = 9L;
    private static final long OTHER_UPLOADER = 77L;

    private UserPermissionRecord permission;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        PermissionRepository repository = (tenantId, userId) ->
                permission == null || permission.userId() != userId ? Optional.empty() : Optional.of(permission);
        service = new PermissionService(repository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void grant(UserRoleEnum role, DocScopeEnum scope) {
        permission = new UserPermissionRecord(1L, USER, role, scope);
    }

    @Test
    @DisplayName("无权限记录默认拒绝（10003）")
    void defaultDeny() {
        permission = null;
        assertThatThrownBy(() -> service.requireStartAudit(USER))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("只读角色不能改规则、不能发起审核、不能复核")
    void viewerCannotAct() {
        grant(UserRoleEnum.VIEWER, DocScopeEnum.TENANT);
        assertThatThrownBy(() -> service.requireManageConfig(USER)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.requireStartAudit(USER)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.requireReview(USER)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("审核员不能改规则（越权改规则路径）")
    void auditorCannotManageConfig() {
        grant(UserRoleEnum.AUDITOR, DocScopeEnum.TENANT);
        assertThatThrownBy(() -> service.requireManageConfig(USER))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("复核员不能发起审核（越权导出报告路径）")
    void reviewerCannotStartAudit() {
        grant(UserRoleEnum.REVIEWER, DocScopeEnum.TENANT);
        assertThatThrownBy(() -> service.requireStartAudit(USER))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("OWN 数据域读他人合同返回 10004（与不存在同码）")
    void ownScopeHidesOthersContract() {
        grant(UserRoleEnum.AUDITOR, DocScopeEnum.OWN);
        assertThatThrownBy(() -> service.requireContractAccess(USER, OTHER_UPLOADER))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        // 自己上传的合同可见
        service.requireContractAccess(USER, USER);
    }

    @Test
    @DisplayName("TENANT/ALL 数据域可见本租户合同；NONE 不可见")
    void tenantAndAllScopeVisible() {
        grant(UserRoleEnum.AUDITOR, DocScopeEnum.TENANT);
        service.requireContractAccess(USER, OTHER_UPLOADER);
        grant(UserRoleEnum.ADMIN, DocScopeEnum.ALL);
        service.requireContractAccess(USER, OTHER_UPLOADER);
        grant(UserRoleEnum.AUDITOR, DocScopeEnum.NONE);
        assertThatThrownBy(() -> service.requireContractAccess(USER, OTHER_UPLOADER))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("管理员具备全部动作权限")
    void adminCanDoAll() {
        grant(UserRoleEnum.ADMIN, DocScopeEnum.TENANT);
        service.requireManageConfig(USER);
        service.requireStartAudit(USER);
        service.requireReview(USER);
        service.requireContractAccess(USER, OTHER_UPLOADER);
    }
}
