-- =============================================================================
-- 数据库初始化 10：中文全文检索配置 crag_cn（规范 6.5）
--
-- 首选 zhparser，其次 pg_jieba；两者都不可用时退化为 simple 并登记整改期限（不超过两周）。
-- 配置名统一为 crag_cn，建表语句只引用该名称，不直接引用具体解析器。
--
-- 本脚本可重复执行：
--   1) 已有配置但解析器与当前可用扩展不一致时，先删除再重建（收敛到正确解析器）；
--   2) 分词类型映射按解析器实际词性清单生成，避免只映射部分词性导致召回缺失；
--   3) 标点类型 w 不建映射，减少无意义词项。
-- =============================================================================

DO $$
DECLARE
    chosen          text := 'simple';
    current_parser  text;
    token           record;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'zhparser') THEN
        CREATE EXTENSION IF NOT EXISTS zhparser;
        chosen := 'zhparser';
    ELSIF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_jieba') THEN
        CREATE EXTENSION IF NOT EXISTS pg_jieba;
        chosen := CASE WHEN EXISTS (SELECT 1 FROM pg_ts_parser WHERE prsname = 'jieba')
                       THEN 'jieba' ELSE 'jiebaqry' END;
    END IF;

    IF chosen = 'simple' THEN
        IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'crag_cn') THEN
            EXECUTE 'CREATE TEXT SEARCH CONFIGURATION crag_cn (COPY = simple)';
            EXECUTE format('COMMENT ON TEXT SEARCH CONFIGURATION crag_cn IS %L',
                           '阶段性方案：未安装 zhparser/pg_jieba，使用 simple 配置，整改期限两周（规范 6.5）');
            RAISE WARNING 'crag_cn 退化为 simple 配置：中文关键词召回会明显变差，请在两周内安装 zhparser 或 pg_jieba';
        END IF;
        RETURN;
    END IF;

    -- 已有配置但解析器不对（例如先退化到 simple、后补装 zhparser）：删除重建
    SELECT p.prsname INTO current_parser
    FROM pg_ts_config c
    JOIN pg_ts_parser p ON p.oid = c.cfgparser
    WHERE c.cfgname = 'crag_cn';

    IF current_parser IS NOT NULL AND current_parser <> chosen THEN
        EXECUTE 'DROP TEXT SEARCH CONFIGURATION crag_cn';
        RAISE NOTICE 'crag_cn 解析器由 % 切换为 %，已重建', current_parser, chosen;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'crag_cn') THEN
        EXECUTE format('CREATE TEXT SEARCH CONFIGURATION crag_cn (PARSER = %I)', chosen);
        FOR token IN
            SELECT alias FROM ts_token_type((SELECT oid FROM pg_ts_parser WHERE prsname = chosen))
            WHERE alias <> 'w'
        LOOP
            EXECUTE format('ALTER TEXT SEARCH CONFIGURATION crag_cn ADD MAPPING FOR %I WITH simple',
                           token.alias);
        END LOOP;
        EXECUTE format('COMMENT ON TEXT SEARCH CONFIGURATION crag_cn IS %L',
                       '中文分词配置，解析器 ' || chosen);
        RAISE NOTICE 'crag_cn 使用解析器 %，已映射全部词性（标点除外）', chosen;
    END IF;

    -- 自定义词典：覆盖合同高频风险词（规范 6.5）。缺词会直接导致关键词路召回为空。
    IF chosen = 'zhparser' AND to_regclass('zhparser.zhprs_custom_word') IS NOT NULL THEN
        INSERT INTO zhparser.zhprs_custom_word (word)
        SELECT word FROM unnest(ARRAY['违约金', '连带责任', '管辖地', '质保期', '账期',
                                      '免责', '保密', '争议解决']) AS word
        ON CONFLICT (word) DO NOTHING;
        RAISE NOTICE 'zhparser 自定义词典已写入 % 条合同高频词',
            (SELECT count(*) FROM zhparser.zhprs_custom_word);
    END IF;
END $$;
