-- =============================================================================
-- Flyway 迁移：风险明细补充回流所需上下文（规范 9.4 hard negatives）
-- 版本：V202611151010  描述：add risk item context columns
-- 回滚方案：ALTER TABLE audit_risk_item DROP COLUMN retrieval_snapshot, DROP COLUMN clause_text;
-- 说明：hard negatives 字段固定为「条款文本、召回片段、期望结论为空、原因码、来源标记」。
--       审核模块不允许依赖 doc/kb（规范 2.2），因此条款文本与召回片段必须在生成风险时随风险项落库，
--       否则导出时无法取到（事后拼接会引入新的跨模块耦合）。
-- =============================================================================

ALTER TABLE audit_risk_item
    ADD COLUMN clause_text         varchar(2000),
    ADD COLUMN retrieval_snapshot  text;

COMMENT ON COLUMN audit_risk_item.clause_text IS '风险对应的条款原文，供 hard negatives 回流（规范 9.4）';
COMMENT ON COLUMN audit_risk_item.retrieval_snapshot IS '召回片段快照 JSON 数组 [{chunkKey,text}]，供回流与复核比对';

-- =============================================================================
-- DOWN：
-- ALTER TABLE audit_risk_item DROP COLUMN retrieval_snapshot;
-- ALTER TABLE audit_risk_item DROP COLUMN clause_text;
-- =============================================================================
