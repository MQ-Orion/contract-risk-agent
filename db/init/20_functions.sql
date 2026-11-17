-- =============================================================================
-- 数据库初始化 20：通用函数（规范 3.1 db/init、4.3）
--
-- tsv 列由触发器统一维护，应用层禁止手写全文转换函数，否则两处口径必然分叉。
-- 本函数不绑定具体表，contract_chunk（第 1–2 周）与 kb_chunk（第 3–4 周）共用。
-- =============================================================================

CREATE OR REPLACE FUNCTION crag_set_tsv()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.tsv := to_tsvector('crag_cn', coalesce(NEW.chunk_text, ''));
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION crag_set_tsv() IS '维护切片全文列 tsv，分词配置固定为 crag_cn（规范 4.3、6.5）';
