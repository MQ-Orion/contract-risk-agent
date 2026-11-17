-- =============================================================================
-- Flyway 迁移：审核模块四表（规范 4.2 核心表基线、9.1 状态机、9.4 复核回流、11.1 权限）
-- 版本：V202611151000  描述：init audit module tables
-- 回滚方案：见文件末尾 DOWN 注释；本迁移不涉及数据搬迁。
-- 说明：
--   1. audit_review_log 只追加（规范 9.4、11.6），故无 updated_at/deleted 两列；
--   2. 审核任务以「租户 + 合同 + 合同版本」唯一，保证同一合同不并发多个审核（规范 9.1、10.7）；
--   3. 风险项带 version_no 供复核乐观锁（规范 10.7）。
-- =============================================================================

-- 审核任务（归属 audit-module）
CREATE TABLE audit_task
(
    id              bigint      NOT NULL,
    tenant_id       bigint      NOT NULL,
    contract_id     bigint      NOT NULL,
    -- 合同版本号：同合同新版本允许另起任务（规范 9.5），同版本禁止并发（规范 9.1）
    contract_version integer    NOT NULL DEFAULT 1,
    status          varchar(24) NOT NULL DEFAULT 'CREATED',
    auditor_id      bigint,
    -- 结论摘要（人工确认后才写入；模型结论不得直接作为对外结论，规范 7.4）
    conclusion      varchar(500),
    model_version   varchar(64),
    prompt_version  varchar(32),
    -- 规范 9.3：语义收敛为「最近一次导出文件的位置」，不是报告正文副本
    report_url      varchar(512),
    started_at      timestamptz,
    finished_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      bigint,
    deleted         smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_audit_task PRIMARY KEY (id),
    CONSTRAINT fk_audit_task_contract FOREIGN KEY (contract_id) REFERENCES contract_main (id),
    CONSTRAINT ck_audit_task_status CHECK (status IN ('CREATED', 'PARSING', 'AUDITING', 'PENDING_REVIEW',
                                                      'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_audit_task_version CHECK (contract_version >= 1),
    CONSTRAINT ck_audit_task_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE audit_task IS '审核任务（归属 audit-module），状态机见规范 9.1';
COMMENT ON COLUMN audit_task.contract_version IS '合同版本号；与租户、合同一起唯一，防止同一版本并发审核';
COMMENT ON COLUMN audit_task.report_url IS '最近一次导出文件的位置（规范 9.3），不是报告正文存档';
COMMENT ON COLUMN audit_task.conclusion IS '人工复核后的结论摘要，模型结论不得直接写入';

-- 禁止同一合同同版本并发任务（规范 9.1、10.7 幂等）
CREATE UNIQUE INDEX uk_audit_task_contract_version
    ON audit_task (tenant_id, contract_id, contract_version)
    WHERE deleted = 0 AND status NOT IN ('CANCELLED', 'FAILED');
CREATE INDEX idx_audit_task_tenant_status ON audit_task (tenant_id, status, updated_at DESC);

-- 风险明细（归属 audit-module）：每条必须带来源切片与引用原文（规范 9.2、9.3）
CREATE TABLE audit_risk_item
(
    id                bigint       NOT NULL,
    tenant_id         bigint       NOT NULL,
    task_id           bigint       NOT NULL,
    risk_level        varchar(16)  NOT NULL,
    risk_content      varchar(1000) NOT NULL,
    suggestion        varchar(500),
    -- 来源：规则 / 模型 / 规则与模型冲突（规范 8.4）
    source            varchar(16)  NOT NULL DEFAULT 'RULE',
    rule_code         varchar(64),
    rule_version      integer,
    source_doc_name   varchar(200),
    source_chunk_key  varchar(200),
    source_excerpt    varchar(500),
    citation_verified smallint     NOT NULL DEFAULT 0,
    clause_no         varchar(32),
    clause_type       varchar(32),
    -- 复核字段（规范 4.2：是否已复核、复核结果）
    reviewed          smallint     NOT NULL DEFAULT 0,
    review_result     varchar(16),
    reviewed_by       bigint,
    reviewed_at       timestamptz,
    -- 乐观锁版本，复核时校验，避免覆盖他人结果（规范 10.7）
    version_no        integer      NOT NULL DEFAULT 1,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        bigint,
    deleted           smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_audit_risk_item PRIMARY KEY (id),
    CONSTRAINT fk_risk_item_task FOREIGN KEY (task_id) REFERENCES audit_task (id),
    CONSTRAINT ck_risk_item_level CHECK (risk_level IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_risk_item_source CHECK (source IN ('RULE', 'MODEL', 'RULE_CONFLICT')),
    CONSTRAINT ck_risk_item_reviewed CHECK (reviewed IN (0, 1)),
    CONSTRAINT ck_risk_item_review_result CHECK (review_result IS NULL OR review_result IN ('PASS', 'REJECT', 'NEED_FIX')),
    CONSTRAINT ck_risk_item_version CHECK (version_no >= 1),
    CONSTRAINT ck_risk_item_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE audit_risk_item IS '风险明细（归属 audit-module），规则结论必须进入清单且不可被模型覆盖（规范 7.2）';
COMMENT ON COLUMN audit_risk_item.version_no IS '乐观锁版本；复核请求必须携带期望版本（规范 10.7）';

CREATE INDEX idx_risk_item_task ON audit_risk_item (tenant_id, task_id, risk_level);
CREATE INDEX idx_risk_item_unreviewed ON audit_risk_item (tenant_id, task_id) WHERE reviewed = 0;

-- 复核日志（归属 audit-module）：只追加，不可修改（规范 9.4、11.6）
CREATE TABLE audit_review_log
(
    id             bigint       NOT NULL,
    tenant_id      bigint       NOT NULL,
    task_id        bigint       NOT NULL,
    risk_item_id   bigint       NOT NULL,
    reviewer_id    bigint       NOT NULL,
    reviewer_name  varchar(64),
    action         varchar(16)  NOT NULL,
    -- 驳回必填，四类固定原因码（规范 9.4）
    reason_code    varchar(32),
    comment        varchar(500),
    trace_id       varchar(64),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_review_log PRIMARY KEY (id),
    CONSTRAINT fk_review_log_task FOREIGN KEY (task_id) REFERENCES audit_task (id),
    CONSTRAINT fk_review_log_item FOREIGN KEY (risk_item_id) REFERENCES audit_risk_item (id),
    CONSTRAINT ck_review_log_action CHECK (action IN ('PASS', 'REJECT', 'NEED_FIX')),
    CONSTRAINT ck_review_log_reason CHECK (reason_code IS NULL OR reason_code IN ('IRRELEVANT_RETRIEVAL',
        'WRONG_CITATION', 'RULE_FALSE_POSITIVE', 'SEMANTIC_MISJUDGE')),
    CONSTRAINT ck_review_log_reject_reason CHECK (action <> 'REJECT' OR reason_code IS NOT NULL)
);

COMMENT ON TABLE audit_review_log IS '人工复核日志（只追加，规范 9.4、11.6）；驳回原因码固定四类';
COMMENT ON COLUMN audit_review_log.reason_code IS 'IRRELEVANT_RETRIEVAL 检索无关 / WRONG_CITATION 引用错误 / RULE_FALSE_POSITIVE 规则误报 / SEMANTIC_MISJUDGE 语义误判';

CREATE INDEX idx_review_log_task ON audit_review_log (tenant_id, task_id, created_at DESC);
CREATE INDEX idx_review_log_reason ON audit_review_log (tenant_id, reason_code, created_at DESC);

-- 用户权限（归属 audit-module）：角色 + 数据域双层校验（规范 11.1）
CREATE TABLE user_permission
(
    id          bigint      NOT NULL,
    tenant_id   bigint      NOT NULL,
    user_id     bigint      NOT NULL,
    user_name   varchar(64),
    -- 角色决定可执行动作：ADMIN 管理规则/权限、AUDITOR 发起审核、REVIEWER 复核、VIEWER 只读
    role        varchar(16) NOT NULL,
    -- 数据域决定可见范围：ALL 全租户、TENANT 本租户、OWN 仅本人上传、NONE 不可见
    doc_scope   varchar(16) NOT NULL DEFAULT 'TENANT',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    deleted     smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_permission PRIMARY KEY (id),
    CONSTRAINT ck_user_permission_role CHECK (role IN ('ADMIN', 'AUDITOR', 'REVIEWER', 'VIEWER')),
    CONSTRAINT ck_user_permission_scope CHECK (doc_scope IN ('ALL', 'TENANT', 'OWN', 'NONE')),
    CONSTRAINT ck_user_permission_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE user_permission IS '用户权限（归属 audit-module）：角色 + 数据域，权限变更必须留痕（规范 11.1）';

CREATE UNIQUE INDEX uk_user_permission_tenant_user ON user_permission (tenant_id, user_id) WHERE deleted = 0;

-- =============================================================================
-- DOWN（回滚）：按依赖逆序
-- DROP TABLE IF EXISTS user_permission;
-- DROP TABLE IF EXISTS audit_review_log;
-- DROP TABLE IF EXISTS audit_risk_item;
-- DROP TABLE IF EXISTS audit_task;
-- =============================================================================
