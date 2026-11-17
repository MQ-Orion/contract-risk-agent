package com.crag.agent.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.agent.rule.application.port.RuleRepository;
import com.crag.agent.rule.domain.ExpressionGuard;
import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.agent.rule.domain.RuleExample;
import com.crag.agent.rule.infrastructure.expression.AviatorRuleExpressionEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 规则回放用例（规范 8.3：新增规则必须附带正例与反例各一条，并纳入回放用例）。
 *
 * <p>直接读取 Flyway 种子的 {@code rule_config}：每条规则的正例必须命中、反例必须不命中。
 * 这样“规则写对了”不是靠人工抽查，而是每次构建都能复现的断言。</p>
 *
 * <p>同时校验表达式通过 {@link ExpressionGuard}（防止有人绕过接口直接改库写入危险表达式）。</p>
 */
@SpringBootTest
@Testcontainers
@EnabledIfSystemProperty(named = "crag.it", matches = "true")
class RuleReplayIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("crag-postgres:16-zhparser").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("contract_risk")
            .withUsername("crag")
            .withPassword("crag-dev");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
        REDIS.start();
        applyInitScripts();
    }

    @Autowired
    private RuleRepository ruleRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("crag.embedding.api-key", () -> "test-key");
        registry.add("crag.model.api-key", () -> "test-key");
    }

    @Test
    @DisplayName("种子规则全部通过正例与反例回放")
    void replaysSeedRules() throws Exception {
        List<RuleDefinition> rules = ruleRepository.findAllForRefresh();
        assertThat(rules).as("种子规则数量").hasSizeGreaterThanOrEqualTo(15);

        AviatorRuleExpressionEvaluator evaluator = new AviatorRuleExpressionEvaluator(1000L);
        for (RuleDefinition rule : rules) {
            assertThat(ExpressionGuard.findViolation(rule.conditionExpr()))
                    .as("规则 %s 表达式必须通过白名单", rule.ruleCode())
                    .isNull();
            RuleExample positive = objectMapper.readValue(rule.positiveExample(), RuleExample.class);
            RuleExample negative = objectMapper.readValue(rule.negativeExample(), RuleExample.class);

            assertThat(evaluator.matches(rule.conditionExpr(), positive.toContext()))
                    .as("规则 %s 正例应命中", rule.ruleCode())
                    .isTrue();
            assertThat(evaluator.matches(rule.conditionExpr(), negative.toContext()))
                    .as("规则 %s 反例不应命中", rule.ruleCode())
                    .isFalse();
        }
    }

    private static void applyInitScripts() {
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        Path initDir = root.resolve("db/init");
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            for (String file : new String[]{"00_extensions.sql", "10_tsearch_cn.sql", "20_functions.sql"}) {
                String sql = Files.readString(initDir.resolve(file), StandardCharsets.UTF_8);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化脚本执行失败", e);
        }
    }
}
