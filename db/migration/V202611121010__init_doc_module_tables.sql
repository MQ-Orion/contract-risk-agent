-- =============================================================================
-- Flyway 迁移：文档模块核心表（规范 3.1、4.2、4.3）
-- 版本：V202611121010  描述：init doc module tables
-- 回滚方案：见文件末尾 DOWN 注释，按逆序删除触发器、索引与表；本迁移不涉及数据搬迁。
-- 前置条件：db/init 已执行（pgvector 扩展、crag_cn 分词配置、crag_set_tsv 函数）。
-- =============================================================================

-- 合同主表：同租户同哈希唯一，用于秒传与去重（规范 5.1、4.2）
CREATE TABLE contract_main
(
    id            bigint       NOT NULL,
    tenant_id     bigint       NOT NULL,
    contract_no   varchar(64),
    name          varchar(200) NOT NULL,
    file_hash     char(64)     NOT NULL,
    file_path     varchar(512) NOT NULL,
    file_format   varchar(16)  NOT NULL,
    file_size     bigint       NOT NULL,
    page_count    integer      NOT NULL DEFAULT 0,
    uploader_id   bigint       NOT NULL,
    audit_status  varchar(32),
    version_no    integer      NOT NULL DEFAULT 1,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    bigint,
    deleted       smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_contract_main PRIMARY KEY (id),
    CONSTRAINT ck_contract_main_format CHECK (file_format IN ('PDF', 'DOC', 'DOCX')),
    CONSTRAINT ck_contract_main_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE contract_main IS '合同主表（归属 doc-module）';
COMMENT ON COLUMN contract_main.file_hash IS '源文件 SHA-256，同租户内唯一';
COMMENT ON COLUMN contract_main.file_path IS '存储相对路径，按租户/年月生成，不含原始文件名';

-- 同租户同哈希唯一；逻辑删除后允许重新上传同一文件
CREATE UNIQUE INDEX uk_contract_main_tenant_hash
    ON contract_main (tenant_id, file_hash)
    WHERE deleted = 0;

-- 解析任务表：状态机落库，人工可重试（规范 5.5）
CREATE TABLE contract_parse_task
(
    id             bigint      NOT NULL,
    tenant_id      bigint      NOT NULL,
    contract_id    bigint      NOT NULL,
    status         varchar(16) NOT NULL,
    retry_count    integer     NOT NULL DEFAULT 0,
    next_retry_at  timestamptz,
    error_code     varchar(32),
    error_message  varchar(512),
    started_at     timestamptz,
    finished_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     bigint,
    deleted        smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_contract_parse_task PRIMARY KEY (id),
    CONSTRAINT fk_parse_task_contract FOREIGN KEY (contract_id) REFERENCES contract_main (id),
    CONSTRAINT ck_parse_task_status CHECK (status IN ('PENDING', 'PARSING', 'SPLITTING', 'EMBEDDING', 'READY', 'FAILED')),
    CONSTRAINT ck_parse_task_retry CHECK (retry_count >= 0),
    CONSTRAINT ck_parse_task_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE contract_parse_task IS '合同解析任务状态（归属 doc-module）';

CREATE INDEX idx_parse_task_tenant_contract
    ON contract_parse_task (tenant_id, contract_id, created_at DESC);

-- 合同切片表：只作为查询输入，不存 embedding、不参与向量召回（规范 4.2）
CREATE TABLE contract_chunk
(
    id              bigint      NOT NULL,
    tenant_id       bigint      NOT NULL,
    contract_id     bigint      NOT NULL,
    clause_no       varchar(32) NOT NULL,
    chunk_type      varchar(32) NOT NULL,
    type_source     varchar(16) NOT NULL,
    chunk_text      text        NOT NULL,
    full_text_index integer     NOT NULL,
    char_start      integer     NOT NULL,
    char_end        integer     NOT NULL,
    tsv             tsvector,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      bigint,
    deleted         smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_contract_chunk PRIMARY KEY (id),
    CONSTRAINT fk_contract_chunk_contract FOREIGN KEY (contract_id) REFERENCES contract_main (id),
    CONSTRAINT ck_contract_chunk_type CHECK (chunk_type IN ('BREACH', 'CONFIDENTIALITY', 'PAYMENT', 'JURISDICTION', 'DISCLAIMER', 'OTHER')),
    CONSTRAINT ck_contract_chunk_type_source CHECK (type_source IN ('KEYWORD', 'MODEL', 'MANUAL', 'DEFAULT')),
    CONSTRAINT ck_contract_chunk_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE contract_chunk IS '合同条款切片（归属 doc-module），仅用于合同内关键词定位';
COMMENT ON COLUMN contract_chunk.char_start IS '切片在原文中的起始偏移（含）';
COMMENT ON COLUMN contract_chunk.char_end IS '切片在原文中的结束偏移（不含）';
COMMENT ON COLUMN contract_chunk.tsv IS '全文列，由触发器 crag_set_tsv 维护，应用层禁止写入';

CREATE UNIQUE INDEX uk_contract_chunk_index
    ON contract_chunk (contract_id, full_text_index)
    WHERE deleted = 0;

CREATE INDEX idx_contract_chunk_tsv ON contract_chunk USING gin (tsv);
CREATE INDEX idx_contract_chunk_tenant_type ON contract_chunk (tenant_id, chunk_type);

-- tsv 由触发器维护（规范 4.3：禁止应用层手写全文转换）
CREATE TRIGGER trg_contract_chunk_tsv
    BEFORE INSERT OR UPDATE OF chunk_text
    ON contract_chunk
    FOR EACH ROW
EXECUTE FUNCTION crag_set_tsv();

-- =============================================================================
-- DOWN（回滚，仅用于本地与发布单留档，Flyway 不支持回滚脚本时人工执行）
-- DROP TRIGGER IF EXISTS trg_contract_chunk_tsv ON contract_chunk;
-- DROP TABLE IF EXISTS contract_chunk;
-- DROP TABLE IF EXISTS contract_parse_task;
-- DROP TABLE IF EXISTS contract_main;
-- =============================================================================
