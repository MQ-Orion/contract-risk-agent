-- =============================================================================
-- Flyway 迁移：开发租户的管理员权限种子（规范 11.1）
-- 版本：V202611151030  描述：seed dev admin permission
-- 回滚方案：DELETE FROM user_permission WHERE tenant_id = 1 AND user_id = 1;
-- 说明：开发期身份为静态实现（ADR 0002 偏差 6），用户 1 需要管理员权限才能操作规则与审核；
--       生产接入令牌后按真实用户写入权限，本种子不随生产发布。
-- =============================================================================

INSERT INTO user_permission (id, tenant_id, user_id, user_name, role, doc_scope)
SELECT 9101, 1, 1, 'dev-admin', 'ADMIN', 'ALL'
WHERE NOT EXISTS (
    SELECT 1 FROM user_permission WHERE tenant_id = 1 AND user_id = 1 AND deleted = 0
);

-- =============================================================================
-- DOWN：DELETE FROM user_permission WHERE id = 9101;
-- =============================================================================
