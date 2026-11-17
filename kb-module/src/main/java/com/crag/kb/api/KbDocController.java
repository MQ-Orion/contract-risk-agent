package com.crag.kb.api;

import com.crag.common.api.ApiResponse;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.kb.api.dto.CreateKbDocRequest;
import com.crag.kb.api.dto.KbDocResponse;
import com.crag.kb.api.dto.KbSyncResponse;
import com.crag.kb.api.dto.UpdateKbDocContentRequest;
import com.crag.kb.application.KbDocService;
import com.crag.kb.application.KbQueryService;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.SyncOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库接口（规范 10.1、10.3）。
 */
@RestController
@RequestMapping("/api/v1/kb/documents")
@Tag(name = "知识库", description = "文档管理、上下架与增量同步")
public class KbDocController {

    private final KbDocService docService;
    private final KbQueryService queryService;

    /**
     * 构造接口。
     *
     * @param docService   文档用例
     * @param queryService 查询用例
     */
    public KbDocController(KbDocService docService, KbQueryService queryService) {
        this.docService = docService;
        this.queryService = queryService;
    }

    /**
     * 新建文档。
     *
     * @param request 请求体
     * @return 文档
     */
    @PostMapping
    @Operation(summary = "新建知识库文档", description = "上架时异步触发增量向量写入")
    public ApiResponse<KbDocResponse> create(@Valid @RequestBody CreateKbDocRequest request) {
        KbDocRecord doc = docService.create(TenantContext.require(), UserContext.require(),
                request.title(), request.docType(), request.content(), request.activate());
        return ApiResponse.ok(toResponse(doc));
    }

    /**
     * 列出文档。
     *
     * @param active 上架过滤，可为空
     * @return 文档列表
     */
    @GetMapping
    @Operation(summary = "知识库文档列表")
    public ApiResponse<List<KbDocResponse>> list(@RequestParam(required = false) Boolean active) {
        List<KbDocRecord> docs = queryService.list(TenantContext.require(), active);
        List<KbDocResponse> responses = new ArrayList<>(docs.size());
        for (KbDocRecord doc : docs) {
            responses.add(toResponse(doc));
        }
        return ApiResponse.ok(List.copyOf(responses));
    }

    /**
     * 文档详情。
     *
     * @param docId 文档标识
     * @return 文档
     */
    @GetMapping("/{docId}")
    @Operation(summary = "知识库文档详情")
    public ApiResponse<KbDocResponse> detail(@PathVariable long docId) {
        return ApiResponse.ok(toResponse(queryService.detail(TenantContext.require(), docId)));
    }

    /**
     * 更新正文。
     *
     * @param docId   文档标识
     * @param request 请求体
     * @return 文档
     */
    @PutMapping("/{docId}/content")
    @Operation(summary = "更新知识库正文", description = "已上架文档会触发增量重嵌入，仅变化切片重写向量")
    public ApiResponse<KbDocResponse> updateContent(@PathVariable long docId,
                                                    @Valid @RequestBody UpdateKbDocContentRequest request) {
        return ApiResponse.ok(toResponse(
                docService.updateContent(TenantContext.require(), docId, request.content())));
    }

    /**
     * 上架。
     *
     * @param docId 文档标识
     * @return 文档
     */
    @PostMapping("/{docId}/publish")
    @Operation(summary = "上架文档", description = "同一逻辑文档只保留一个上架版本")
    public ApiResponse<KbDocResponse> publish(@PathVariable long docId) {
        return ApiResponse.ok(toResponse(docService.publish(TenantContext.require(), docId)));
    }

    /**
     * 下架。
     *
     * @param docId 文档标识
     * @return 文档
     */
    @PostMapping("/{docId}/unpublish")
    @Operation(summary = "下架文档")
    public ApiResponse<KbDocResponse> unpublish(@PathVariable long docId) {
        return ApiResponse.ok(toResponse(docService.unpublish(TenantContext.require(), docId)));
    }

    /**
     * 手动同步。
     *
     * @param docId 文档标识
     * @return 同步结果
     */
    @PostMapping("/{docId}/sync")
    @Operation(summary = "手动同步向量", description = "失败后的人工重试入口")
    public ApiResponse<KbSyncResponse> sync(@PathVariable long docId) {
        SyncOutcome outcome = docService.syncNow(TenantContext.require(), docId);
        return ApiResponse.ok(new KbSyncResponse(outcome.inserted(), outcome.updated(), outcome.skipped(),
                outcome.deleted(), outcome.skipRatio()));
    }

    private static KbDocResponse toResponse(KbDocRecord doc) {
        return new KbDocResponse(String.valueOf(doc.id()), doc.docKey(), doc.title(), doc.docType().name(),
                doc.versionNo(), doc.active(), doc.syncStatus().name(), doc.lastSyncedAt(), doc.createdAt());
    }
}
