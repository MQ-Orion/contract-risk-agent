package com.crag.kb.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.kb.application.port.KbChunkRepository;
import com.crag.kb.application.port.KbDocRepository;
import com.crag.kb.domain.model.KbChunkRecord;
import com.crag.kb.domain.model.KbDocRecord;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 知识库查询用例。
 */
@Service
public class KbQueryService {

    private final KbDocRepository docRepository;
    private final KbChunkRepository chunkRepository;

    /**
     * 构造服务。
     *
     * @param docRepository   文档仓储
     * @param chunkRepository 切片仓储
     */
    public KbQueryService(KbDocRepository docRepository, KbChunkRepository chunkRepository) {
        this.docRepository = docRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * 列出文档。
     *
     * @param tenantId 租户
     * @param active   上架过滤，可为空
     * @return 文档列表
     */
    public List<KbDocRecord> list(long tenantId, Boolean active) {
        return docRepository.list(tenantId, active);
    }

    /**
     * 查询文档详情。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @return 文档
     */
    public KbDocRecord detail(long tenantId, long docId) {
        return docRepository.findById(tenantId, docId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "kb doc " + docId));
    }

    /**
     * 查询文档切片。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @return 切片
     */
    public List<KbChunkRecord> chunks(long tenantId, long docId) {
        detail(tenantId, docId);
        return chunkRepository.listByDocId(docId);
    }
}
