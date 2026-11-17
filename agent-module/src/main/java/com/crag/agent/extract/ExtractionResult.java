package com.crag.agent.extract;

import com.crag.doc.domain.model.ExtractStatusEnum;
import java.math.BigDecimal;
import java.util.List;

/**
 * 抽取结果。
 *
 * @param contractAmount    合同金额（仅当回验通过时非 null）
 * @param paymentDays       账期天数（仅当回验通过时非 null）
 * @param jurisdiction      管辖地（仅当回验通过时非 null）
 * @param hasJointLiability 是否含连带责任（仅当回验通过时非 null）
 * @param status            抽取状态
 * @param fields            字段明细（含待人工确认项）
 * @param sourceSpanJson    写入 source_span_json 的 JSON
 * @param modelRawResponse  模型原始返回（截断）
 * @param promptVersion     Prompt 版本
 * @param modelName         模型名
 * @param degraded          是否模型降级
 * @param degradeReason     降级原因
 * @param totalTokens       本次抽取消耗的模型 token（用于单合同预算）
 */
public record ExtractionResult(BigDecimal contractAmount,
                               Integer paymentDays,
                               String jurisdiction,
                               Boolean hasJointLiability,
                               ExtractStatusEnum status,
                               List<ExtractionField> fields,
                               String sourceSpanJson,
                               String modelRawResponse,
                               String promptVersion,
                               String modelName,
                               boolean degraded,
                               String degradeReason,
                               int totalTokens) {

    /**
     * 构造结果，字段列表做不可变复制。
     */
    public ExtractionResult {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /**
     * 有值字段总数。
     *
     * @return 数量
     */
    public int valueCount() {
        return (int) fields.stream().filter(field -> field.value() != null).count();
    }

    /**
     * 回验通过字段数。
     *
     * @return 数量
     */
    public int verifiedCount() {
        return (int) fields.stream().filter(ExtractionField::usable).count();
    }

    /**
     * 回验通过率（分母为“有值字段数”）。
     *
     * @return 0–1；无有值字段时返回 1
     */
    public double verifiedRate() {
        int total = valueCount();
        return total == 0 ? 1D : (double) verifiedCount() / total;
    }

    /**
     * 按字段名取明细。
     *
     * @param name 字段名
     * @return 明细，不存在返回 null
     */
    public ExtractionField field(String name) {
        for (ExtractionField field : fields) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        return null;
    }
}
