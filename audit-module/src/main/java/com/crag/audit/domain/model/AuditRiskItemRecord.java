package com.crag.audit.domain.model;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.RiskLevelEnum;
import java.time.Instant;

/**
 * 风险明细记录（表 audit_risk_item，规范 4.2、9.2、10.7）。
 *
 * <p>{@code versionNo} 是乐观锁：复核请求必须携带期望版本，更新时以 {@code version_no = expected} 为条件，
 * 避免两个复核人互相覆盖结论（规范 10.7）。</p>
 *
 * @param id                风险项标识
 * @param tenantId          租户
 * @param taskId            任务标识
 * @param riskLevel         风险等级
 * @param riskContent       风险说明
 * @param suggestion        建议
 * @param source            来源：RULE / MODEL / RULE_CONFLICT
 * @param ruleCode          规则编码
 * @param ruleVersion       规则版本
 * @param sourceDocName     来源文档名
 * @param sourceChunkKey    来源切片定位
 * @param sourceExcerpt     引用原文
 * @param citationVerified  引用是否回验通过
 * @param clauseNo          条款序号
 * @param clauseType        条款类型
 * @param clauseText        条款原文（回流用）
 * @param retrievalSnapshot 召回片段快照 JSON（回流用）
 * @param reviewed          是否已复核
 * @param reviewResult      复核结果
 * @param reviewedBy        复核人
 * @param reviewedAt        复核时间
 * @param versionNo         乐观锁版本
 */
public record AuditRiskItemRecord(long id,
                                  long tenantId,
                                  long taskId,
                                  RiskLevelEnum riskLevel,
                                  String riskContent,
                                  String suggestion,
                                  String source,
                                  String ruleCode,
                                  Integer ruleVersion,
                                  String sourceDocName,
                                  String sourceChunkKey,
                                  String sourceExcerpt,
                                  boolean citationVerified,
                                  String clauseNo,
                                  String clauseType,
                                  String clauseText,
                                  String retrievalSnapshot,
                                  boolean reviewed,
                                  ReviewActionEnum reviewResult,
                                  Long reviewedBy,
                                  Instant reviewedAt,
                                  int versionNo) {

    /**
     * 复核：返回新记录，版本号 +1。
     *
     * @param action   复核动作
     * @param reviewer 复核人
     * @param now      当前时间
     * @return 新记录
     */
    public AuditRiskItemRecord reviewed(ReviewActionEnum action, long reviewer, Instant now) {
        if (action == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "review action required");
        }
        return new AuditRiskItemRecord(id, tenantId, taskId, riskLevel, riskContent, suggestion, source, ruleCode,
                ruleVersion, sourceDocName, sourceChunkKey, sourceExcerpt, citationVerified, clauseNo, clauseType,
                clauseText, retrievalSnapshot, true, action, reviewer, now, versionNo + 1);
    }

    /**
     * 是否必须人工复核后才能完成任务（高/中风险未复核）。
     *
     * @return 需要复核返回 true
     */
    public boolean blocksCompletion() {
        return !reviewed && riskLevel.requiresManualReview();
    }
}
