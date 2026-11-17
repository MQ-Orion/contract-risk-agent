-- 风险项“本轮失效”标记：驳回后重审时，旧一轮风险项保留供审计追溯，但不再出现在当前清单。
-- 背景：驳回会把任务退回审核中并重跑；此前直接追加风险项导致同一任务出现重复条目（全链路测试发现）。
ALTER TABLE audit_risk_item ADD COLUMN superseded SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE audit_risk_item
    ADD CONSTRAINT ck_risk_item_superseded CHECK (superseded IN (0, 1));

CREATE INDEX idx_risk_item_task_active ON audit_risk_item (tenant_id, task_id)
    WHERE superseded = 0 AND deleted = 0;
