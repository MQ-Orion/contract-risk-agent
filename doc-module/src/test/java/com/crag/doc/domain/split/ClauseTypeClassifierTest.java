package com.crag.doc.domain.split;

import com.crag.common.text.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.text.ClauseTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 条款类型判定测试（规范 5.3）。
 */
class ClauseTypeClassifierTest {

    private final ClauseTypeClassifier classifier = new ClauseTypeClassifier();

    @Test
    @DisplayName("标题命中关键词即定类型")
    void classifiesByHeadline() {
        assertThat(classifier.classify("第三条 保密", "双方应保守商业秘密。").clauseType())
                .isEqualTo(ClauseTypeEnum.CONFIDENTIALITY);
        assertThat(classifier.classify("第四条 管辖", "由甲方所在地法院管辖。").clauseType())
                .isEqualTo(ClauseTypeEnum.JURISDICTION);
    }

    @Test
    @DisplayName("标题未命中时回落到正文关键词")
    void classifiesByBody() {
        TypeClassification result = classifier.classify("第七条 其他", "乙方逾期交付的，应按日支付违约金。");

        assertThat(result.clauseType()).isEqualTo(ClauseTypeEnum.BREACH);
        assertThat(result.typeSource()).isEqualTo(com.crag.common.text.TypeSourceEnum.KEYWORD);
    }

    @Test
    @DisplayName("多类型同时命中时按固定优先级取第一个")
    void honoursPriorityOrder() {
        TypeClassification result = classifier.classify("付款与违约", "涉及付款与违约金。");

        assertThat(result.clauseType()).isEqualTo(ClauseTypeEnum.BREACH);
    }

    @Test
    @DisplayName("未命中任何关键词时置为其他并要求模型兜底")
    void fallsBackToOther() {
        TypeClassification result = classifier.classify("第一条 定义", "本合同中相关术语的含义如下。");

        assertThat(result.clauseType()).isEqualTo(ClauseTypeEnum.OTHER);
        assertThat(result.requiresModelFallback()).isTrue();
    }

    @Test
    @DisplayName("空输入不产生异常")
    void handlesBlankInput() {
        assertThat(classifier.classify(null, null).clauseType()).isEqualTo(ClauseTypeEnum.OTHER);
        assertThat(classifier.classify("", "  ").clauseType()).isEqualTo(ClauseTypeEnum.OTHER);
    }

    @Test
    @DisplayName("连带责任/担保责任归入违约责任（全链路回归：担保条款规则可命中）")
    void classifiesJointLiabilityAsBreach() {
        TypeClassification result = classifier.classify("第五条 担保责任",
                "丙方对乙方在本合同项下的全部债务承担无限连带责任。");

        assertThat(result.clauseType()).isEqualTo(ClauseTypeEnum.BREACH);
    }
}