package com.crag.agent.api;

import com.crag.common.api.ApiResponse;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.doc.application.port.DocumentParsingPort;
import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.doc.domain.model.ParsedDocument;
import com.crag.doc.domain.validation.DocumentFormatDetector;
import com.crag.kb.application.KbDocService;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbDocTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档文件上传（PDF / DOC / DOCX）。
 *
 * <p>规范 5.1 的格式白名单原本针对合同文件；知识库只存正文，因此这里复用同一套
 * “扩展名 + 魔数 + OOXML 包校验”后，用 {@link DocumentParsingPort} 解析成文本，
 * 再走 {@link KbDocService#create} 写库与增量向量化。KB 模块不依赖 doc 模块，
 * 所以组合逻辑放在同时依赖两者的 agent 模块。</p>
 *
 * <p>失败语义：类型不支持 20001、超过 50MB 20002、解析为空 20003；临时文件在 finally 删除。</p>
 */
@RestController
@RequestMapping("/api/v1/kb/documents")
@Tag(name = "知识库", description = "文档管理、上下架与增量同步")
public class KbDocumentUploadController {

    /** 单文件上限，与合同上传一致（规范 5.1）。 */
    private static final long MAX_BYTES = 50L * 1024 * 1024;

    private final KbDocService docService;
    private final DocumentParsingPort parsingPort;
    private final DocumentFormatDetector formatDetector;

    /**
     * 构造接口。
     *
     * @param docService    知识库文档用例
     * @param parsingPort   文档解析端口（Tika）
     * @param formatDetector 格式与魔数校验
     */
    public KbDocumentUploadController(KbDocService docService, DocumentParsingPort parsingPort,
                                      DocumentFormatDetector formatDetector) {
        this.docService = docService;
        this.parsingPort = parsingPort;
        this.formatDetector = formatDetector;
    }

    /**
     * 上传文档并创建知识库条目。
     *
     * @param file    文件，字段名固定为 file
     * @param title   标题，可空（默认取文件名）
     * @param docType 文档类型：INSTITUTION / CASE
     * @param activate 是否创建后立即上架
     * @return 创建结果
     * @throws IOException 临时文件读写失败
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "上传知识库文档", description = "PDF/DOC/DOCX，解析为正文后创建文档并触发增量向量化")
    public ApiResponse<KbUploadResponse> upload(@RequestPart("file") MultipartFile file,
                                                @RequestParam(required = false) String title,
                                                @RequestParam(defaultValue = "INSTITUTION") String docType,
                                                @RequestParam(defaultValue = "true") boolean activate)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "file required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.of(ErrorCode.FILE_LIMIT_EXCEEDED, "size=" + file.getSize());
        }
        KbDocTypeEnum type;
        try {
            type = KbDocTypeEnum.valueOf(docType);
        } catch (IllegalArgumentException e) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "docType=" + docType);
        }
        Path temp = Files.createTempFile("kb-upload-", ".tmp");
        try {
            file.transferTo(temp);
            DocumentFormatEnum format = formatDetector.detect(temp, file.getOriginalFilename());
            ParsedDocument parsed = parsingPort.parse(temp, format);
            String text = parsed.text();
            if (text == null || text.isBlank()) {
                throw BizException.of(ErrorCode.PARSE_FAILED, "empty text");
            }
            String finalTitle = title == null || title.isBlank()
                    ? stripExtension(file.getOriginalFilename()) : title.trim();
            long tenantId = TenantContext.require();
            long userId = UserContext.require();
            KbDocRecord doc = docService.create(tenantId, userId, finalTitle, type, text, activate);
            return ApiResponse.ok(new KbUploadResponse(String.valueOf(doc.id()), doc.title(),
                    doc.syncStatus().name(), doc.versionNo()));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "未命名文档";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /**
     * 上传结果。
     *
     * @param docId      文档标识
     * @param title      标题
     * @param syncStatus 同步状态
     * @param versionNo  版本号
     */
    public record KbUploadResponse(String docId, String title, String syncStatus, int versionNo) {
    }
}
