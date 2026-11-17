-- =============================================================================
-- 数据库初始化 00：扩展安装（规范 3.1 db/init、4.3）
-- 仅由容器 entrypoint 以超级用户执行；应用侧 Flyway 不得创建扩展。
-- =============================================================================

-- pgvector：知识库切片向量列（kb_chunk.embedding）使用；索引 HNSW、距离度量 cosine（规范 2.4、4.3）
-- 镜像必须自带该扩展，缺失时在此处立即失败，避免带着“看似正常”的库上线。
CREATE EXTENSION IF NOT EXISTS vector;

-- 便于运维核对版本：pgvector 0.7 以上（规范 2.4）
DO $$
DECLARE
    installed text;
BEGIN
    SELECT extversion INTO installed FROM pg_extension WHERE extname = 'vector';
    RAISE NOTICE 'pgvector version = %', installed;
END $$;
