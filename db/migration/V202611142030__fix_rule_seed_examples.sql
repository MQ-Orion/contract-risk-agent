-- =============================================================================
-- Flyway 迁移：修正规则正例文本（规范 8.3 回放用例）
-- 版本：V202611142030  描述：fix rule seed examples
-- 回滚方案：把 PAYMENT.INVOICE_TRIGGER.02 的 positive_example 恢复为种子值（见文件末注释）。
-- 原因：种子正例写成“收到乙方发票”，而表达式匹配“收到发票”，回放用例因此不命中。
--       已执行的迁移不得修改（规范 4.5），故以新迁移修正数据。
-- =============================================================================

UPDATE rule_config
SET positive_example = $json${"clauseText":"甲方应在收到发票后 75 日内支付全部价款。","clauseType":"PAYMENT","contractAmount":600000,"paymentDays":75,"jurisdiction":"北京市","hasJointLiability":false,"presentTypes":["PAYMENT"],"expected":true}$json$,
    updated_at = now()
WHERE tenant_id = 1
  AND rule_code = 'PAYMENT.INVOICE_TRIGGER.02'
  AND deleted = 0;

-- =============================================================================
-- DOWN：恢复为原始种子值
-- UPDATE rule_config SET positive_example = $json${"clauseText":"甲方应在收到乙方发票后 75 日内支付全部价款。",...}$json$ ...
-- =============================================================================
