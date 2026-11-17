-- =============================================================================
-- Flyway 迁移：规则初始集（规范 8.1、8.3、9.2）
-- 版本：V202611142020  描述：seed rule config
-- 回滚方案：DELETE FROM rule_config WHERE owner_name = 'seed' AND rule_code LIKE '%-%'; 或按编码逐条删除。
-- 说明：
--   1. 覆盖规范 9.2 五类条款（付款/违约/保密/管辖/免责）+ 合同级“必备条款缺失”两类；
--   2. 每条规则带正例与反例（jsonb，含完整求值上下文），供 RuleReplayIT 自动回放（规范 8.3）；
--   3. 表达式变量严格限定为规范 8.2 的 6 个 + 负责人 2026-11-14 批准的合同级扩展 presentTypes；
--   4. 租户 1 为开发租户；灰度时把其它租户的 enabled 置 0 试跑（规范 8.3）。
-- =============================================================================

INSERT INTO rule_config (id, tenant_id, rule_code, rule_name, clause_type, scope, condition_expr, risk_level,
                         suggestion_template, enabled, version_no, positive_example, negative_example, owner_name)
SELECT v.id, 1, v.rule_code, v.rule_name, v.clause_type, v.scope, v.condition_expr, v.risk_level,
       v.suggestion_template, 1, 1, v.positive_example::jsonb, v.negative_example::jsonb, 'seed'
FROM (VALUES
-- 付款类（3 条）
(9001, 'PAYMENT.MAX_PAYMENT_DAYS.01', '账期上限 90 天', 'PAYMENT', 'CLAUSE',
 $expr$paymentDays > 90$expr$, 'HIGH',
 '账期超过内部上限 90 天，建议压缩账期或增加履约担保后再签。',
 $json${"clauseText":"乙方应在收到发票后 120 日内付款。","clauseType":"PAYMENT","contractAmount":1200000,"paymentDays":120,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["PAYMENT"],"expected":true}$json$,
 $json${"clauseText":"乙方应在收到发票后 60 日内付款。","clauseType":"PAYMENT","contractAmount":1200000,"paymentDays":60,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["PAYMENT"],"expected":false}$json$),

(9002, 'PAYMENT.INVOICE_TRIGGER.02', '以票期起算放大账期', 'PAYMENT', 'CLAUSE',
 $expr$string.contains(clauseText, "收到发票") && paymentDays > 60$expr$, 'MEDIUM',
 '账期自收到发票起算且超过 60 天，实际回款周期不可控，建议改为固定日历日或缩短账期。',
 $json${"clauseText":"甲方应在收到乙方发票后 75 日内支付全部价款。","clauseType":"PAYMENT","contractAmount":600000,"paymentDays":75,"jurisdiction":"北京市","hasJointLiability":false,"presentTypes":["PAYMENT"],"expected":true}$json$,
 $json${"clauseText":"甲方应在收到乙方发票后 30 日内支付全部价款。","clauseType":"PAYMENT","contractAmount":600000,"paymentDays":30,"jurisdiction":"北京市","hasJointLiability":false,"presentTypes":["PAYMENT"],"expected":false}$json$),

(9003, 'PAYMENT.LARGE_AMOUNT.03', '大额合同需双人会签', 'PAYMENT', 'CLAUSE',
 $expr$contractAmount >= 5000000$expr$, 'MEDIUM',
 '合同金额达到大额标准，需财务与法务双人会签后再出具初审结论。',
 $json${"clauseText":"合同总价为人民币捌佰万元整。","clauseType":"PAYMENT","contractAmount":8000000,"paymentDays":30,"jurisdiction":"广东省深圳市","hasJointLiability":false,"presentTypes":["PAYMENT"],"expected":true}$json$,
 $json${"clauseText":"合同总价为人民币壹佰万元整。","clauseType":"PAYMENT","contractAmount":1000000,"paymentDays":30,"jurisdiction":"广东省深圳市","hasJointLiability":false,"presentTypes":["PAYMENT"],"expected":false}$json$),

-- 违约责任类（3 条）
(9004, 'BREACH.UNLIMITED_JOINT_LIABILITY.01', '无限连带责任', 'BREACH', 'CLAUSE',
 $expr$hasJointLiability && string.contains(clauseText, "无限")$expr$, 'HIGH',
 '出现无限连带责任约定，责任范围不封顶，须人工复核后决定是否接受。',
 $json${"clauseText":"丙方对乙方全部债务承担无限连带责任。","clauseType":"BREACH","contractAmount":2000000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":true,"presentTypes":["BREACH"],"expected":true}$json$,
 $json${"clauseText":"丙方对乙方债务在担保范围内承担连带责任。","clauseType":"BREACH","contractAmount":2000000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":true,"presentTypes":["BREACH"],"expected":false}$json$),

(9005, 'BREACH.NO_LIABILITY_CAP.02', '赔偿无上限', 'BREACH', 'CLAUSE',
 $expr$string.contains(clauseText, "赔偿") && !string.contains(clauseText, "上限") && !string.contains(clauseText, "不超过")$expr$, 'HIGH',
 '赔偿责任未设上限，建议补充“累计赔偿不超过合同总价”的封顶条款。',
 $json${"clauseText":"因乙方违约给甲方造成损失的，乙方应赔偿由此产生的全部损失。","clauseType":"BREACH","contractAmount":1500000,"paymentDays":30,"jurisdiction":"江苏省南京市","hasJointLiability":false,"presentTypes":["BREACH"],"expected":true}$json$,
 $json${"clauseText":"乙方累计赔偿总额不超过合同总价的百分之三十。","clauseType":"BREACH","contractAmount":1500000,"paymentDays":30,"jurisdiction":"江苏省南京市","hasJointLiability":false,"presentTypes":["BREACH"],"expected":false}$json$),

(9006, 'BREACH.UNILATERAL_TERMINATION.03', '单方解除权', 'BREACH', 'CLAUSE',
 $expr$string.contains(clauseText, "单方解除")$expr$, 'MEDIUM',
 '约定单方解除权，需确认解除条件是否对等、是否设置通知期。',
 $json${"clauseText":"甲方有权在任何时候单方解除本合同且无需承担违约责任。","clauseType":"BREACH","contractAmount":900000,"paymentDays":15,"jurisdiction":"浙江省杭州市","hasJointLiability":false,"presentTypes":["BREACH"],"expected":true}$json$,
 $json${"clauseText":"经双方书面协商一致，可以解除本合同。","clauseType":"BREACH","contractAmount":900000,"paymentDays":15,"jurisdiction":"浙江省杭州市","hasJointLiability":false,"presentTypes":["BREACH"],"expected":false}$json$),

-- 保密类（2 条）
(9007, 'CONFIDENTIALITY.NO_DURATION.01', '保密期限未约定', 'CONFIDENTIALITY', 'CLAUSE',
 $expr$!string.contains(clauseText, "年") && !string.contains(clauseText, "永久")$expr$, 'MEDIUM',
 '保密义务未约定期限，建议明确“合同终止后 3 年内持续有效”。',
 $json${"clauseText":"双方应对本合同内容及履行过程中知悉的信息承担保密义务。","clauseType":"CONFIDENTIALITY","contractAmount":500000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["CONFIDENTIALITY"],"expected":true}$json$,
 $json${"clauseText":"保密义务自签署之日起生效，合同终止后 3 年内持续有效。","clauseType":"CONFIDENTIALITY","contractAmount":500000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["CONFIDENTIALITY"],"expected":false}$json$),

(9008, 'CONFIDENTIALITY.NO_EXCEPTION.02', '缺少公开信息例外', 'CONFIDENTIALITY', 'CLAUSE',
 $expr$!string.contains(clauseText, "例外") && !string.contains(clauseText, "已公开") && !string.contains(clauseText, "公开信息")$expr$, 'LOW',
 '保密条款未排除已公开信息，建议补充法定例外情形，避免义务范围过宽。',
 $json${"clauseText":"接收方不得向任何第三方披露保密信息。","clauseType":"CONFIDENTIALITY","contractAmount":300000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["CONFIDENTIALITY"],"expected":true}$json$,
 $json${"clauseText":"接收方不得披露保密信息，但已公开信息与依法应披露的信息除外。","clauseType":"CONFIDENTIALITY","contractAmount":300000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["CONFIDENTIALITY"],"expected":false}$json$),

-- 管辖类（3 条）
(9009, 'JURISDICTION.FOREIGN_COURT.01', '境外法院管辖', 'JURISDICTION', 'CLAUSE',
 $expr$string.contains(clauseText, "境外") || string.contains(clauseText, "外国法院")$expr$, 'HIGH',
 '约定境外法院管辖，诉讼成本与执行难度显著上升，须人工复核。',
 $json${"clauseText":"因本合同引起的争议，由境外有管辖权的法院管辖。","clauseType":"JURISDICTION","contractAmount":4000000,"paymentDays":30,"jurisdiction":"境外","hasJointLiability":false,"presentTypes":["JURISDICTION"],"expected":true}$json$,
 $json${"clauseText":"因本合同引起的争议，由合同签订地人民法院管辖。","clauseType":"JURISDICTION","contractAmount":4000000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["JURISDICTION"],"expected":false}$json$),

(9010, 'JURISDICTION.DEFENDANT_SEAT.02', '被告所在地管辖', 'JURISDICTION', 'CLAUSE',
 $expr$string.contains(clauseText, "被告所在地")$expr$, 'MEDIUM',
 '管辖地随被告变化，己方作为原告时不确定，建议改为原告所在地或合同签订地。',
 $json${"clauseText":"争议由被告所在地人民法院管辖。","clauseType":"JURISDICTION","contractAmount":700000,"paymentDays":30,"jurisdiction":"被告所在地","hasJointLiability":false,"presentTypes":["JURISDICTION"],"expected":true}$json$,
 $json${"clauseText":"争议由原告所在地人民法院管辖。","clauseType":"JURISDICTION","contractAmount":700000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["JURISDICTION"],"expected":false}$json$),

(9011, 'JURISDICTION.FOREIGN_ARBITRATION.03', '境外仲裁', 'JURISDICTION', 'CLAUSE',
 $expr$string.contains(clauseText, "境外仲裁") || string.contains(clauseText, "新加坡仲裁")$expr$, 'HIGH',
 '约定境外仲裁机构，费用与周期不可控，须人工复核。',
 $json${"clauseText":"争议提交新加坡仲裁中心仲裁解决。","clauseType":"JURISDICTION","contractAmount":6000000,"paymentDays":30,"jurisdiction":"新加坡","hasJointLiability":false,"presentTypes":["JURISDICTION"],"expected":true}$json$,
 $json${"clauseText":"争议提交上海国际经济贸易仲裁委员会仲裁解决。","clauseType":"JURISDICTION","contractAmount":6000000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["JURISDICTION"],"expected":false}$json$),

-- 免责类（2 条）
(9012, 'DISCLAIMER.EXCLUDE_ALL_LIABILITY.01', '完全免责', 'DISCLAIMER', 'CLAUSE',
 $expr$string.contains(clauseText, "不承担任何")$expr$, 'HIGH',
 '约定完全免除一方责任，属责任无限扩大/排除主要义务，须人工复核。',
 $json${"clauseText":"无论何种原因，甲方均不承担任何赔偿责任。","clauseType":"DISCLAIMER","contractAmount":1100000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["DISCLAIMER"],"expected":true}$json$,
 $json${"clauseText":"因甲方重大过失造成的损失，甲方仍应承担相应赔偿责任。","clauseType":"DISCLAIMER","contractAmount":1100000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["DISCLAIMER"],"expected":false}$json$),

(9013, 'DISCLAIMER.BROAD_FORCE_MAJEURE.02', '不可抗力范围过宽', 'DISCLAIMER', 'CLAUSE',
 $expr$string.contains(clauseText, "不可抗力") && string.contains(clauseText, "包括但不限于")$expr$, 'MEDIUM',
 '以“包括但不限于”扩张不可抗力范围，可能被用于规避履约责任，建议改为封闭列举。',
 $json${"clauseText":"不可抗力包括但不限于政策调整、市场变化、供应链中断等情形。","clauseType":"DISCLAIMER","contractAmount":800000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["DISCLAIMER"],"expected":true}$json$,
 $json${"clauseText":"不可抗力指地震、洪水、战争等不能预见、不能避免且不能克服的客观情况。","clauseType":"DISCLAIMER","contractAmount":800000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["DISCLAIMER"],"expected":false}$json$),

-- 合同级必备条款缺失（2 条；presentTypes 为本次切分得到的条款类型集合）
(9014, 'CONTRACT.MISSING.CONFIDENTIALITY.01', '缺少保密条款', 'CONTRACT', 'CONTRACT',
 $expr$!include(presentTypes, "CONFIDENTIALITY")$expr$, 'MEDIUM',
 '全篇未识别到保密条款，建议补充；若确无保密需求，请人工确认后关闭本条。',
 $json${"clauseText":"","clauseType":"CONTRACT","contractAmount":500000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["PAYMENT","BREACH"],"expected":true}$json$,
 $json${"clauseText":"","clauseType":"CONTRACT","contractAmount":500000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["PAYMENT","CONFIDENTIALITY"],"expected":false}$json$),

(9015, 'CONTRACT.MISSING.JURISDICTION.02', '缺少争议解决条款', 'CONTRACT', 'CONTRACT',
 $expr$!include(presentTypes, "JURISDICTION")$expr$, 'MEDIUM',
 '全篇未识别到争议解决/管辖条款，建议补充明确的管辖法院或仲裁机构。',
 $json${"clauseText":"","clauseType":"CONTRACT","contractAmount":500000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["PAYMENT","BREACH"],"expected":true}$json$,
 $json${"clauseText":"","clauseType":"CONTRACT","contractAmount":500000,"paymentDays":30,"jurisdiction":"上海市","hasJointLiability":false,"presentTypes":["PAYMENT","JURISDICTION"],"expected":false}$json$)
) AS v(id, rule_code, rule_name, clause_type, scope, condition_expr, risk_level, suggestion_template,
       positive_example, negative_example)
WHERE NOT EXISTS (
    SELECT 1 FROM rule_config r WHERE r.tenant_id = 1 AND r.rule_code = v.rule_code AND r.deleted = 0
);

-- =============================================================================
-- DOWN：DELETE FROM rule_config WHERE tenant_id = 1 AND rule_code IN (...);
-- =============================================================================
