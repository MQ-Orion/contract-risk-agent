package com.crag.agent.rule.api;

import com.crag.agent.rule.api.dto.RuleDryRunRequest;
import com.crag.agent.rule.api.dto.RuleRequest;
import com.crag.agent.rule.api.dto.RuleResponse;
import com.crag.agent.rule.application.RuleCache;
import com.crag.agent.rule.application.RuleCommand;
import com.crag.agent.rule.application.RuleConfigService;
import com.crag.agent.rule.domain.RuleContext;
import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.common.api.ApiResponse;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.common.security.PermissionPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则管理接口（规范 8.1、8.2、8.3、10.1）。
 *
 * <p>调用链：本类 → {@link RuleConfigService}（事务）→ 规则仓储/变更日志；缓存刷新由定时任务或
 * {@code POST /rules/refresh} 触发。</p>
 *
 * <p>租户与操作人只从上下文获取（规范 10.3）；规则按租户隔离，灰度时对目标租户单独启用（规范 8.3）。</p>
 */
@RestController
@RequestMapping("/api/v1/rules")
@Tag(name = "规则", description = "规则配置管理：增改、启停、试算与热更新")
public class RuleController {

    private final RuleConfigService ruleConfigService;
    private final PermissionPort permissionPort;

    /**
     * 构造接口。
     *
     * @param ruleConfigService 规则配置用例
     * @param permissionPort    权限端口（规则变更仅管理员，规范 11.1）
     */
    public RuleController(RuleConfigService ruleConfigService, PermissionPort permissionPort) {
        this.ruleConfigService = ruleConfigService;
        this.permissionPort = permissionPort;
    }

    /**
     * 规则列表。
     *
     * @param includeDisabled 是否包含未启用
     * @return 规则列表
     */
    @GetMapping
    @Operation(summary = "规则列表", description = "默认只返回启用规则；灰度试跑需传 includeDisabled=true")
    public ApiResponse<List<RuleResponse>> list(
            @RequestParam(name = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
        List<RuleDefinition> rules = ruleConfigService.list(TenantContext.require(), includeDisabled);
        List<RuleResponse> responses = new ArrayList<>(rules.size());
        for (RuleDefinition rule : rules) {
            responses.add(RuleResponse.from(rule));
        }
        return ApiResponse.ok(List.copyOf(responses));
    }

    /**
     * 规则详情。
     *
     * @param ruleId 规则标识
     * @return 规则
     */
    @GetMapping("/{ruleId}")
    @Operation(summary = "规则详情")
    public ApiResponse<RuleResponse> detail(@PathVariable long ruleId) {
        return ApiResponse.ok(RuleResponse.from(ruleConfigService.get(TenantContext.require(), ruleId)));
    }

    /**
     * 新建规则。
     *
     * @param request 规则请求
     * @return 新建的规则
     */
    @PostMapping
    @Operation(summary = "新建规则", description = "保存前强制校验编码格式、表达式白名单与正反例回放；默认不启用，先灰度试跑")
    public ApiResponse<RuleResponse> create(@Valid @RequestBody RuleRequest request) {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        permissionPort.requireManageConfig(userId);
        RuleDefinition rule = ruleConfigService.create(tenantId, toCommand(request), userId, operatorName(userId));
        return ApiResponse.ok(RuleResponse.from(rule));
    }

    /**
     * 修改规则。
     *
     * @param ruleId  规则标识
     * @param request 规则请求
     * @return 修改后的规则
     */
    @PutMapping("/{ruleId}")
    @Operation(summary = "修改规则", description = "规则编码不可变；版本号 +1 并写入变更日志")
    public ApiResponse<RuleResponse> update(@PathVariable long ruleId, @Valid @RequestBody RuleRequest request) {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        permissionPort.requireManageConfig(userId);
        RuleDefinition rule = ruleConfigService.update(tenantId, ruleId, toCommand(request), userId,
                operatorName(userId));
        return ApiResponse.ok(RuleResponse.from(rule));
    }

    /**
     * 启用规则。
     *
     * @param ruleId 规则标识
     * @return 变更后的规则
     */
    @PostMapping("/{ruleId}/enable")
    @Operation(summary = "启用规则", description = "版本号 +1；灰度完成后调用")
    public ApiResponse<RuleResponse> enable(@PathVariable long ruleId) {
        long userId = UserContext.require();
        permissionPort.requireManageConfig(userId);
        return ApiResponse.ok(RuleResponse.from(ruleConfigService.changeEnabled(TenantContext.require(), ruleId,
                true, userId, operatorName(userId))));
    }

    /**
     * 停用规则。
     *
     * @param ruleId 规则标识
     * @return 变更后的规则
     */
    @PostMapping("/{ruleId}/disable")
    @Operation(summary = "停用规则", description = "版本号 +1；停用后该规则不再参与求值")
    public ApiResponse<RuleResponse> disable(@PathVariable long ruleId) {
        long userId = UserContext.require();
        permissionPort.requireManageConfig(userId);
        return ApiResponse.ok(RuleResponse.from(ruleConfigService.changeEnabled(TenantContext.require(), ruleId,
                false, userId, operatorName(userId))));
    }

    /**
     * 试算：给定上下文求值，不写库、不影响缓存。
     *
     * @param ruleId  规则标识
     * @param request 试算上下文
     * @return 是否命中
     */
    @PostMapping("/{ruleId}/dry-run")
    @Operation(summary = "规则试算", description = "灰度阶段用历史条款比对差异；不写库、不改变缓存")
    public ApiResponse<DryRunResponse> dryRun(@PathVariable long ruleId,
                                              @RequestBody RuleDryRunRequest request) {
        RuleContext context = new RuleContext(request.clauseText(), request.clauseType(),
                request.contractAmount(), request.paymentDays(), request.jurisdiction(),
                request.hasJointLiability(),
                request.presentTypes() == null ? Set.of() : Set.copyOf(request.presentTypes()));
        boolean matched = ruleConfigService.dryRun(TenantContext.require(), ruleId, context);
        return ApiResponse.ok(new DryRunResponse(matched));
    }

    /**
     * 立即刷新规则缓存。
     *
     * @return 刷新后的缓存状态
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新规则缓存", description = "不等待定时任务，立即生效；返回新版本")
    public ApiResponse<CacheStatusResponse> refresh() {
        long tenantId = TenantContext.require();
        permissionPort.requireManageConfig(UserContext.require());
        long version = ruleConfigService.refreshCache();
        return ApiResponse.ok(cacheStatus(tenantId, version));
    }

    /**
     * 缓存状态。
     *
     * @return 缓存版本与规则数
     */
    @GetMapping("/cache")
    @Operation(summary = "规则缓存状态", description = "用于验证热更新是否生效")
    public ApiResponse<CacheStatusResponse> cache() {
        long tenantId = TenantContext.require();
        return ApiResponse.ok(cacheStatus(tenantId, null));
    }

    private CacheStatusResponse cacheStatus(long tenantId, Long refreshedVersion) {
        RuleCache.Snapshot snapshot = ruleConfigService.cacheSnapshot();
        long version = refreshedVersion == null ? snapshot.version() : refreshedVersion;
        long tenantRules = snapshot.allRules().stream().filter(rule -> rule.tenantId() == tenantId).count();
        long enabled = snapshot.allRules().stream()
                .filter(rule -> rule.tenantId() == tenantId && rule.enabled())
                .count();
        return new CacheStatusResponse(version, tenantRules, enabled);
    }

    private static RuleCommand toCommand(RuleRequest request) {
        return new RuleCommand(request.ruleCode(), request.ruleName(), request.clauseType(), request.scope(),
                request.conditionExpr(), request.riskLevel(), request.suggestionTemplate(), request.enabled(),
                request.positiveExample(), request.negativeExample(), request.ownerName());
    }

    private static String operatorName(long userId) {
        // 开发期身份只有数字标识；权限里程碑接入令牌后改为真实用户名（ADR 0002 偏差 6）
        return "user-" + userId;
    }

    /**
     * 试算结果。
     *
     * @param matched 是否命中
     */
    public record DryRunResponse(boolean matched) {
    }

    /**
     * 缓存状态。
     *
     * @param version      缓存版本（最大更新时间）
     * @param tenantRules  本租户规则数
     * @param enabledRules 本租户启用规则数
     */
    public record CacheStatusResponse(long version, long tenantRules, long enabledRules) {
    }
}
