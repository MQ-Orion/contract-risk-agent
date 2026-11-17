package com.crag.agent.rule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.agent.rule.domain.RuleChangeActionEnum;
import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.agent.rule.infrastructure.expression.AviatorRuleExpressionEvaluator;
import com.crag.agent.rule.support.FakeRuleChangeLogRepository;
import com.crag.agent.rule.support.FakeRuleRepository;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规则配置用例测试：编码格式、表达式白名单、正反例回放、版本号与变更日志（规范 8.2、8.3）。
 */
class RuleConfigServiceTest {

    private static final String POSITIVE =
            "{\"clauseText\":\"120 日内付款\",\"clauseType\":\"PAYMENT\",\"paymentDays\":120,\"expected\":true}";
    private static final String NEGATIVE =
            "{\"clauseText\":\"30 日内付款\",\"clauseType\":\"PAYMENT\",\"paymentDays\":30,\"expected\":false}";

    private FakeRuleRepository repository;
    private FakeRuleChangeLogRepository changeLog;
    private RuleConfigService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRuleRepository();
        changeLog = new FakeRuleChangeLogRepository();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new RuleConfigService(repository, changeLog, new AviatorRuleExpressionEvaluator(1000L),
                new RuleCache(), objectMapper, new SnowflakeIdGenerator(1L),
                Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC));
    }

    private static RuleCommand command(String code, String expression, String positive, String negative) {
        return new RuleCommand(code, "规则名称", "PAYMENT", "CLAUSE", expression, "HIGH", "建议", false,
                positive, negative, "tester");
    }

    @Test
    @DisplayName("编码不符合三段式时拒绝入库")
    void rejectsInvalidCode() {
        RuleCommand bad = command("payment_max_days", "paymentDays > 90", POSITIVE, NEGATIVE);
        assertThatThrownBy(() -> service.create(1L, bad, 9L, "tester"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("参数校验失败");
        assertThat(repository.all()).isEmpty();
    }

    @Test
    @DisplayName("表达式含反射链时拒绝入库")
    void rejectsReflectionExpression() {
        RuleCommand bad = command("PAYMENT.REFLECT.01", "\"\".getClass().forName(\"java.lang.Runtime\")",
                POSITIVE, NEGATIVE);
        assertThatThrownBy(() -> service.create(1L, bad, 9L, "tester")).isInstanceOf(BizException.class);
        assertThat(changeLog.entries()).isEmpty();
    }

    @Test
    @DisplayName("正例不命中时拒绝入库（保存前强制试算）")
    void rejectsWhenPositiveExampleDoesNotMatch() {
        RuleCommand bad = command("PAYMENT.MAX_DAYS.01", "paymentDays > 200", POSITIVE, NEGATIVE);
        assertThatThrownBy(() -> service.create(1L, bad, 9L, "tester")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("新建规则：版本 1、默认未启用、写入 CREATE 日志")
    void createsRuleWithVersionOne() {
        RuleDefinition created = service.create(1L, command("PAYMENT.MAX_DAYS.01", "paymentDays > 90", POSITIVE,
                NEGATIVE), 9L, "tester");

        assertThat(created.versionNo()).isEqualTo(1);
        assertThat(created.enabled()).isFalse();
        assertThat(created.riskLevel().name()).isEqualTo("HIGH");
        assertThat(changeLog.entries()).hasSize(1);
        assertThat(changeLog.entries().get(0).action()).isEqualTo(RuleChangeActionEnum.CREATE);
        assertThat(changeLog.entries().get(0).versionBefore()).isNull();
    }

    @Test
    @DisplayName("修改规则：版本 +1 且日志同时记录变更前后值")
    void updateBumpsVersionAndLogsBeforeAfter() {
        RuleDefinition created = service.create(1L, command("PAYMENT.MAX_DAYS.01", "paymentDays > 90", POSITIVE,
                NEGATIVE), 9L, "tester");
        RuleCommand update = new RuleCommand(null, "规则名称", "PAYMENT", "CLAUSE", "paymentDays > 120", "MEDIUM",
                "建议", true, POSITIVE.replace("\"paymentDays\":120", "\"paymentDays\":150"), NEGATIVE, "tester");

        RuleDefinition updated = service.update(1L, created.id(), update, 9L, "tester");

        assertThat(updated.versionNo()).isEqualTo(2);
        assertThat(updated.riskLevel().name()).isEqualTo("MEDIUM");
        assertThat(updated.enabled()).isTrue();
        assertThat(changeLog.entries()).hasSize(2);
        assertThat(changeLog.entries().get(1).action()).isEqualTo(RuleChangeActionEnum.UPDATE);
        assertThat(changeLog.entries().get(1).versionBefore()).isEqualTo(1);
        assertThat(changeLog.entries().get(1).beforeJson()).contains("paymentDays > 90");
        assertThat(changeLog.entries().get(1).afterJson()).contains("paymentDays > 120");
    }

    @Test
    @DisplayName("启停规则：版本号不变，动作分别为 ENABLE/DISABLE")
    void changeEnabledLogsAction() {
        RuleDefinition created = service.create(1L, command("PAYMENT.MAX_DAYS.01", "paymentDays > 90", POSITIVE,
                NEGATIVE), 9L, "tester");

        RuleDefinition enabled = service.changeEnabled(1L, created.id(), true, 9L, "tester");
        RuleDefinition disabled = service.changeEnabled(1L, created.id(), false, 9L, "tester");

        // 启停不是内容变更：版本保持 1；只有 update 才递增（规范 8.3）
        assertThat(enabled.versionNo()).isEqualTo(1);
        assertThat(disabled.versionNo()).isEqualTo(1);
        assertThat(changeLog.entries()).extracting("action")
                .containsExactly(RuleChangeActionEnum.CREATE, RuleChangeActionEnum.ENABLE,
                        RuleChangeActionEnum.DISABLE);
    }

    @Test
    @DisplayName("编码不可变：修改时更换编码被拒绝")
    void codeIsImmutable() {
        RuleDefinition created = service.create(1L, command("PAYMENT.MAX_DAYS.01", "paymentDays > 90", POSITIVE,
                NEGATIVE), 9L, "tester");
        RuleCommand rename = command("PAYMENT.OTHER.02", "paymentDays > 90", POSITIVE, NEGATIVE);

        assertThatThrownBy(() -> service.update(1L, created.id(), rename, 9L, "tester"))
                .isInstanceOf(BizException.class);
    }
}
