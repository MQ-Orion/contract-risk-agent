package com.crag.doc.api;

import com.crag.common.api.ApiResponse;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.common.security.PermissionPort;
import com.crag.doc.api.dto.ClauseResponse;
import com.crag.doc.api.dto.ContractDetailResponse;
import com.crag.doc.api.dto.ContractUploadResponse;
import com.crag.doc.api.dto.ParseTaskResponse;
import com.crag.doc.application.ContractQueryService;
import com.crag.doc.application.ContractUploadService;
import com.crag.doc.application.ParseTaskService;
import com.crag.doc.application.UploadResult;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.doc.domain.model.ParseTaskRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 合同接口（规范 10.1、10.5）。
 *
 * <p>路径为版本化资源路径，资源名用名词复数，动作用请求方法表达。租户与操作人只从上下文获取，
 * 禁止从请求体或查询参数读取（规范 10.3）。</p>
 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同", description = "合同接收、解析进度与条款切片")
public class ContractController {

    /** 切片列表单次返回上限（规范 10.4、4.6：分页上限两百条）。 */
    private static final int MAX_CLAUSE_PAGE_SIZE = 200;

    private final ContractUploadService uploadService;
    private final ParseTaskService parseTaskService;
    private final ContractQueryService queryService;
    private final PermissionPort permissionPort;

    /**
     * 构造接口。
     *
     * @param uploadService    上传用例
     * @param parseTaskService 任务用例
     * @param queryService     查询用例
     */
    public ContractController(ContractUploadService uploadService,
                              ParseTaskService parseTaskService,
                              ContractQueryService queryService, PermissionPort permissionPort) {
        this.uploadService = uploadService;
        this.parseTaskService = parseTaskService;
        this.queryService = queryService;
        this.permissionPort = permissionPort;
    }

    /**
     * 上传合同并发起解析。
     *
     * <p>调用链：本方法 → {@code ContractUploadService.upload}（文件 IO 与哈希，无事务）→ 仓储写入 →
     * {@code ensureTask} → 解析线程池 → {@code ParsePipelineService.execute}。接口在任务入库后立即返回，
     * 不等待解析完成；客户端凭任务标识轮询进度（规范 10.5、10.6）。</p>
     *
     * @param file 合同文件，字段名固定为 file
     * @return 合同标识与任务标识
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传合同", description = "校验类型与大小后落盘并发起异步解析；同租户同哈希直接秒传")
    public ApiResponse<ContractUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        long tenantId = TenantContext.require();
        long userId = UserContext.require();
        try {
            UploadResult result = uploadService.upload(tenantId, userId, file.getOriginalFilename(),
                    file.getSize(), file.getInputStream());
            return ApiResponse.ok(new ContractUploadResponse(String.valueOf(result.contractId()),
                    String.valueOf(result.taskId()), result.deduplicated()));
        } catch (IOException e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "read upload stream failed: " + e.getMessage());
        }
    }

    /**
     * 查询合同详情。
     *
     * @param contractId 合同标识
     * @return 详情
     */
    @GetMapping("/{contractId}")
    @Operation(summary = "合同详情")
    public ApiResponse<ContractDetailResponse> detail(@PathVariable long contractId) {
        ContractRecord contract = queryService.detail(TenantContext.require(), contractId);
        permissionPort.requireContractAccess(UserContext.require(), contract.uploaderId());
        return ApiResponse.ok(new ContractDetailResponse(String.valueOf(contract.id()), contract.name(),
                contract.format().name(), contract.fileSize(), contract.pageCount(), contract.versionNo(),
                contract.createdAt()));
    }

    /**
     * 查询解析任务状态。
     *
     * @param contractId 合同标识
     * @return 任务状态
     */
    @GetMapping("/{contractId}/parse-task")
    @Operation(summary = "解析任务状态")
    public ApiResponse<ParseTaskResponse> parseTask(@PathVariable long contractId) {
        ParseTaskRecord task = parseTaskService.latest(TenantContext.require(), contractId);
        return ApiResponse.ok(toResponse(task));
    }

    /**
     * 人工重试解析。
     *
     * <p>调用链：本方法 → {@code ParseTaskService.retry}（校验状态与次数并落库）→ 解析线程池。
     * 只有 FAILED 且未达次数上限才允许，其余状态返回 40001（规范 5.5）。</p>
     *
     * @param contractId 合同标识
     * @return 重新排队后的任务状态
     */
    @PostMapping("/{contractId}/parse-task/retry")
    @Operation(summary = "人工重试解析", description = "仅失败态可重试，总尝试次数不超过三次")
    public ApiResponse<ParseTaskResponse> retry(@PathVariable long contractId) {
        ParseTaskRecord task = parseTaskService.retry(TenantContext.require(), contractId);
        return ApiResponse.ok(toResponse(task));
    }

    /**
     * 查询条款切片。
     *
     * <p>调用链：本方法 → {@code ContractQueryService.clauses}（先校验合同存在）→ {@code ContractChunkRepository}。
     * 切片按全文索引升序返回，截断到 200 条是接口层的硬上限，不是数据库分页（规范 10.4、4.6）。</p>
     *
     * @param contractId 合同标识
     * @return 切片列表
     */
    @GetMapping("/{contractId}/clauses")
    @Operation(summary = "条款切片列表", description = "按全文索引升序返回，单次最多 200 条")
    public ApiResponse<List<ClauseResponse>> clauses(@PathVariable long contractId) {
        ContractRecord contract = queryService.detail(TenantContext.require(), contractId);
        permissionPort.requireContractAccess(UserContext.require(), contract.uploaderId());
        List<ContractChunkRecord> chunks = queryService.clauses(TenantContext.require(), contractId);
        if (chunks.size() > MAX_CLAUSE_PAGE_SIZE) {
            chunks = chunks.subList(0, MAX_CLAUSE_PAGE_SIZE);
        }
        List<ClauseResponse> responses = new ArrayList<>(chunks.size());
        for (ContractChunkRecord chunk : chunks) {
            responses.add(new ClauseResponse(chunk.clauseNo(), chunk.clauseType().name(), chunk.typeSource().name(),
                    chunk.chunkText(), chunk.fullTextIndex(), chunk.charStart(), chunk.charEnd()));
        }
        return ApiResponse.ok(List.copyOf(responses));
    }

    private static ParseTaskResponse toResponse(ParseTaskRecord task) {
        return new ParseTaskResponse(String.valueOf(task.id()), String.valueOf(task.contractId()),
                task.status().name(), task.retryCount(), task.errorCode(), task.nextRetryAt(), task.updatedAt());
    }
}
