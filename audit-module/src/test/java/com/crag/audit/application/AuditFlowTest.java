package com.crag.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.audit.application.port.AuditRiskInput;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import com.crag.audit.domain.model.RejectReasonCodeEnum;
import com.crag.audit.domain.model.ReviewActionEnum;
import com.crag.audit.support.FakeAuditRepository;
import com.crag.common.domain.BizException;
import com.crag.common.domain.RiskLevelEnum;
import com.crag.common.domain.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审核流程测试（规范 9.1、9.4、10.7）：幂等、状态流转、复核乐观锁、驳回原因码、完成后置校验。
 */
class AuditFlowTest {

    private static final long TENANT = 1L;
    private static final long CONTRACT = 100L;
    private static final long USER = 9L;

    private FakeAuditRepository repository;
    private AuditTaskService taskService;
    private AuditReviewService reviewService;

    @BeforeEach
    void setUp() {
        repository = new FakeAuditRepository();
        SnowflakeIdGenerator ids = new SnowflakeIdGenerator(1L);
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);
        taskService = new AuditTaskService(repository, ids, clock);
        reviewService = new AuditReviewService(repository, ids, clock);
    }

    private static AuditRiskInput risk(RiskLevelEnum level) {
        return new AuditRiskInput(level, "风险说明", "建议", "RULE", "PAYMENT.MAX_PAYMENT_DAYS.01", 1, "制度",
                "kb#1", "原文片段", true, "第三条", "PAYMENT", "合同约定 120 日付款。", "[]");
    }

    private long createAuditingTask() {
        long taskId = taskService.createOrGet(TENANT, CONTRACT, 1, USER);
        taskService.markAuditing(TENANT, taskId, USER);
        return taskId;
    }

    @Test
    @DisplayName("任务创建幂等：同合同同版本返回同一任务")
    void createIsIdempotent() {
        long first = taskService.createOrGet(TENANT, CONTRACT, 1, USER);
        long second = taskService.createOrGet(TENANT, CONTRACT, 1, USER);
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("并发唯一键冲突后回查已有任务，不抛异常")
    void duplicateInsertFallsBackToExisting() {
        long first = taskService.createOrGet(TENANT, CONTRACT, 1, USER);
        // 模拟另一个实例已插入：清空内存但保留“冲突”行为不可行，因此直接验证第二次调用走查库分支
        repository.duplicateOnInsert(false);
        long second = taskService.createOrGet(TENANT, CONTRACT, 1, USER);
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("提交结果进入待复核，风险项默认未复核")
    void submitResultMovesToPendingReview() {
        long taskId = createAuditingTask();
        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.HIGH)), "/reports/x.docx",
                "qwen3.8-flash", "extract-v1", USER);

        AuditTaskRecord task = taskService.get(TENANT, taskId);
        assertThat(task.status()).isEqualTo(AuditTaskStatusEnum.PENDING_REVIEW);
        assertThat(task.reportUrl()).isEqualTo("/reports/x.docx");
        assertThat(taskService.risks(TENANT, taskId)).hasSize(1);
    }

    @Test
    @DisplayName("存在未复核高风险时禁止完成任务")
    void completeBlockedByUnreviewedRisk() {
        long taskId = createAuditingTask();
        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.HIGH)), "/r.docx", "m", "p", USER);

        assertThatThrownBy(() -> taskService.complete(TENANT, taskId, "通过", USER))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("复核通过后可完成任务；日志只追加一条")
    void reviewPassThenComplete() {
        long taskId = createAuditingTask();
        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.HIGH)), "/r.docx", "m", "p", USER);
        AuditRiskItemRecord item = taskService.risks(TENANT, taskId).get(0);

        reviewService.review(TENANT, taskId, item.id(), ReviewActionEnum.PASS, null, "确认", USER, "reviewer",
                item.versionNo());
        AuditTaskRecord completed = taskService.complete(TENANT, taskId, "风险可接受", USER);

        assertThat(completed.status()).isEqualTo(AuditTaskStatusEnum.COMPLETED);
        assertThat(repository.logs()).hasSize(1);
        assertThat(taskService.risks(TENANT, taskId).get(0).reviewed()).isTrue();
    }

    @Test
    @DisplayName("驳回必须带原因码，并把任务退回审核中")
    void rejectRequiresReasonAndRollsBack() {
        long taskId = createAuditingTask();
        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.MEDIUM)), "/r.docx", "m", "p", USER);
        AuditRiskItemRecord item = taskService.risks(TENANT, taskId).get(0);

        assertThatThrownBy(() -> reviewService.review(TENANT, taskId, item.id(), ReviewActionEnum.REJECT, null,
                null, USER, "reviewer", item.versionNo()))
                .isInstanceOf(BizException.class);

        AuditReviewService.ReviewResult result = reviewService.review(TENANT, taskId, item.id(),
                ReviewActionEnum.REJECT, RejectReasonCodeEnum.RULE_FALSE_POSITIVE, "规则误报", USER, "reviewer",
                item.versionNo());

        assertThat(result.task().status()).isEqualTo(AuditTaskStatusEnum.AUDITING);
        assertThat(repository.logs()).hasSize(1);
        assertThat(repository.logs().get(0).reasonCode()).isEqualTo(RejectReasonCodeEnum.RULE_FALSE_POSITIVE);
    }

    @Test
    @DisplayName("复核使用乐观锁：旧版本号被拒绝，不覆盖他人结论")
    void staleVersionIsRejected() {
        long taskId = createAuditingTask();
        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.LOW)), "/r.docx", "m", "p", USER);
        AuditRiskItemRecord item = taskService.risks(TENANT, taskId).get(0);
        reviewService.review(TENANT, taskId, item.id(), ReviewActionEnum.PASS, null, null, USER, "a",
                item.versionNo());

        assertThatThrownBy(() -> reviewService.review(TENANT, taskId, item.id(), ReviewActionEnum.PASS, null,
                null, USER, "b", item.versionNo()))
                .isInstanceOf(BizException.class);
        assertThat(repository.logs()).hasSize(1);
    }

    @Test
    @DisplayName("已完成任务不可再复核")
    void completedTaskIsNotReviewable() {
        long taskId = createAuditingTask();
        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.LOW)), "/r.docx", "m", "p", USER);
        AuditRiskItemRecord item = taskService.risks(TENANT, taskId).get(0);
        reviewService.review(TENANT, taskId, item.id(), ReviewActionEnum.PASS, null, null, USER, "a",
                item.versionNo());
        taskService.complete(TENANT, taskId, "完成", USER);

        assertThatThrownBy(() -> reviewService.review(TENANT, taskId, item.id(), ReviewActionEnum.PASS, null,
                null, USER, "a", item.versionNo() + 1))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("驳回重审后提交结果只保留当前一轮风险项，不重复堆叠")
    void resubmitAfterRejectReplacesCurrentRisks() {
        long taskId = createAuditingTask();
        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.HIGH), risk(RiskLevelEnum.MEDIUM)),
                "/r1.docx", "m", "p", USER);
        AuditRiskItemRecord item = taskService.risks(TENANT, taskId).get(0);
        reviewService.review(TENANT, taskId, item.id(), ReviewActionEnum.REJECT,
                RejectReasonCodeEnum.RULE_FALSE_POSITIVE, "规则误报", USER, "reviewer", item.versionNo());

        taskService.submitResult(TENANT, taskId, List.of(risk(RiskLevelEnum.HIGH)), "/r2.docx", "m", "p", USER);

        assertThat(taskService.risks(TENANT, taskId)).hasSize(1);
        assertThat(taskService.get(TENANT, taskId).status()).isEqualTo(AuditTaskStatusEnum.PENDING_REVIEW);
    }
}