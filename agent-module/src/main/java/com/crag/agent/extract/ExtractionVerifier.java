package com.crag.agent.extract;

import com.crag.common.text.TextNormalizer;
import com.crag.doc.domain.model.ExtractStatusEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽取字段原文回验（规范 7.5 第三级防线、12.3 抽取回验通过率）。
 *
 * <p>口径：每个有值字段必须带 {@code span}，归一化后逐字存在于本次送入模型的条款原文中；
 * 缺片段 → {@code span_missing}，找不到 → {@code span_not_found}，两者都不落库该字段值（避免规则用错误值），
 * 并计入回验统计的分母。</p>
 *
 * <p>为什么归一化：解析产生的空格、换行、全半角与标点差异会让正确引用误判失败（见 {@link TextNormalizer}），
 * 展示时仍使用原始片段，归一化只服务于校验。</p>
 *
 * <p>线程安全性：无可变状态（ObjectMapper 线程安全），可共享。</p>
 */
public class ExtractionVerifier {

    private final ObjectMapper objectMapper;

    /**
     * 构造校验器。
     *
     * @param objectMapper JSON 序列化
     */
    public ExtractionVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 逐字段回验并组装结果。
     *
     * @param candidate     模型输出
     * @param sourceText    本次送入模型的条款原文拼接
     * @param promptVersion Prompt 版本
     * @param modelName     模型名
     * @param rawResponse   原始返回（截断由调用方负责）
     * @param tokens        本次消耗 token
     * @return 回验后的结果
     */
    public ExtractionResult verify(ExtractionValidator.ExtractionCandidate candidate, String sourceText,
                                   String promptVersion, String modelName, String rawResponse, int tokens) {
        List<ExtractionField> fields = new ArrayList<>();
        fields.add(verifyField("contractAmount", candidate.contractAmount(), candidate.contractAmountSpan(),
                sourceText));
        fields.add(verifyField("paymentDays", candidate.paymentDays(), candidate.paymentDaysSpan(), sourceText));
        fields.add(verifyField("jurisdiction", candidate.jurisdiction(), candidate.jurisdictionSpan(), sourceText));
        fields.add(verifyField("hasJointLiability", candidate.hasJointLiability(),
                candidate.hasJointLiabilitySpan(), sourceText));

        boolean pending = fields.stream().anyMatch(ExtractionField::pendingManual);
        ExtractStatusEnum status = pending ? ExtractStatusEnum.PENDING_MANUAL : ExtractStatusEnum.VERIFIED;
        return new ExtractionResult(
                verifiedValue(fields, "contractAmount", BigDecimal.class),
                verifiedValue(fields, "paymentDays", Integer.class),
                verifiedValue(fields, "jurisdiction", String.class),
                verifiedValue(fields, "hasJointLiability", Boolean.class),
                status, fields, toSpanJson(fields), rawResponse, promptVersion, modelName, false, null, tokens);
    }

    /**
     * 单字段回验。
     *
     * @param name       字段名
     * @param value      值
     * @param span       片段
     * @param sourceText 原文
     * @return 字段结果
     */
    public ExtractionField verifyField(String name, Object value, String span, String sourceText) {
        if (value == null) {
            return new ExtractionField(name, null, null, false, "absent");
        }
        if (span == null || span.isBlank()) {
            return new ExtractionField(name, value, null, false, "span_missing");
        }
        if (!TextNormalizer.containsNormalized(sourceText, span)) {
            return new ExtractionField(name, value, span, false, "span_not_found");
        }
        return new ExtractionField(name, value, span, true, null);
    }

    private static <T> T verifiedValue(List<ExtractionField> fields, String name, Class<T> type) {
        for (ExtractionField field : fields) {
            if (field.name().equals(name) && field.usable()) {
                return type.cast(field.value());
            }
        }
        return null;
    }

    /**
     * 生成 source_span_json：每字段的值、片段、是否通过、失败原因。
     *
     * @param fields 字段明细
     * @return JSON 文本
     */
    public String toSpanJson(List<ExtractionField> fields) {
        ObjectNode root = objectMapper.createObjectNode();
        for (ExtractionField field : fields) {
            ObjectNode node = root.putObject(field.name());
            if (field.value() == null) {
                node.putNull("value");
            } else if (field.value() instanceof BigDecimal amount) {
                node.put("value", amount);
            } else if (field.value() instanceof Integer number) {
                node.put("value", number);
            } else if (field.value() instanceof Boolean flag) {
                node.put("value", flag);
            } else {
                node.put("value", String.valueOf(field.value()));
            }
            node.put("span", field.span());
            node.put("verified", field.verified());
            node.put("reason", field.reason());
        }
        return root.toString();
    }
}
