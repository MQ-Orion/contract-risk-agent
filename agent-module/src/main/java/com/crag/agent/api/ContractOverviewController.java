package com.crag.agent.api;

import com.crag.audit.application.AuditTaskService;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.common.api.ApiResponse;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.common.security.PermissionPort;
import com.crag.doc.application.ContractAdminService;
import com.crag.doc.application.ContractQueryService;
import com.crag.doc.domain.model.ContractRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同管理接口：租户合同列表与删除（规范 4.4、9.1）。
 *
 * <p>放在 agent 而不是 doc：列表要展示审核状态，需要同时读 doc 的合同表与 audit 的任务表；
 * agent 是唯一同时依赖两者的模块，放在这里避免 doc 反向依赖 audit。</p>
 *
 * <p>租户隔离：合同查询由 doc 仓储按租户过滤；审核任务查询也带 tenantId，跨租户一律查不到。</p>
 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同管理", description = "租户合同列表、审核状态与删除")
public class ContractOverviewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ContractQueryService contractQueryService;
    private final ContractAdminService contractAdminService;
    private final AuditTaskService auditTaskService;
    private final PermissionPort permissionPort;

    /**
     * 构造接口。
     *
     * @param contractQueryService 合同查询用例
     * @param contractAdminService 合同删除用例
     * @param auditTaskService     审核任务用例
     * @param permissionPort       权限端口
     */
    public ContractOverviewController(ContractQueryService contractQueryService,
                                      ContractAdminService contractAdminService,
                                      AuditTaskService auditTaskService,
                                      PermissionPort permissionPort) {
        this.contractQueryService = contractQueryService;
        this.contractAdminService = contractAdminService;
        this.auditTaskService = auditTaskService;
        this.permissionPort = permissionPort;
    }

    /**
     * 合同列表（仅当前租户）。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数，最大 100
     * @return 合同列表与总数
     */
    @GetMapping
    @Operation(summary = "合同列表", description = "按租户隔离，附带最近一次审核状态与任务标识")
    public ApiResponse<ContractPage> list(@RequestParam(name = "page", defaultValue = "1") int page,
                                          @RequestParam(name = "size", defaultValue = "20") int size) {
        long tenantId = TenantContext.require();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<ContractRecord> contracts = contractQueryService.list(tenantId, page, safeSize);
        long total = contractQueryService.count(tenantId);
        List<ContractItem> items = new ArrayList<>(contracts.size());
        for (ContractRecord contract : contracts) {
            AuditTaskRecord latest = auditTaskService.latestByContract(tenantId, contract.id()).orElse(null);
            items.add(new ContractItem(String.valueOf(contract.id()), contract.name(),
                    contract.format().name(), contract.fileSize(), contract.pageCount(),
                    String.valueOf(contract.uploaderId()), contract.createdAt(),
                    latest == null ? "UNREVIEWED" : latest.status().name(),
                    latest == null ? null : String.valueOf(latest.id())));
        }
        return ApiResponse.ok(new ContractPage(List.copyOf(items), total, page, safeSize));
    }

    /**
     * 删除合同（任意审核状态均可删，逻辑删除 + 切片同步隐藏）。
     *
     * @param contractId 合同标识
     * @return 空响应
     */
    @DeleteMapping("/{contractId}")
    @Operation(summary = "删除合同", description = "逻辑删除合同与切片；审核历史保留供追溯")
    public ApiResponse<Void> delete(@PathVariable long contractId) {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        permissionPort.requireStartAudit(userId);
        ContractRecord contract = contractQueryService.detail(tenantId, contractId);
        permissionPort.requireContractAccess(userId, contract.uploaderId());
        contractAdminService.delete(tenantId, contractId);
        return ApiResponse.ok(null);
    }

    /**
     * 合同列表项（合同表 + 最近审核状态）。
     *
     * @param contractId  合同标识
     * @param name        名称
     * @param format      格式
     * @param fileSize    字节数
     * @param pageCount   页数
     * @param uploaderId  上传人
     * @param createdAt   上传时间
     * @param auditStatus 审核状态：UNREVIEWED 或任务状态
     * @param taskId      最近任务标识，可为空
     */
    public record ContractItem(String contractId, String name, String format, long fileSize, int pageCount,
                               String uploaderId, Instant createdAt, String auditStatus, String taskId) {
    }

    /**
     * 合同分页结果。
     *
     * @param items 当前页
     * @param total 总数
     * @param page  页码
     * @param size  每页条数
     */
    public record ContractPage(List<ContractItem> items, long total, int page, int size) {
    }
}
