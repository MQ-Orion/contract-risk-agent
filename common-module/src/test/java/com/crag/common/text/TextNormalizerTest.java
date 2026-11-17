package com.crag.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引用归一化校验测试（规范 7.5）：空白、全半角与标点差异不应造成误判。
 */
class TextNormalizerTest {

    @Test
    @DisplayName("空白与换行差异不导致校验失败")
    void ignoresWhitespaceDiff() {
        String clause = "乙方应在收到发票后 120 日内\n付款。";
        assertThat(TextNormalizer.containsNormalized(clause, "收到发票后120日内付款")).isTrue();
    }

    @Test
    @DisplayName("全角空格与全角标点被统一")
    void unifiesWidthAndPunctuation() {
        String clause = "甲方应支付价款，并承担连带责任。";
        assertThat(TextNormalizer.containsNormalized(clause, "甲方应支付价款,并承担连带责任.")).isTrue();
    }

    @Test
    @DisplayName("空引用一律判失败")
    void emptyExcerptFails() {
        assertThat(TextNormalizer.containsNormalized("任意条款", "   ")).isFalse();
        assertThat(TextNormalizer.containsNormalized("任意条款", null)).isFalse();
    }

    @Test
    @DisplayName("不存在的引用判失败")
    void missingExcerptFails() {
        assertThat(TextNormalizer.containsNormalized("甲方应支付价款", "乙方应支付价款")).isFalse();
    }

    @Test
    @DisplayName("归一化保留大小写")
    void keepsCase() {
        assertThat(TextNormalizer.normalize("Party A")).isEqualTo("PartyA");
        assertThat(TextNormalizer.normalize("party a")).isEqualTo("partya");
    }

    @Test
    @DisplayName("null 与空串归一化为空串")
    void nullSafe() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("")).isEmpty();
    }
}
