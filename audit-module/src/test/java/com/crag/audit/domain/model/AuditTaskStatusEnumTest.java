package com.crag.audit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.common.domain.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审核状态机测试（规范 9.1）：白名单边、唯一回退、终态不可变。
 */
class AuditTaskStatusEnumTest {

    @Test
    @DisplayName("正常路径：创建→审核中→待复核→已完成")
    void happyPath() {
        assertThat(AuditTaskStatusEnum.CREATED.canTransitionTo(AuditTaskStatusEnum.AUDITING)).isTrue();
        assertThat(AuditTaskStatusEnum.AUDITING.canTransitionTo(AuditTaskStatusEnum.PENDING_REVIEW)).isTrue();
        assertThat(AuditTaskStatusEnum.PENDING_REVIEW.canTransitionTo(AuditTaskStatusEnum.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("复核不通过返回审核中是唯一允许的回退")
    void rejectIsTheOnlyRollback() {
        assertThat(AuditTaskStatusEnum.PENDING_REVIEW.canTransitionTo(AuditTaskStatusEnum.AUDITING)).isTrue();
        assertThat(AuditTaskStatusEnum.COMPLETED.canTransitionTo(AuditTaskStatusEnum.AUDITING)).isFalse();
        assertThat(AuditTaskStatusEnum.AUDITING.canTransitionTo(AuditTaskStatusEnum.CREATED)).isFalse();
        assertThat(AuditTaskStatusEnum.PENDING_REVIEW.canTransitionTo(AuditTaskStatusEnum.CREATED)).isFalse();
    }

    @Test
    @DisplayName("禁止跳跃：创建不能直达待复核或完成")
    void forbidsSkipping() {
        assertThat(AuditTaskStatusEnum.CREATED.canTransitionTo(AuditTaskStatusEnum.PENDING_REVIEW)).isFalse();
        assertThat(AuditTaskStatusEnum.CREATED.canTransitionTo(AuditTaskStatusEnum.COMPLETED)).isFalse();
        assertThatThrownBy(() -> AuditTaskStatusEnum.CREATED.checkTransition(AuditTaskStatusEnum.COMPLETED))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("终态不可再流转")
    void terminalStatesAreFinal() {
        for (AuditTaskStatusEnum terminal : new AuditTaskStatusEnum[]{
                AuditTaskStatusEnum.COMPLETED, AuditTaskStatusEnum.FAILED, AuditTaskStatusEnum.CANCELLED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (AuditTaskStatusEnum target : AuditTaskStatusEnum.values()) {
                assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
            }
        }
    }

    @Test
    @DisplayName("只有待复核状态可复核；驳回必须带原因码")
    void reviewRules() {
        assertThat(AuditTaskStatusEnum.PENDING_REVIEW.reviewable()).isTrue();
        assertThat(AuditTaskStatusEnum.AUDITING.reviewable()).isFalse();
        assertThat(ReviewActionEnum.REJECT.requiresReasonCode()).isTrue();
        assertThat(ReviewActionEnum.PASS.requiresReasonCode()).isFalse();
        assertThat(ReviewActionEnum.NEED_FIX.requiresReasonCode()).isFalse();
    }

    @Test
    @DisplayName("驳回原因码固定四类")
    void reasonCodesAreFixed() {
        assertThat(RejectReasonCodeEnum.values()).hasSize(4);
        assertThat(RejectReasonCodeEnum.fromCode("IRRELEVANT_RETRIEVAL").label()).isEqualTo("检索无关");
        assertThat(RejectReasonCodeEnum.fromCode("WRONG_CITATION").label()).isEqualTo("引用错误");
        assertThat(RejectReasonCodeEnum.fromCode("RULE_FALSE_POSITIVE").label()).isEqualTo("规则误报");
        assertThat(RejectReasonCodeEnum.fromCode("SEMANTIC_MISJUDGE").label()).isEqualTo("语义误判");
        assertThat(RejectReasonCodeEnum.fromCode("OTHER")).isNull();
    }
}
