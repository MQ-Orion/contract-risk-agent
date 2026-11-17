-- =============================================================================
-- Flyway 迁移：允许终态任务后重新发起审核（规范 9.1 vs 10.7 的边界）
-- 版本：V202611151020  描述：relax audit task unique index
-- 回滚方案：见文件末尾 DOWN。
-- 说明：
--   原唯一索引只排除 CANCELLED/FAILED，导致 COMPLETED 的合同永远无法再次发起审核。
--   9.1 禁止的是「并发」多个任务，不是「永久只能有一个」；10.7 的幂等针对「进行中的任务」。
--   因此唯一约束排除全部终态（COMPLETED/CANCELLED/FAILED），进行中的任务仍唯一。
-- =============================================================================

DROP INDEX IF EXISTS uk_audit_task_contract_version;

CREATE UNIQUE INDEX uk_audit_task_contract_version
    ON audit_task (tenant_id, contract_id, contract_version)
    WHERE deleted = 0 AND status NOT IN ('COMPLETED', 'CANCELLED', 'FAILED');

-- =============================================================================
-- DOWN：
-- DROP INDEX IF EXISTS uk_audit_task_contract_version;
-- CREATE UNIQUE INDEX uk_audit_task_contract_version ON audit_task (tenant_id, contract_id, contract_version)
--     WHERE deleted = 0 AND status NOT IN ('CANCELLED', 'FAILED');
-- =============================================================================
