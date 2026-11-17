package com.crag.agent.review;

import com.crag.common.domain.RiskLevelEnum;

/**
 * 风险项（报告明细与审核任务风险明细的同一来源，规范 9.2、9.3、9.4）。
 *
 * <p>每条风险必须带来源文档名、切片定位与引用原文；{@code clauseText} 与 {@code retrievalSnapshot}
 * 是为 hard negatives 回流准备的上下文（审核模块不依赖 doc/kb，必须随风险项一起落库，规范 9.4）。</p>
 *
 * @param level             风险等级
 * @param clauseNo          条款序号
 * @param clauseType        条款类型
 * @param title             风险标题（规则名或模型结论摘要）
 * @param content           风险说明
 * @param suggestion        建议
 * @param sourceDocName     来源知识库文档名
 * @param chunkKey          来源切片定位
 * @param excerpt           引用原文
 * @param source            来源：RULE / MODEL / RULE_CONFLICT
 * @param ruleCode          规则编码，模型结论时为空
 * @param ruleVersion       规则版本
 * @param manualRequired    是否必须人工复核
 * @param citationVerified  引用是否通过回验
 * @param clauseText        条款原文（回流用）
 * @param retrievalSnapshot 召回片段快照 JSON（回流用）
 */
public record RiskItem(RiskLevelEnum level,
                       String clauseNo,
                       String clauseType,
                       String title,
                       String content,
                       String suggestion,
                       String sourceDocName,
                       String chunkKey,
                       String excerpt,
                       String source,
                       String ruleCode,
                       Integer ruleVersion,
                       boolean manualRequired,
                       boolean citationVerified,
                       String clauseText,
                       String retrievalSnapshot) {

    /**
     * 高/中风险必须人工复核（规范 9.2）。
     *
     * @return 需要复核返回 true
     */
    public boolean needsReview() {
        return manualRequired || level.requiresManualReview();
    }
}
