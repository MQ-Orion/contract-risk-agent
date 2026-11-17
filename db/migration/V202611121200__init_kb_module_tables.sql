-- =============================================================================
-- Flyway 迁移：知识库模块核心表（规范 4.2、4.3、5.6、9.5）
-- 版本：V202611121200  描述：init kb module tables
-- 回滚方案：见文件末尾 DOWN 注释；本迁移不涉及数据搬迁。
-- 前置条件：db/init 已执行（pgvector 扩展、crag_cn 分词配置、crag_set_tsv 函数）。
-- =============================================================================

-- 知识库文档（含版本历史）。
-- doc_key 为逻辑文档标识，跨版本稳定，chunk_key 由它派生，保证版本切换时按切片做增量（规范 9.5）。
CREATE TABLE kb_doc
(
    id                bigint       NOT NULL,
    tenant_id         bigint       NOT NULL,
    doc_key           varchar(64)  NOT NULL,
    title             varchar(200) NOT NULL,
    doc_type          varchar(16)  NOT NULL,
    version_no        integer      NOT NULL DEFAULT 1,
    is_active         smallint     NOT NULL DEFAULT 0,
    source_hash       char(64)     NOT NULL,
    synced_file_hash  char(64),
    last_synced_at    timestamptz,
    sync_status       varchar(16)  NOT NULL DEFAULT 'PENDING',
    content_text      text         NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        bigint,
    deleted           smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_kb_doc PRIMARY KEY (id),
    CONSTRAINT ck_kb_doc_type CHECK (doc_type IN ('INSTITUTION', 'CASE')),
    CONSTRAINT ck_kb_doc_active CHECK (is_active IN (0, 1)),
    CONSTRAINT ck_kb_doc_sync_status CHECK (sync_status IN ('PENDING', 'EMBEDDING', 'READY', 'FAILED')),
    CONSTRAINT ck_kb_doc_version CHECK (version_no >= 1),
    CONSTRAINT ck_kb_doc_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE kb_doc IS '知识库文档（归属 kb-module），含版本与同步状态';
COMMENT ON COLUMN kb_doc.doc_key IS '逻辑文档标识，跨版本稳定，用于生成 chunk_key';
COMMENT ON COLUMN kb_doc.is_active IS '上下架标记：1 上架、0 下架；检索只查上架版本';
COMMENT ON COLUMN kb_doc.source_hash IS '源文本 SHA-256，用于判断文档是否变化';
COMMENT ON COLUMN kb_doc.synced_file_hash IS '上次同步完成时的源哈希快照，供每日对账只读判定（规范 5.6）';
COMMENT ON COLUMN kb_doc.last_synced_at IS '上次同步完成时间，供每日对账判定';
COMMENT ON COLUMN kb_doc.content_text IS '已解析的正文文本，对账与重建切片只读本列，不重新解析源文件';

CREATE UNIQUE INDEX uk_kb_doc_key_version
    ON kb_doc (tenant_id, doc_key, version_no)
    WHERE deleted = 0;

CREATE INDEX idx_kb_doc_tenant_active ON kb_doc (tenant_id, is_active);

-- 知识库切片：chunk_key 稳定业务键，content_hash 为变更判据（规范 4.2、4.3）
CREATE TABLE kb_chunk
(
    id              bigint        NOT NULL,
    tenant_id       bigint        NOT NULL,
    kb_doc_id       bigint        NOT NULL,
    chunk_key       varchar(160)  NOT NULL,
    clause_no       varchar(32)   NOT NULL,
    chunk_text      text          NOT NULL,
    chunk_type      varchar(32)   NOT NULL,
    content_hash    char(64)      NOT NULL,
    embedding       vector(1024),
    tsv             tsvector,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    created_by      bigint,
    deleted         smallint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_kb_chunk PRIMARY KEY (id),
    CONSTRAINT fk_kb_chunk_doc FOREIGN KEY (kb_doc_id) REFERENCES kb_doc (id),
    CONSTRAINT ck_kb_chunk_type CHECK (chunk_type IN ('BREACH', 'CONFIDENTIALITY', 'PAYMENT', 'JURISDICTION', 'DISCLAIMER', 'OTHER')),
    CONSTRAINT ck_kb_chunk_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE kb_chunk IS '知识库切片（归属 kb-module），向量与全文同表存储';
COMMENT ON COLUMN kb_chunk.chunk_key IS '由 doc_key 与条款序号生成，跨版本稳定，用于条件 upsert';
COMMENT ON COLUMN kb_chunk.content_hash IS '规范化文本 SHA-256，哈希未变则跳过重新嵌入（规范 4.3）';
COMMENT ON COLUMN kb_chunk.embedding IS '向量列，维度与嵌入模型一致（当前 qwen3.7-text-embedding-flash = 1024）';
COMMENT ON COLUMN kb_chunk.tsv IS '全文列，由触发器 crag_set_tsv 维护，应用层禁止写入';

CREATE UNIQUE INDEX uk_kb_chunk_key ON kb_chunk (chunk_key);
CREATE INDEX idx_kb_chunk_embedding ON kb_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_kb_chunk_tsv ON kb_chunk USING gin (tsv);
CREATE INDEX idx_kb_chunk_tenant_type ON kb_chunk (tenant_id, chunk_type);
CREATE INDEX idx_kb_chunk_doc ON kb_chunk (kb_doc_id);

CREATE TRIGGER trg_kb_chunk_tsv
    BEFORE INSERT OR UPDATE OF chunk_text
    ON kb_chunk
    FOR EACH ROW
EXECUTE FUNCTION crag_set_tsv();

-- =============================================================================
-- DOWN（回滚，仅用于本地与发布单留档）
-- DROP TRIGGER IF EXISTS trg_kb_chunk_tsv ON kb_chunk;
-- DROP TABLE IF EXISTS kb_chunk;
-- DROP TABLE IF EXISTS kb_doc;
-- =============================================================================
