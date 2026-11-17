package com.crag.common.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条款类型判定：标题关键词命中即定，未命中交由模型兜底（规范 5.3）。
 *
 * <p>判定顺序固定为 违约责任、保密、付款、管辖、免责、其他；同一文本命中多个类型时按该顺序取第一个，
 * 保证同一输入在任何机器上得到同一结果。关键词表为配置化前的基线，后续迁入规则配置表。</p>
 *
 * <p>本类只做确定性文字匹配，不调用模型：doc-module 禁止调用 LLM（规范 2.1），
 * 模型兜底由 agent-module 通过 {@code ClauseTypeFallbackPort} 注入。</p>
 */
public final class ClauseTypeClassifier {

    /** 关键词表：保持与六类枚举一致的判定顺序。 */
    private static final Map<ClauseTypeEnum, List<String>> KEYWORDS = buildKeywords();

    /**
     * 判定条款类型。
     *
     * <p>先标题后正文的理由：标题是作者对条款类别的显式声明，正文只是佐证；若先扫正文，
     * “违约责任”条款常含“逾期付款”字样，会被误判为付款类，进而影响规则适用范围（规范 5.3、8.1）。</p>
     *
     * @param headline 条款标题行，可为空
     * @param body     条款正文，可为空
     * @return 类型与来源
     */
    public TypeClassification classify(String headline, String body) {
        ClauseTypeEnum fromHeadline = match(headline);
        if (fromHeadline != null) {
            return new TypeClassification(fromHeadline, TypeSourceEnum.KEYWORD);
        }
        ClauseTypeEnum fromBody = match(body);
        if (fromBody != null) {
            return new TypeClassification(fromBody, TypeSourceEnum.KEYWORD);
        }
        return new TypeClassification(ClauseTypeEnum.OTHER, TypeSourceEnum.DEFAULT);
    }

    private ClauseTypeEnum match(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (Map.Entry<ClauseTypeEnum, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static Map<ClauseTypeEnum, List<String>> buildKeywords() {
        Map<ClauseTypeEnum, List<String>> keywords = new LinkedHashMap<>();
        keywords.put(ClauseTypeEnum.BREACH, List.of("违约", "赔偿责任", "违约金", "损失赔偿", "逾期付款", "解除合同",
            "连带责任", "担保责任"));
        keywords.put(ClauseTypeEnum.CONFIDENTIALITY, List.of("保密", "商业秘密", "机密信息", "保密义务"));
        keywords.put(ClauseTypeEnum.PAYMENT, List.of("付款", "支付", "价款", "账期", "结算", "发票", "预付款", "尾款", "货款"));
        keywords.put(ClauseTypeEnum.JURISDICTION, List.of("管辖", "争议解决", "仲裁", "诉讼", "法院"));
        keywords.put(ClauseTypeEnum.DISCLAIMER, List.of("免责", "不可抗力", "责任限制", "不承担"));
        // 必须保持判定顺序，不能用 Map.copyOf（无序）
        return Collections.unmodifiableMap(keywords);
    }
}
