package com.crag.agent.review;

import com.crag.common.domain.RiskLevelEnum;
import java.util.List;

/**
 * 模型风险判断结果（规范 7.4、7.5）。
 *
 * @param level           风险等级，NONE 表示未发现
 * @param reason          判断依据
 * @param suggestion      建议
 * @param citations       引用
 * @param citationValid   引用是否全部通过校验（切片属于本次召回 + 原文逐字存在）
 * @param validationError 校验失败原因
 * @param tokensUsed      本次判断消耗的模型 token
 */
public record ModelJudgement(RiskLevelEnum level,
                             String reason,
                             String suggestion,
                             List<Citation> citations,
                             boolean citationValid,
                             String validationError,
                             int tokensUsed) {

    /**
     * 构造结果，引用列表做不可变复制。
     */
    public ModelJudgement {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    /**
     * 是否命中风险。
     *
     * @return 非 NONE 返回 true
     */
    public boolean hasRisk() {
        return level != null;
    }

    /**
     * 引用项。
     *
     * @param chunkKey 切片定位
     * @param excerpt  引用原文
     */
    public record Citation(String chunkKey, String excerpt) {
    }
}
