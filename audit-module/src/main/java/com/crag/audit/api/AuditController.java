package com.crag.audit.api;

import com.crag.audit.application.AuditReviewService;
import com.crag.audit.application.AuditTaskService;
import com.crag.audit.application.HardNegativeExportService;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.RejectReasonCodeEnum;
import com.crag.audit.domain.model.ReviewActionEnum;
import com.crag.common.api.ApiResponse;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.common.security.PermissionPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核任务与复核接口（规范 9.1、9.4、10.1）。
 *
 * <p>调用链：本类 → {@link AuditTaskService} / {@link AuditReviewService}（事务）→ 审核仓储。
 * 租户与操作人只从上下文获取（规范 10.3）。</p>
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "审核", description = "审核任务、风险清单、人工复核与回流导出")
public class AuditController {

    private final AuditTaskService taskService;
    private final AuditReviewService reviewService;
    private final HardNegativeExportService exportService;
    private final PermissionPort permissionPort;

    /**
     * 构造接口。
     *
     * @param taskService   任务用例
     * @param reviewService 复核用例
     * @param exportService 回流导出
     */
    public AuditController(AuditTaskService taskService, AuditReviewService reviewService,
                           HardNegativeExportService exportService, PermissionPort permissionPort) {
        this.taskService = taskService;
        this.reviewService = reviewService;
        this.exportService = exportService;
        this.permissionPort = permissionPort;
    }

    /**
     * 创建或复用审核任务（幂等）。
     *
     * @param request 请求
     * @return 任务
     */
    @PostMapping("/tasks")
    @Operation(summary = "创建审核任务", description = "同合同同版本重复提交返回已有任务（规范 10.7）")
    public ApiResponse<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        permissionPort.requireStartAudit(userId);
        long taskId = taskService.createOrGet(tenantId, request.contractId(),
                request.contractVersion() == null ? 1 : request.contractVersion(), userId);
        return ApiResponse.ok(TaskResponse.from(taskService.get(tenantId, taskId)));
    }

    /**
     * 任务详情。
     *
     * @param taskId 任务标识
     * @return 任务
     */
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "审核任务详情")
    public ApiResponse<TaskResponse> detail(@PathVariable long taskId) {
        return ApiResponse.ok(TaskResponse.from(taskService.get(TenantContext.require(), taskId)));
    }

    /**
     * 风险清单。
     *
     * @param taskId 任务标识
     * @return 风险项
     */
    @GetMapping("/tasks/{taskId}/risks")
    @Operation(summary = "风险清单", description = "含复核状态与乐观锁版本，复核请求必须回传 versionNo")
    public ApiResponse<List<RiskResponse>> risks(@PathVariable long taskId) {
        List<AuditRiskItemRecord> items = taskService.risks(TenantContext.require(), taskId);
        List<RiskResponse> responses = new ArrayList<>(items.size());
        for (AuditRiskItemRecord item : items) {
            responses.add(RiskResponse.from(item));
        }
        return ApiResponse.ok(List.copyOf(responses));
    }

    /**
     * 复核风险项。
     *
     * @param taskId  任务标识
     * @param riskId  风险项标识
     * @param request 复核请求
     * @return 复核结果
     */
    @PostMapping("/tasks/{taskId}/risks/{riskId}/review")
    @Operation(summary = "复核风险项", description = "三类动作：PASS/REJECT/NEED_FIX；REJECT 必须带四类原因码")
    public ApiResponse<ReviewResponse> review(@PathVariable long taskId, @PathVariable long riskId,
                                              @Valid @RequestBody ReviewRequest request) {
        long userId = UserContext.require();
        permissionPort.requireReview(userId);
        ReviewActionEnum action = ReviewActionEnum.fromCode(request.action());
        if (action == null) {
            throw com.crag.common.domain.BizException.of(com.crag.common.api.ErrorCode.PARAM_INVALID,
                    "action must be PASS/REJECT/NEED_FIX");
        }
        RejectReasonCodeEnum reason = null;
        if (request.reasonCode() != null && !request.reasonCode().isBlank()) {
            reason = RejectReasonCodeEnum.fromCode(request.reasonCode());
            if (reason == null) {
                throw com.crag.common.domain.BizException.of(com.crag.common.api.ErrorCode.PARAM_INVALID,
                        "unknown reason code");
            }
        }
        AuditReviewService.ReviewResult result = reviewService.review(TenantContext.require(), taskId, riskId,
                action, reason, request.comment(), userId, "user-" + userId, request.versionNo());
        return ApiResponse.ok(new ReviewResponse(RiskResponse.from(result.item()),
                TaskResponse.from(result.task())));
    }

    /**
     * 完成任务。
     *
     * @param taskId  任务标识
     * @param request 请求
     * @return 任务
     */
    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "完成审核", description = "存在未复核的高/中风险时返回 40001")
    public ApiResponse<TaskResponse> complete(@PathVariable long taskId,
                                              @RequestBody CompleteRequest request) {
        permissionPort.requireReview(UserContext.require());
        return ApiResponse.ok(TaskResponse.from(taskService.complete(TenantContext.require(), taskId,
                request.conclusion(), UserContext.require())));
    }

    /**
     * 取消任务。
     *
     * @param taskId 任务标识
     * @return 任务
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "取消审核")
    public ApiResponse<TaskResponse> cancel(@PathVariable long taskId) {
        permissionPort.requireReview(UserContext.require());
        return ApiResponse.ok(TaskResponse.from(taskService.cancel(TenantContext.require(), taskId,
                UserContext.require())));
    }

    /**
     * 导出 hard negatives。
     *
     * @param since 起始时间，缺省为 24 小时前
     * @return 导出结果
     */
    @PostMapping("/hard-negatives/export")
    @Operation(summary = "导出 hard negatives", description = "字段固定：条款文本、召回片段、期望结论为空、原因码、来源标记")
    public ApiResponse<HardNegativeExportService.ExportResult> export(
            @RequestParam(name = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        Instant from = since == null ? Instant.now().minusSeconds(86_400L) : since;
        return ApiResponse.ok(exportService.export(TenantContext.require(), from));
    }

    /**
     * 创建任务请求。
     *
     * @param contractId      合同标识
     * @param contractVersion 合同版本
     */
    public record CreateTaskRequest(@NotNull Long contractId, Integer contractVersion) {
    }

    /**
     * 复核请求。
     *
     * @param action    动作
     * @param reasonCode 原因码
     * @param comment   备注
     * @param versionNo 期望版本
     */
    public record ReviewRequest(@NotBlank String action, String reasonCode, String comment,
                                @NotNull Integer versionNo) {
    }

    /**
     * 完成请求。
     *
     * @param conclusion 结论摘要
     */
    public record CompleteRequest(String conclusion) {
    }

    /**
     * 任务响应。
     *
     * @param id              任务标识
     * @param contractId      合同标识
     * @param contractVersion 合同版本
     * @param status          状态
     * @param reportUrl       报告位置
     * @param modelVersion    模型版本
     * @param promptVersion   Prompt 版本
     * @param conclusion      结论
     * @param startedAt       开始时间
     * @param finishedAt      结束时间
     */
    public record TaskResponse(String id, String contractId, int contractVersion, String status, String reportUrl,
                               String modelVersion, String promptVersion, String conclusion, Instant startedAt,
                               Instant finishedAt) {

        /**
         * 由领域对象转换。
         *
         * @param task 任务
         * @return 响应
         */
        public static TaskResponse from(AuditTaskRecord task) {
            return new TaskResponse(String.valueOf(task.id()), String.valueOf(task.contractId()),
                    task.contractVersion(), task.status().name(), task.reportUrl(), task.modelVersion(),
                    task.promptVersion(), task.conclusion(), task.startedAt(), task.finishedAt());
        }
    }

    /**
     * 风险项响应。
     *
     * @param id              风险项标识
     * @param level           等级
     * @param content         说明
     * @param suggestion      建议
     * @param source          来源
     * @param ruleCode        规则编码
     * @param clauseNo        条款序号
     * @param sourceChunkKey  切片定位
     * @param sourceExcerpt   引用原文
     * @param citationVerified 引用是否回验
     * @param reviewed        是否已复核
     * @param reviewResult    复核结果
     * @param versionNo       版本
     */
    public record RiskResponse(String id, String level, String content, String suggestion, String source,
                               String ruleCode, String clauseNo, String sourceChunkKey, String sourceExcerpt,
                               boolean citationVerified, boolean reviewed, String reviewResult, int versionNo) {

        /**
         * 由领域对象转换。
         *
         * @param item 风险项
         * @return 响应
         */
        public static RiskResponse from(AuditRiskItemRecord item) {
            return new RiskResponse(String.valueOf(item.id()), item.riskLevel().name(), item.riskContent(),
                    item.suggestion(), item.source(), item.ruleCode(), item.clauseNo(), item.sourceChunkKey(),
                    item.sourceExcerpt(), item.citationVerified(), item.reviewed(),
                    item.reviewResult() == null ? null : item.reviewResult().name(), item.versionNo());
        }
    }

    /**
     * 复核结果响应。
     *
     * @param risk 风险项
     * @param task 任务
     */
    public record ReviewResponse(RiskResponse risk, TaskResponse task) {
    }
}
