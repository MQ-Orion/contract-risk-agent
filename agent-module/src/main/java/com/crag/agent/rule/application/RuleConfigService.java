package com.crag.agent.rule.application;

import com.crag.agent.rule.domain.ExpressionGuard;
import com.crag.common.domain.RiskLevelEnum;
import com.crag.agent.rule.domain.RuleChangeActionEnum;
import com.crag.agent.rule.domain.RuleChangeLogEntry;
import com.crag.agent.rule.domain.RuleContext;
import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.agent.rule.domain.RuleExample;
import com.crag.agent.rule.domain.RuleScopeEnum;
import com.crag.agent.rule.application.port.RuleChangeLogRepository;
import com.crag.agent.rule.application.port.RuleRepository;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.common.text.ClauseTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 规则配置用例（规范 8.1、8.2、8.3）：增改、启停、试算与缓存刷新。
 *
 * <p>调用链：{@code RuleController} → 本类（事务）→ {@link RuleRepository} / {@link RuleChangeLogRepository}；
 * 缓存刷新由 {@code RuleCacheRefreshJob} 定时触发，或由 {@code POST /rules/refresh} 手动触发。</p>
 *
 * <p>保存前强制校验三件事（规范 8.2、8.3）：编码格式、表达式静态白名单与实际试算、正反例回放。
 * 任何一项不通过都抛 10001，规则不得入库——让错误停在配置阶段，而不是停在生产审核阶段。</p>
 */
@Service
public class RuleConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuleConfigService.class);

    /** 三段式编码：条款类型.业务点.两位序号（规范 8.3）。 */
    private static final Pattern RULE_CODE_PATTERN =
            Pattern.compile("^[A-Z][A-Z0-9_]*\\.[A-Z][A-Z0-9_]*\\.[0-9]{2}$");

    private final RuleRepository repository;
    private final RuleChangeLogRepository changeLog;
    private final RuleExpressionEvaluator evaluator;
    private final RuleCache cache;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock clock;

    /**
     * 构造服务。
     *
     * @param repository   规则仓储
     * @param changeLog    变更日志仓储
     * @param evaluator    表达式求值器
     * @param cache        规则缓存
     * @param objectMapper JSON 序列化
     * @param idGenerator  雪花标识
     * @param clock        统一时钟
     */
    public RuleConfigService(RuleRepository repository,
                             RuleChangeLogRepository changeLog,
                             RuleExpressionEvaluator evaluator,
                             RuleCache cache,
                             ObjectMapper objectMapper,
                             SnowflakeIdGenerator idGenerator,
                             Clock clock) {
        this.repository = repository;
        this.changeLog = changeLog;
        this.evaluator = evaluator;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 列出规则。
     *
     * @param tenantId        租户
     * @param includeDisabled 是否包含未启用
     * @return 规则列表
     */
    public List<RuleDefinition> list(long tenantId, boolean includeDisabled) {
        return repository.findByTenant(tenantId, includeDisabled);
    }

    /**
     * 查询单条规则。
     *
     * @param tenantId 租户
     * @param ruleId   规则标识
     * @return 规则
     */
    public RuleDefinition get(long tenantId, long ruleId) {
        return repository.findById(tenantId, ruleId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "rule " + ruleId));
    }

    /**
     * 新建规则。
     *
     * @param tenantId 租户
     * @param command  规则命令
     * @param operatorId 操作人
     * @param operatorName 操作人名称
     * @return 新建的规则
     */
    @Transactional
    public RuleDefinition create(long tenantId, RuleCommand command, long operatorId, String operatorName) {
        ValidatedRule validated = validate(command, true);
        repository.findByCode(tenantId, validated.ruleCode()).ifPresent(existing -> {
            throw BizException.of(ErrorCode.PARAM_INVALID, "rule code already exists: " + validated.ruleCode());
        });
        Instant now = clock.instant();
        RuleDefinition rule = new RuleDefinition(idGenerator.nextId(), tenantId, validated.ruleCode(),
                command.ruleName().trim(), validated.clauseType(), validated.scope(), command.conditionExpr().trim(),
                validated.riskLevel(), command.suggestionTemplate().trim(),
                Boolean.TRUE.equals(command.enabled()), 1,
                command.positiveExample().trim(), command.negativeExample().trim(),
                command.ownerName().trim(), now);
        repository.insert(rule);
        appendLog(rule, RuleChangeActionEnum.CREATE, null, rule, operatorId, operatorName);
        log.info("规则创建 tenantId={} ruleCode={} level={} enabled={}",
                tenantId, rule.ruleCode(), rule.riskLevel(), rule.enabled());
        return rule;
    }

    /**
     * 修改规则：编码不可变，版本号 +1 并记录变更前后值（规范 8.3）。
     *
     * @param tenantId 租户
     * @param ruleId   规则标识
     * @param command  规则命令
     * @param operatorId 操作人
     * @param operatorName 操作人名称
     * @return 修改后的规则
     */
    @Transactional
    public RuleDefinition update(long tenantId, long ruleId, RuleCommand command, long operatorId,
                                 String operatorName) {
        RuleDefinition before = get(tenantId, ruleId);
        if (command.ruleCode() != null && !command.ruleCode().trim().equals(before.ruleCode())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "rule code is immutable");
        }
        ValidatedRule validated = validate(command, false);
        Instant now = clock.instant();
        RuleDefinition after = new RuleDefinition(before.id(), before.tenantId(), before.ruleCode(),
                command.ruleName().trim(), validated.clauseType(), validated.scope(), command.conditionExpr().trim(),
                validated.riskLevel(), command.suggestionTemplate().trim(),
                Boolean.TRUE.equals(command.enabled()), before.versionNo() + 1,
                command.positiveExample().trim(), command.negativeExample().trim(),
                command.ownerName().trim(), now);
        repository.update(after);
        appendLog(after, RuleChangeActionEnum.UPDATE, before, after, operatorId, operatorName);
        log.info("规则更新 tenantId={} ruleCode={} version={}->{}", tenantId, after.ruleCode(),
                before.versionNo(), after.versionNo());
        return after;
    }

    /**
     * 启用/停用规则：版本号同样 +1，并记录动作（规范 8.3）。
     *
     * @param tenantId 租户
     * @param ruleId   规则标识
     * @param enabled  目标状态
     * @param operatorId 操作人
     * @param operatorName 操作人名称
     * @return 变更后的规则
     */
    @Transactional
    public RuleDefinition changeEnabled(long tenantId, long ruleId, boolean enabled, long operatorId,
                                        String operatorName) {
        RuleDefinition before = get(tenantId, ruleId);
        if (before.enabled() == enabled) {
            return before;
        }
        RuleDefinition after = before.withEnabled(enabled, clock.instant());
        repository.update(after);
        appendLog(after, enabled ? RuleChangeActionEnum.ENABLE : RuleChangeActionEnum.DISABLE, before, after,
                operatorId, operatorName);
        log.info("规则启停 tenantId={} ruleCode={} enabled={} version={}", tenantId, after.ruleCode(), enabled,
                after.versionNo());
        return after;
    }

    /**
     * 试算：对给定上下文求值，不写库、不影响缓存。
     *
     * <p>灰度流程：新规则先以 enabled=false 入库，再对历史条款批量试算并比对差异，确认后再启用（规范 8.3）。</p>
     *
     * @param tenantId 租户
     * @param ruleId   规则标识
     * @param context  试算上下文
     * @return 命中结果
     */
    public boolean dryRun(long tenantId, long ruleId, RuleContext context) {
        RuleDefinition rule = get(tenantId, ruleId);
        return evaluator.matches(rule.conditionExpr(), context);
    }

    /**
     * 立即刷新缓存，返回新版本。
     *
     * <p>定时任务每 30 秒刷新一次（规范 8.1 要求 ≤60 秒）；本方法供联调与演示使用，避免等待。
     * 刷新本身是幂等的，多实例并发调用安全（规范 8.1）。</p>
     *
     * @return 刷新后的缓存版本
     */
    public long refreshCache() {
        cache.replace(repository.findAllForRefresh());
        return cache.snapshot().version();
    }

    /**
     * 当前缓存快照，供接口展示热更新状态。
     *
     * @return 快照
     */
    public RuleCache.Snapshot cacheSnapshot() {
        return cache.snapshot();
    }

    private void appendLog(RuleDefinition rule, RuleChangeActionEnum action, RuleDefinition before,
                           RuleDefinition after, long operatorId, String operatorName) {
        changeLog.append(new RuleChangeLogEntry(idGenerator.nextId(), rule.tenantId(), rule.id(), rule.ruleCode(),
                action,
                before == null ? null : before.versionNo(),
                after == null ? null : after.versionNo(),
                before == null ? null : toJson(before),
                after == null ? null : toJson(after),
                operatorId, operatorName, clock.instant()));
    }

    private String toJson(RuleDefinition rule) {
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "serialize rule failed");
        }
    }

    private ValidatedRule validate(RuleCommand command, boolean creating) {
        if (command == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "command required");
        }
        String code = command.ruleCode() == null ? "" : command.ruleCode().trim();
        if (creating && !RULE_CODE_PATTERN.matcher(code).matches()) {
            throw BizException.of(ErrorCode.PARAM_INVALID,
                    "rule code must be TYPE.POINT.NN, e.g. PAYMENT.MAX_PAYMENT_DAYS.01");
        }
        requireText(command.ruleName(), "ruleName", 128);
        requireText(command.suggestionTemplate(), "suggestionTemplate", 500);
        requireText(command.ownerName(), "ownerName", 64);

        RuleScopeEnum scope = RuleScopeEnum.fromCode(command.scope());
        if (scope == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "scope must be CLAUSE or CONTRACT");
        }
        String clauseType = validateClauseType(command.clauseType(), scope);
        RiskLevelEnum level = RiskLevelEnum.fromCode(command.riskLevel());
        if (level == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "riskLevel must be HIGH, MEDIUM or LOW");
        }

        String expression = command.conditionExpr() == null ? "" : command.conditionExpr().trim();
        ExpressionGuard.check(expression);

        RuleExample positive = parseExample(command.positiveExample(), "positiveExample");
        RuleExample negative = parseExample(command.negativeExample(), "negativeExample");
        verifyExample(expression, positive, true);
        verifyExample(expression, negative, false);
        return new ValidatedRule(code, clauseType, scope, level);
    }

    private String validateClauseType(String clauseType, RuleScopeEnum scope) {
        String type = clauseType == null ? "" : clauseType.trim().toUpperCase();
        if (scope == RuleScopeEnum.CONTRACT) {
            if (!"CONTRACT".equals(type)) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "contract scope requires clauseType=CONTRACT");
            }
            return type;
        }
        for (ClauseTypeEnum candidate : ClauseTypeEnum.values()) {
            if (candidate.name().equals(type)) {
                return type;
            }
        }
        throw BizException.of(ErrorCode.PARAM_INVALID, "unknown clauseType: " + clauseType);
    }

    private RuleExample parseExample(String json, String field) {
        if (json == null || json.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, field + " required");
        }
        try {
            RuleExample example = objectMapper.readValue(json, RuleExample.class);
            if (example.expected() == null) {
                throw BizException.of(ErrorCode.PARAM_INVALID, field + ".expected required");
            }
            return example;
        } catch (JsonProcessingException e) {
            throw BizException.of(ErrorCode.PARAM_INVALID, field + " is not valid JSON");
        }
    }

    private void verifyExample(String expression, RuleExample example, boolean expected) {
        boolean actual;
        try {
            actual = evaluator.matches(expression, example.toContext());
        } catch (RuleExecutionException e) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "expression failed on example: " + e.getMessage());
        }
        if (example.expectedValue() != expected) {
            throw BizException.of(ErrorCode.PARAM_INVALID,
                    "example expected flag must be " + expected + " for " + (expected ? "positive" : "negative"));
        }
        if (actual != expected) {
            throw BizException.of(ErrorCode.PARAM_INVALID,
                    (expected ? "positive" : "negative") + " example does not match expression");
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, field + " required");
        }
        if (value.trim().length() > maxLength) {
            throw BizException.of(ErrorCode.PARAM_INVALID, field + " exceeds " + maxLength);
        }
    }

    /**
     * 校验通过后的取值。
     *
     * @param ruleCode   规则编码
     * @param clauseType 条款类型
     * @param scope      作用域
     * @param riskLevel  风险等级
     */
    private record ValidatedRule(String ruleCode, String clauseType, RuleScopeEnum scope, RiskLevelEnum riskLevel) {
    }
}
