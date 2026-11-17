-- =============================================================================
-- Flyway 迁移：Agent 模块规则与模型调用表（规范 4.2、7.6、8.1、8.3）
-- 版本：V202611142000  描述：init rule and model call tables
-- 回滚方案：见文件末尾 DOWN 注释；本迁移不涉及数据搬迁，回滚仅删除空表。
-- 前置条件：db/init 已执行（crag_cn 分词配置、crag_set_tsv 函数）。
-- =============================================================================

-- 规则配置表（规范 8.1、4.2）：
-- 规则编码三段式，编码与业务键一起唯一；启用标记与版本号支撑热更新与灰度（规范 8.2、8.3）。
-- tenant_id 的存在是为了支持“单租户关闭态试跑后再全量启用”的灰度流程（规范 8.3）。
CREATE TABLE rule_config
(
    id                  bigint       NOT NULL,
    tenant_id           bigint       NOT NULL,
    rule_code           varchar(64)  NOT NULL,
    rule_name           varchar(128) NOT NULL,
    -- 适用条款类型；CONTRACT 表示合同级规则（判“缺失必备条款”），CLAUSE 级规则填六类枚举之一
    clause_type         varchar(32)  NOT NULL,
    scope               varchar(16)  NOT NULL DEFAULT 'CLAUSE',
    condition_expr      varchar(500) NOT NULL,
    risk_level          varchar(16)  NOT NULL,
    suggestion_template varchar(500) NOT NULL,
    enabled             smallint     NOT NULL DEFAULT 0,
    version_no          integer      NOT NULL DEFAULT 1,
    -- 规范 8.3：新增规则必须附带正例与反例各一条，并纳入回放用例。
    -- 用带 JSON 约束的 text 而非 jsonb：回放需要完整上下文（条款文本、金额、账期、条款类型集合），
    -- 只有一段文本复现不出求值条件；列不做 JSON 运算符查询，text + IS JSON 约束已足够，
    -- 同时避免 MyBatis 写入 jsonb 需要 PGobject 类型处理器的额外耦合。
    positive_example    text         NOT NULL,
    negative_example    text         NOT NULL,
    -- 维护人（规范 8.1 字段“维护人”）
    owner_name          varchar(64)  NOT NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          bigint,
    deleted             smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_rule_config PRIMARY KEY (id),
    CONSTRAINT ck_rule_config_scope CHECK (scope IN ('CLAUSE', 'CONTRACT')),
    CONSTRAINT ck_rule_config_type CHECK (clause_type IN ('BREACH', 'CONFIDENTIALITY', 'PAYMENT', 'JURISDICTION',
                                                          'DISCLAIMER', 'OTHER', 'CONTRACT')),
    CONSTRAINT ck_rule_config_level CHECK (risk_level IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_rule_config_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_rule_config_version CHECK (version_no >= 1),
    CONSTRAINT ck_rule_config_expr_len CHECK (char_length(condition_expr) <= 500),
    CONSTRAINT ck_rule_config_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_rule_config_positive_json CHECK (positive_example IS JSON),
    CONSTRAINT ck_rule_config_negative_json CHECK (negative_example IS JSON)
);

COMMENT ON TABLE rule_config IS '规则配置（归属 agent-module），禁止在业务代码内新增 if-else 规则分支（规范 8.1）';
COMMENT ON COLUMN rule_config.rule_code IS '三段式编码：条款类型.业务点.序号，如 PAYMENT.MAX_PAYMENT_DAYS.01';
COMMENT ON COLUMN rule_config.condition_expr IS 'Aviator 表达式，变量白名单见规范 8.2；长度不超过 500 字符';
COMMENT ON COLUMN rule_config.scope IS 'CLAUSE 单条款求值；CONTRACT 合同级求值（可读 presentTypes）';
COMMENT ON COLUMN rule_config.enabled IS '启用标记：0 关闭（灰度试跑态）、1 启用';
COMMENT ON COLUMN rule_config.version_no IS '规则版本，每次变更 +1 并写入 rule_change_log';

CREATE UNIQUE INDEX uk_rule_config_tenant_code ON rule_config (tenant_id, rule_code) WHERE deleted = 0;
CREATE INDEX idx_rule_config_tenant_type ON rule_config (tenant_id, clause_type, enabled);
CREATE INDEX idx_rule_config_updated ON rule_config (tenant_id, updated_at DESC);

-- 规则变更日志（规范 8.3：记录变更前后值与操作人）：
-- 只追加、不更新、不删除，因此没有 updated_at/deleted 两个审计字段（规范 4.1 的删除标记对只追加表无意义）。
CREATE TABLE rule_change_log
(
    id            bigint      NOT NULL,
    tenant_id     bigint      NOT NULL,
    rule_id       bigint      NOT NULL,
    rule_code     varchar(64) NOT NULL,
    action        varchar(16) NOT NULL,
    version_before integer,
    version_after  integer,
    before_json   jsonb,
    after_json    jsonb,
    operator_id   bigint,
    operator_name varchar(64),
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_rule_change_log PRIMARY KEY (id),
    CONSTRAINT ck_rule_change_action CHECK (action IN ('CREATE', 'UPDATE', 'ENABLE', 'DISABLE', 'GRAY'))
);

COMMENT ON TABLE rule_change_log IS '规则变更日志（归属 agent-module），只追加，供审计与回溯规则口径';
COMMENT ON COLUMN rule_change_log.before_json IS '变更前快照；CREATE 时为 null';
COMMENT ON COLUMN rule_change_log.after_json IS '变更后快照';

CREATE INDEX idx_rule_change_log_rule ON rule_change_log (tenant_id, rule_id, created_at DESC);

-- 模型调用日志（规范 7.6：模型名与版本、Prompt 版本、令牌数、耗时、是否降级）：
-- 只追加；用于成本核算与质量排查，不参与业务事实判断。
CREATE TABLE model_call_log
(
    id                bigint      NOT NULL,
    tenant_id         bigint      NOT NULL,
    contract_id       bigint,
    clause_no         varchar(32),
    purpose           varchar(32) NOT NULL,
    model_name        varchar(64) NOT NULL,
    model_version     varchar(64),
    prompt_version    varchar(32) NOT NULL,
    prompt_tokens     integer     NOT NULL DEFAULT 0,
    completion_tokens integer     NOT NULL DEFAULT 0,
    total_tokens      integer     NOT NULL DEFAULT 0,
    latency_millis    integer     NOT NULL DEFAULT 0,
    degraded          smallint    NOT NULL DEFAULT 0,
    degrade_reason    varchar(64),
    trace_id          varchar(64),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_model_call_log PRIMARY KEY (id),
    CONSTRAINT ck_model_call_purpose CHECK (purpose IN ('EXTRACT', 'RISK_JUDGEMENT', 'CLAUSE_TYPE')),
    CONSTRAINT ck_model_call_degraded CHECK (degraded IN (0, 1))
);

COMMENT ON TABLE model_call_log IS '模型调用日志（归属 agent-module），只追加；规范 7.6 要求记录的五项在此表';
COMMENT ON COLUMN model_call_log.purpose IS '调用用途：字段抽取、风险判断、条款类型兜底';
COMMENT ON COLUMN model_call_log.degraded IS '是否降级（1 表示该次调用失败后走了规则兜底）';

CREATE INDEX idx_model_call_log_contract ON model_call_log (tenant_id, contract_id, created_at DESC);

-- =============================================================================
-- DOWN（回滚）：按依赖逆序删除
-- DROP TABLE IF EXISTS model_call_log;
-- DROP TABLE IF EXISTS rule_change_log;
-- DROP TABLE IF EXISTS rule_config;
-- =============================================================================