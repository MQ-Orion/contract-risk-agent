package com.crag.agent.rule.application;

import com.crag.agent.rule.domain.RuleMatch;
import java.util.List;

/**
 * 规则求值结果。
 *
 * @param matches         命中规则，按风险等级与规则编码排序（结果稳定，便于回归比对）
 * @param failedRuleCodes 求值失败的规则编码；失败不阻断其余规则，但必须计数并在报告中标注
 */
public record RuleEvaluationResult(List<RuleMatch> matches, List<String> failedRuleCodes) {

    /**
     * 构造结果，列表做不可变复制。
     */
    public RuleEvaluationResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
        failedRuleCodes = failedRuleCodes == null ? List.of() : List.copyOf(failedRuleCodes);
    }

    /**
     * 空结果。
     *
     * @return 空结果
     */
    public static RuleEvaluationResult empty() {
        return new RuleEvaluationResult(List.of(), List.of());
    }

    /**
     * 是否发生过规则执行失败。
     *
     * @return 有失败返回 true
     */
    public boolean degraded() {
        return !failedRuleCodes.isEmpty();
    }
}
