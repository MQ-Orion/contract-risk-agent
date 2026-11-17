package com.crag.agent.audit;

import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.application.AuditTaskService;
import com.crag.common.api.ApiResponse;
import com.crag.common.domain.BizException;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.common.security.PermissionPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核发起接口（规范 9.1、10.6）。
 *
 * <p>调用链：本接口 → {@link AuditRunService#start}（创建/复用任务、置审核中、提交线程池）→ 立即返回任务标识；
 * 客户端再订阅 {@code /api/v1/audit/tasks/{taskId}/stream} 获取进度。</p>
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "审核发起", description = "创建任务并异步执行初审，配合 SSE 推送进度")
public class AuditRunController {

    private final AuditRunService auditRunService;
    private final AuditTaskService auditTaskService;
    private final PermissionPort permissionPort;
    private final Path reportRoot;

    /**
     * 构造接口。
     *
     * @param auditRunService  审核执行服务
     * @param auditTaskService 审核任务用例
     * @param permissionPort   权限端口（发起审核需 AUDITOR/ADMIN，规范 11.1）
     * @param reportStorageRoot 报告根目录（与 ReportFileStorage 使用同一配置）
     */
    public AuditRunController(AuditRunService auditRunService, AuditTaskService auditTaskService,
                              PermissionPort permissionPort,
                              @Value("${crag.report.storage-root:./data/reports}") String reportStorageRoot) {
        this.auditRunService = auditRunService;
        this.auditTaskService = auditTaskService;
        this.permissionPort = permissionPort;
        this.reportRoot = Path.of(reportStorageRoot).toAbsolutePath().normalize();
    }

    /**
     * 发起审核。
     *
     * @param contractId 合同标识
     * @param rulesOnly  是否仅规则模式
     * @return 任务信息
     */
    @PostMapping("/tasks/run")
    @Operation(summary = "发起审核", description = "幂等创建任务并异步执行；同合同同版本重复提交返回同一任务")
    public ApiResponse<RunResponse> run(@RequestParam long contractId,
                                        @RequestParam(name = "rulesOnly", defaultValue = "false")
                                        boolean rulesOnly) {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        permissionPort.requireStartAudit(userId);
        long taskId = auditRunService.start(tenantId, contractId, userId, rulesOnly);
        AuditTaskRecord task = auditTaskService.get(tenantId, taskId);
        return ApiResponse.ok(new RunResponse(String.valueOf(task.id()), task.status().name(),
                "/api/v1/audit/tasks/" + task.id() + "/stream"));
    }

    /**
     * 查询合同最近一次审核任务（任意状态），供“输入合同 ID 后刷新”使用。
     *
     * @param contractId 合同标识
     * @return 最近任务；没有任务时 10004
     */
    @GetMapping("/tasks/latest")
    @Operation(summary = "合同最近审核任务", description = "按合同查最近一次任务，没有任务返回 10004")
    public ApiResponse<RunResponse> latest(@RequestParam long contractId) {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        permissionPort.requireStartAudit(userId);
        AuditTaskRecord task = auditTaskService.latestByContract(tenantId, contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND,
                        "no audit task for contract " + contractId));
        return ApiResponse.ok(new RunResponse(String.valueOf(task.id()), task.status().name(),
                "/api/v1/audit/tasks/" + task.id() + "/stream"));
    }

    /**
     * 下载审核报告（DOCX）。
     *
     * <p>权限：任务发起人可直接下载自己的报告；其他人需要复核权限（规范 11.1）。
     * 路径安全：report_url 是“reports/租户/年月/合同.docx”形式的相对路径，必须归一化后仍落在
     * 报告根目录内，防止通过数据库脏数据做路径穿越（规范 11.6）。</p>
     *
     * @param taskId 任务标识
     * @return DOCX 文件流
     * @throws IOException 读取文件失败
     */
    @GetMapping("/tasks/{taskId}/report")
    @Operation(summary = "下载审核报告", description = "返回 DOCX 附件；报告未生成时返回 10004")
    public ResponseEntity<Resource> downloadReport(@PathVariable long taskId) throws IOException {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        AuditTaskRecord task = auditTaskService.get(tenantId, taskId);
        if (task.auditorId() == null || task.auditorId() != userId) {
            permissionPort.requireReview(userId);
        }
        String reportUrl = task.reportUrl();
        if (reportUrl == null || reportUrl.isBlank()) {
            throw BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "report not generated for task " + taskId);
        }
        String relative = reportUrl.startsWith("reports/")
                ? reportUrl.substring("reports/".length()) : reportUrl;
        Path file = reportRoot.resolve(relative).normalize();
        if (!file.startsWith(reportRoot) || !Files.isRegularFile(file)) {
            throw BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "report file missing for task " + taskId);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-report-" + taskId + ".docx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(Files.size(file))
                .body(new FileSystemResource(file));
    }

    /**
     * 发起结果。
     *
     * @param taskId    任务标识
     * @param status    当前状态
     * @param streamUrl 进度订阅地址
     */
    public record RunResponse(String taskId, String status, String streamUrl) {
    }
}
