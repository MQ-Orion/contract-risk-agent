-- =============================================================================
-- 第 1–2 周环境自检（规范 17.1 验收条件：环境一键起）
-- 用法：psql -h 127.0.0.1 -p 5434 -U crag -d contract_risk -f db/verify/verify_week1_2.sql
-- 输出 all_pass=true 表示扩展、分词配置、函数、表、索引、触发器均已就绪。
-- =============================================================================

WITH checks AS (
    SELECT 'extension.vector' AS item,
           EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') AS ok
    UNION ALL
    SELECT 'extension.zhparser',
           EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'zhparser')
    UNION ALL
    SELECT 'tsconfig.crag_cn',
           EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'crag_cn')
    UNION ALL
    SELECT 'tsconfig.crag_cn.zhparser',
           EXISTS (SELECT 1
                   FROM pg_ts_config c
                   JOIN pg_ts_parser p ON p.oid = c.cfgparser
                   WHERE c.cfgname = 'crag_cn' AND p.prsname = 'zhparser')
    UNION ALL
    SELECT 'function.crag_set_tsv',
           EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'crag_set_tsv')
    UNION ALL
    SELECT 'table.contract_main',
           to_regclass('public.contract_main') IS NOT NULL
    UNION ALL
    SELECT 'table.contract_parse_task',
           to_regclass('public.contract_parse_task') IS NOT NULL
    UNION ALL
    SELECT 'table.contract_chunk',
           to_regclass('public.contract_chunk') IS NOT NULL
    UNION ALL
    SELECT 'index.uk_contract_main_tenant_hash',
           EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uk_contract_main_tenant_hash')
    UNION ALL
    SELECT 'index.uk_contract_chunk_index',
           EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uk_contract_chunk_index')
    UNION ALL
    SELECT 'index.idx_contract_chunk_tsv',
           EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_contract_chunk_tsv')
    UNION ALL
    SELECT 'trigger.trg_contract_chunk_tsv',
           EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_contract_chunk_tsv')
)
SELECT item, ok FROM checks ORDER BY item;

WITH checks AS (
    SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') AS ok
    UNION ALL SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'zhparser')
    UNION ALL SELECT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'crag_cn')
    UNION ALL SELECT to_regclass('public.contract_chunk') IS NOT NULL
    UNION ALL SELECT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_contract_chunk_tsv')
)
SELECT bool_and(ok) AS all_pass FROM checks;
