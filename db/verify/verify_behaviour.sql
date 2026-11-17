-- =============================================================================
-- 第 1–2 周数据库行为校验（规范 4.3、4.4、5.1、5.5）
--
-- 目标：验证 tsv 触发器、中文分词检索、逻辑删除下的部分唯一索引、切片唯一约束等
--       “建表语句之外”的行为。
-- 用法：psql -U crag -d contract_risk -f db/verify/verify_behaviour.sql
-- 说明：全部操作在事务内执行并 ROLLBACK，不留下测试数据。
-- =============================================================================

BEGIN;

CREATE TEMP TABLE check_result
(
    name text,
    ok   boolean
) ON COMMIT DROP;

INSERT INTO contract_main (id, tenant_id, name, file_hash, file_path, file_format, file_size, uploader_id)
VALUES (900000000000000001, 1, '行为校验合同', repeat('a', 64), '1/202611/900000000000000001.docx', 'DOCX', 1024, 1);

INSERT INTO contract_chunk (id, tenant_id, contract_id, clause_no, chunk_type, type_source,
                            chunk_text, full_text_index, char_start, char_end)
VALUES (900000000000000002, 1, 900000000000000001, '第八条', 'BREACH', 'KEYWORD',
        '乙方逾期交付的，应支付违约金 payment due within 10 days', 0, 0, 30);

-- 1. 插入后 tsv 自动维护
INSERT INTO check_result
VALUES ('tsv.auto_filled',
        (SELECT tsv IS NOT NULL AND length(tsv::text) > 0 FROM contract_chunk
         WHERE id = 900000000000000002));

-- 2. 中文分词可命中：违约金只需作为普通词查询即可命中（zhparser 生效的标志）
INSERT INTO check_result
VALUES ('tsv.chinese_queryable',
        (SELECT count(*) = 1 FROM contract_chunk
         WHERE id = 900000000000000002 AND tsv @@ to_tsquery('crag_cn', '违约金')));

-- 3. 英文/数字混排也可命中（词性 x 已映射）
INSERT INTO check_result
VALUES ('tsv.ascii_queryable',
        (SELECT count(*) = 1 FROM contract_chunk
         WHERE id = 900000000000000002 AND tsv @@ to_tsquery('crag_cn', 'payment')));

-- 4. 更新切片文本后 tsv 随之变化
UPDATE contract_chunk SET chunk_text = '乙方应在二十日内开票 invoice within 20 days'
WHERE id = 900000000000000002;
INSERT INTO check_result
VALUES ('tsv.refreshed_on_update',
        (SELECT tsv @@ to_tsquery('crag_cn', 'invoice') FROM contract_chunk
         WHERE id = 900000000000000002));

-- 5. 同租户同哈希重复插入被唯一索引拒绝（异常捕获判定，见下方 DO 块）
DO $$
BEGIN
    BEGIN
        INSERT INTO contract_main (id, tenant_id, name, file_hash, file_path, file_format, file_size, uploader_id)
        VALUES (900000000000000003, 1, '重复哈希合同', repeat('a', 64), '1/202611/900000000000000003.docx', 'DOCX', 10, 1);
        INSERT INTO check_result VALUES ('contract.duplicate_hash_rejected', false);
    EXCEPTION WHEN unique_violation THEN
        INSERT INTO check_result VALUES ('contract.duplicate_hash_rejected', true);
    END;

    -- 6. 逻辑删除后允许重新登记同一哈希（部分唯一索引语义）
    BEGIN
        UPDATE contract_main SET deleted = 1 WHERE id = 900000000000000001;
        INSERT INTO contract_main (id, tenant_id, name, file_hash, file_path, file_format, file_size, uploader_id)
        VALUES (900000000000000004, 1, '同哈希重新上传', repeat('a', 64), '1/202611/900000000000000004.docx', 'DOCX', 10, 1);
        INSERT INTO check_result VALUES ('contract.soft_deleted_hash_reusable', true);
    EXCEPTION WHEN unique_violation THEN
        INSERT INTO check_result VALUES ('contract.soft_deleted_hash_reusable', false);
    END;

    -- 7. 同一合同内 full_text_index 重复被拒绝
    BEGIN
        INSERT INTO contract_chunk (id, tenant_id, contract_id, clause_no, chunk_type, type_source,
                                    chunk_text, full_text_index, char_start, char_end)
        VALUES (900000000000000005, 1, 900000000000000001, '第八条', 'BREACH', 'KEYWORD', 'x', 0, 0, 1);
        INSERT INTO check_result VALUES ('chunk.duplicate_index_rejected', false);
    EXCEPTION WHEN unique_violation THEN
        INSERT INTO check_result VALUES ('chunk.duplicate_index_rejected', true);
    END;

    -- 8. 枚举取值受约束
    BEGIN
        INSERT INTO contract_chunk (id, tenant_id, contract_id, clause_no, chunk_type, type_source,
                                    chunk_text, full_text_index, char_start, char_end)
        VALUES (900000000000000006, 1, 900000000000000001, '第八条', 'UNKNOWN_TYPE', 'KEYWORD', 'x', 9, 0, 1);
        INSERT INTO check_result VALUES ('chunk.enum_checked', false);
    EXCEPTION WHEN check_violation THEN
        INSERT INTO check_result VALUES ('chunk.enum_checked', true);
    END;
END $$;

SELECT name, ok, CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result
FROM check_result
ORDER BY name;

SELECT bool_and(ok) AS all_pass FROM check_result;

ROLLBACK;
