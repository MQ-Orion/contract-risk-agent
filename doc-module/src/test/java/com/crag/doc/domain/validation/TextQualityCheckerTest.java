package com.crag.doc.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 文本质量校验测试（规范 5.2：空文本或乱码占比超过 5% 判为解析失败）。
 */
class TextQualityCheckerTest {

    private final TextQualityChecker checker = new TextQualityChecker();

    @Test
    @DisplayName("正常合同文本通过")
    void acceptsNormalText() {
        QualityReport report = checker.check("第一条 付款方式\n甲方应在十日内支付预付款。");

        assertThat(report.acceptable()).isTrue();
        assertThat(report.suspiciousCharRatio()).isZero();
    }

    @Test
    @DisplayName("空文本判为失败")
    void rejectsEmptyText() {
        assertThat(checker.check("").acceptable()).isFalse();
        assertThat(checker.check("  \n\t ").acceptable()).isFalse();
        assertThat(checker.check(null).acceptable()).isFalse();
    }

    @Test
    @DisplayName("乱码占比超过 5% 判为失败")
    void rejectsGarbageRatioAboveThreshold() {
        String garbage = "\uFFFD".repeat(10) + "正常文本".repeat(20);

        QualityReport report = checker.check(garbage);

        assertThat(report.acceptable()).isFalse();
        assertThat(report.suspiciousCharRatio()).isGreaterThan(TextQualityChecker.MAX_SUSPICIOUS_CHAR_RATIO);
    }

    @Test
    @DisplayName("乱码占比低于阈值时通过")
    void acceptsLowGarbageRatio() {
        String text = "\uFFFD" + "正常合同条款内容".repeat(100);

        QualityReport report = checker.check(text);

        assertThat(report.acceptable()).isTrue();
        assertThat(report.suspiciousCharRatio()).isLessThanOrEqualTo(TextQualityChecker.MAX_SUSPICIOUS_CHAR_RATIO);
    }

    @Test
    @DisplayName("私用区字符计入可疑字符")
    void countsPrivateUseCharacters() {
        String text = "\uE001\uE002" + "条款".repeat(10);

        assertThat(checker.check(text).acceptable()).isFalse();
    }
}
