-- =============================================================================
-- Flyway 迁移：合同抽取信息表（规范 4.2：归属 doc-module，source_span_json 必填）
-- 版本：V202611142010  描述：init contract extract info table
-- 回滚方案：DROP TABLE IF EXISTS contract_extract_info;（本迁移不涉及数据搬迁）
-- 说明：抽取由 agent-module 调用模型完成，但表归属 doc-module；agent 通过 doc 的端口写入，
--       不允许跨模块注入 Mapper（规范 2.2）。
-- =============================================================================

CREATE TABLE contract_extract_info
(
    id                  bigint        NOT NULL,
    tenant_id           bigint        NOT NULL,
    contract_id         bigint        NOT NULL,
    -- 金额用定点小数（规范 4.1：18 位精度、2 位小数，禁止浮点）
    contract_amount     numeric(18, 2),
    -- 账期天数；无法确定时为 null，并在 source_span_json 中标注“待人工确认”
    payment_days        integer,
    jurisdiction        varchar(128),
    has_joint_liability smallint,
    -- 校验结论：VERIFIED 字段均通过原文回验；PENDING_MANUAL 存在无原文片段或回验失败字段；
    -- DEGRADED 模型不可用，仅规则结论（规范 7.4、7.5）
    extract_status      varchar(24)   NOT NULL DEFAULT 'PENDING_MANUAL',
    -- 模型原始返回（截断存储），用于回溯与回归比对；禁止直接作为对外结论（规范 7.4）
    model_raw_response  text,
    -- 每个字段的原文片段与校验结果，必填（规范 4.2、7.4）
    source_span_json    text          NOT NULL,
    prompt_version      varchar(32),
    model_name          varchar(64),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          bigint,
    deleted             smallint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_contract_extract_info PRIMARY KEY (id),
    CONSTRAINT fk_extract_contract FOREIGN KEY (contract_id) REFERENCES contract_main (id),
    CONSTRAINT ck_extract_status CHECK (extract_status IN ('VERIFIED', 'PENDING_MANUAL', 'DEGRADED')),
    CONSTRAINT ck_extract_joint CHECK (has_joint_liability IS NULL OR has_joint_liability IN (0, 1)),
    CONSTRAINT ck_extract_days CHECK (payment_days IS NULL OR payment_days >= 0),
    CONSTRAINT ck_extract_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_extract_span_json CHECK (source_span_json IS JSON),
    CONSTRAINT ck_extract_raw_json CHECK (model_raw_response IS NULL OR model_raw_response IS JSON)
);

COMMENT ON TABLE contract_extract_info IS '合同结构化抽取结果（归属 doc-module，规范 4.2）';
COMMENT ON COLUMN contract_extract_info.source_span_json IS '字段原文片段与回验结果，必填；无片段的字段一律置待人工确认';
COMMENT ON COLUMN contract_extract_info.extract_status IS 'VERIFIED/PENDING_MANUAL/DEGRADED';
COMMENT ON COLUMN contract_extract_info.model_raw_response IS '模型原始返回（截断），仅供回溯，禁止对外输出';

CREATE UNIQUE INDEX uk_extract_contract ON contract_extract_info (tenant_id, contract_id) WHERE deleted = 0;

-- =============================================================================
-- DOWN：DROP TABLE IF EXISTS contract_extract_info;
-- =============================================================================
