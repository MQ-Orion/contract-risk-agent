package com.crag.doc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.common.domain.BizException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 解析任务状态机测试（规范 5.5、9.1：只允许白名单边，禁止跳跃）。
 */
class ParseStatusEnumTest {

    @Test
    @DisplayName("合法流转边")
    void allowsWhitelistedEdges() {
        assertThat(ParseStatusEnum.PENDING.canTransitionTo(ParseStatusEnum.PARSING)).isTrue();
        assertThat(ParseStatusEnum.PARSING.canTransitionTo(ParseStatusEnum.SPLITTING)).isTrue();
        assertThat(ParseStatusEnum.SPLITTING.canTransitionTo(ParseStatusEnum.READY)).isTrue();
        assertThat(ParseStatusEnum.SPLITTING.canTransitionTo(ParseStatusEnum.EMBEDDING)).isTrue();
        assertThat(ParseStatusEnum.EMBEDDING.canTransitionTo(ParseStatusEnum.READY)).isTrue();
        assertThat(ParseStatusEnum.FAILED.canTransitionTo(ParseStatusEnum.PENDING)).isTrue();
    }

    @Test
    @DisplayName("禁止跳跃与回退")
    void rejectsSkippingAndRollback() {
        assertThat(ParseStatusEnum.PENDING.canTransitionTo(ParseStatusEnum.READY)).isFalse();
        assertThat(ParseStatusEnum.PARSING.canTransitionTo(ParseStatusEnum.READY)).isFalse();
        assertThat(ParseStatusEnum.READY.canTransitionTo(ParseStatusEnum.PARSING)).isFalse();
        assertThat(ParseStatusEnum.PARSING.canTransitionTo(ParseStatusEnum.PENDING)).isFalse();
        assertThatThrownBy(() -> ParseStatusEnum.READY.checkTransition(ParseStatusEnum.SPLITTING))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("任务记录随状态流转递增重试次数并保留时间")
    void taskRecordTracksFailure() {
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        ParseTaskRecord task = ParseTaskRecord.pending(1L, 2L, 3L, now);

        ParseTaskRecord failed = task.fail("PARSE_FAILED", "boom", now, now.plusSeconds(60));

        assertThat(failed.status()).isEqualTo(ParseStatusEnum.FAILED);
        assertThat(failed.retryCount()).isEqualTo(1);
        assertThat(failed.nextRetryAt()).isEqualTo(now.plusSeconds(60));
        assertThat(failed.errorCode()).isEqualTo("PARSE_FAILED");
    }

    @Test
    @DisplayName("终态判定")
    void terminalStates() {
        assertThat(ParseStatusEnum.READY.isTerminal()).isTrue();
        assertThat(ParseStatusEnum.FAILED.isTerminal()).isTrue();
        assertThat(ParseStatusEnum.PARSING.isTerminal()).isFalse();
    }
}
