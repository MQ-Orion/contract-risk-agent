package com.crag.agent.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 抽取输出校验测试（规范 7.4）：类型、数值范围、缺片段与非法 JSON 的失败路径。
 */
class ExtractionValidatorTest {

    private final ExtractionValidator validator = new ExtractionValidator(new ObjectMapper());

    @Test
    @DisplayName("合法输出：解析出值与片段")
    void parsesValidOutput() {
        ExtractionValidator.ExtractionCandidate candidate = validator.parse("""
                {"contractAmount":{"value":1200000,"span":"合同总价为人民币壹佰贰拾万元整"},
                 "paymentDays":{"value":120,"span":"收到发票后120日内"},
                 "jurisdiction":{"value":"上海市","span":"由上海市浦东新区人民法院管辖"},
                 "hasJointLiability":{"value":true,"span":"承担连带责任"}}
                """);

        assertThat(candidate.contractAmount()).isEqualByComparingTo("1200000");
        assertThat(candidate.paymentDays()).isEqualTo(120);
        assertThat(candidate.jurisdiction()).isEqualTo("上海市");
        assertThat(candidate.hasJointLiability()).isTrue();
        assertThat(candidate.contractAmountSpan()).contains("壹佰贰拾万元");
    }

    @Test
    @DisplayName("字段为 null 视为缺失，不报错")
    void nullFieldsAreAbsent() {
        ExtractionValidator.ExtractionCandidate candidate = validator.parse(
                "{\"contractAmount\":null,\"paymentDays\":null,\"jurisdiction\":null,\"hasJointLiability\":null}");
        assertThat(candidate.contractAmount()).isNull();
        assertThat(candidate.paymentDays()).isNull();
        assertThat(candidate.jurisdiction()).isNull();
        assertThat(candidate.hasJointLiability()).isNull();
    }

    @Test
    @DisplayName("缺片段不报错，由回验阶段判定为待人工确认")
    void missingSpanIsNotFormatError() {
        ExtractionValidator.ExtractionCandidate candidate = validator.parse(
                "{\"paymentDays\":{\"value\":90}}");
        assertThat(candidate.paymentDays()).isEqualTo(90);
        assertThat(candidate.paymentDaysSpan()).isNull();
    }

    @Test
    @DisplayName("金额超出范围判为格式错误（疑似单位错误）")
    void rejectsOutOfRangeAmount() {
        assertThatThrownBy(() -> validator.parse(
                "{\"contractAmount\":{\"value\":9999999999999999,\"span\":\"x\"}}"))
                .isInstanceOf(ExtractionFormatException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    @DisplayName("账期超出 0–3650 天判为格式错误")
    void rejectsOutOfRangePaymentDays() {
        assertThatThrownBy(() -> validator.parse("{\"paymentDays\":{\"value\":9999,\"span\":\"x\"}}"))
                .isInstanceOf(ExtractionFormatException.class);
    }

    @Test
    @DisplayName("非 JSON、根不是对象、类型错误一律判为格式错误")
    void rejectsMalformedOutput() {
        assertThatThrownBy(() -> validator.parse("抱歉，我无法判断"))
                .isInstanceOf(ExtractionFormatException.class);
        assertThatThrownBy(() -> validator.parse("[1,2,3]"))
                .isInstanceOf(ExtractionFormatException.class);
        assertThatThrownBy(() -> validator.parse("{\"hasJointLiability\":{\"value\":\"maybe\"}}"))
                .isInstanceOf(ExtractionFormatException.class);
        assertThatThrownBy(() -> validator.parse(" "))
                .isInstanceOf(ExtractionFormatException.class);
    }

    @Test
    @DisplayName("布尔以字符串给出时按 true/false 接受")
    void acceptsBooleanText() {
        ExtractionValidator.ExtractionCandidate candidate = validator.parse(
                "{\"hasJointLiability\":{\"value\":\"false\"}}");
        assertThat(candidate.hasJointLiability()).isFalse();
    }
}
