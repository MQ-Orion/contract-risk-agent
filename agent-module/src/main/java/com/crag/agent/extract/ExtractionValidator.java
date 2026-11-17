package com.crag.agent.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;

/**
 * 抽取输出校验器（规范 7.4）：字段完整性、类型、数值范围。
 *
 * <p>只做结构校验，不做“容错解析”：格式不对就报错并由上层重试一次，禁止用正则从自由文本里抠字段——
 * 抠出来的值无法证明来自原文，等于把格式错误变成数据错误。</p>
 *
 * <p>数值口径：金额 [0, 1e12] 元；账期 [0, 3650] 天。超出范围视为格式错误，因为这类值几乎必然是
 * 单位错误（万元当成元）或解析错误，静默接受会直接污染风险规则。</p>
 */
public class ExtractionValidator {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final int MAX_PAYMENT_DAYS = 3650;
    private static final List<String> FIELDS = List.of("contractAmount", "paymentDays", "jurisdiction",
            "hasJointLiability");

    private final ObjectMapper objectMapper;

    /**
     * 构造校验器。
     *
     * @param objectMapper JSON 解析
     */
    public ExtractionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析模型输出。
     *
     * @param raw 模型输出文本
     * @return 候选结果
     * @throws ExtractionFormatException 结构或取值非法
     */
    public ExtractionCandidate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ExtractionFormatException("empty output");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new ExtractionFormatException("invalid json");
        }
        if (!root.isObject()) {
            throw new ExtractionFormatException("root must be object");
        }
        BigDecimal amount = null;
        String amountSpan = null;
        Integer days = null;
        String daysSpan = null;
        String jurisdiction = null;
        String jurisdictionSpan = null;
        Boolean joint = null;
        String jointSpan = null;

        JsonNode amountNode = root.get("contractAmount");
        if (isPresent(amountNode)) {
            amount = readAmount(amountNode);
            amountSpan = readSpan(amountNode, "contractAmount");
        }
        JsonNode daysNode = root.get("paymentDays");
        if (isPresent(daysNode)) {
            days = readDays(daysNode);
            daysSpan = readSpan(daysNode, "paymentDays");
        }
        JsonNode jurisdictionNode = root.get("jurisdiction");
        if (isPresent(jurisdictionNode)) {
            jurisdiction = readText(jurisdictionNode, "jurisdiction");
            jurisdictionSpan = readSpan(jurisdictionNode, "jurisdiction");
        }
        JsonNode jointNode = root.get("hasJointLiability");
        if (isPresent(jointNode)) {
            joint = readBoolean(jointNode);
            jointSpan = readSpan(jointNode, "hasJointLiability");
        }
        return new ExtractionCandidate(amount, amountSpan, days, daysSpan, jurisdiction, jurisdictionSpan, joint,
                jointSpan);
    }

    private static boolean isPresent(JsonNode node) {
        return node != null && !node.isNull();
    }

    private BigDecimal readAmount(JsonNode node) {
        JsonNode value = node.get("value");
        if (value == null || value.isNull()) {
            return null;
        }
        BigDecimal amount;
        try {
            amount = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
        } catch (NumberFormatException e) {
            throw new ExtractionFormatException("contractAmount must be numeric");
        }
        if (amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ExtractionFormatException("contractAmount out of range");
        }
        return amount;
    }

    private Integer readDays(JsonNode node) {
        JsonNode value = node.get("value");
        if (value == null || value.isNull()) {
            return null;
        }
        int days;
        try {
            days = value.isNumber() ? value.intValue() : Integer.parseInt(value.asText().trim());
        } catch (NumberFormatException e) {
            throw new ExtractionFormatException("paymentDays must be integer");
        }
        if (days < 0 || days > MAX_PAYMENT_DAYS) {
            throw new ExtractionFormatException("paymentDays out of range");
        }
        return days;
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.get("value");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ExtractionFormatException(field + " must be text");
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > 128) {
            throw new ExtractionFormatException(field + " too long");
        }
        return text;
    }

    private Boolean readBoolean(JsonNode node) {
        JsonNode value = node.get("value");
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        String text = value.asText().trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        throw new ExtractionFormatException("hasJointLiability must be boolean");
    }

    private static String readSpan(JsonNode node, String field) {
        JsonNode span = node.get("span");
        if (span == null || span.isNull()) {
            return null;
        }
        if (!span.isTextual()) {
            throw new ExtractionFormatException(field + ".span must be text");
        }
        String text = span.asText().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 候选字段值（未经原文回验）。
     *
     * @param contractAmount      金额
     * @param contractAmountSpan  金额片段
     * @param paymentDays         账期
     * @param paymentDaysSpan     账期片段
     * @param jurisdiction        管辖地
     * @param jurisdictionSpan    管辖地片段
     * @param hasJointLiability   是否含连带责任
     * @param hasJointLiabilitySpan 连带责任片段
     */
    public record ExtractionCandidate(BigDecimal contractAmount,
                                      String contractAmountSpan,
                                      Integer paymentDays,
                                      String paymentDaysSpan,
                                      String jurisdiction,
                                      String jurisdictionSpan,
                                      Boolean hasJointLiability,
                                      String hasJointLiabilitySpan) {

        /**
         * 全部字段名，供回验遍历。
         *
         * @return 字段名列表
         */
        public static List<String> fieldNames() {
            return FIELDS;
        }
    }
}
