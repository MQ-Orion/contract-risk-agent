package com.crag.kb.application;

import com.crag.kb.application.port.KbChunkRepository;
import com.crag.kb.application.port.KbDocRepository;
import com.crag.kb.domain.model.KbChunkRecord;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbSyncStatusEnum;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库同步落库器：只包裹数据库操作，嵌入调用在事务外完成（规范 3.8）。
 */
@Service
public class KbSyncWriter {

    private static final Logger log = LoggerFactory.getLogger(KbSyncWriter.class);

    private final KbDocRepository docRepository;
    private final KbChunkRepository chunkRepository;
    private final Clock clock;

    /**
     * 构造落库器。
     *
     * @param docRepository   文档仓储
     * @param chunkRepository 切片仓储
     * @param clock           统一时钟
     */
    public KbSyncWriter(KbDocRepository docRepository, KbChunkRepository chunkRepository, Clock clock) {
        this.docRepository = docRepository;
        this.chunkRepository = chunkRepository;
        this.clock = clock;
    }

    /**
     * 置为向量化中。
     *
     * @param doc 文档
     * @return 更新后的文档
     */
    @Transactional
    public KbDocRecord markEmbedding(KbDocRecord doc) {
        KbDocRecord current = doc;
        if (current.syncStatus() != KbSyncStatusEnum.PENDING) {
            current = current.withStatus(KbSyncStatusEnum.PENDING);
        }
        KbDocRecord embedding = current.withStatus(KbSyncStatusEnum.EMBEDDING);
        docRepository.update(embedding);
        return embedding;
    }

    /**
     * 写入切片并置为就绪。
     *
     * <p>顺序不可调换：先删过期、再 upsert 变化切片、最后把未变化切片重绑到当前版本。
     * 若先重绑再删过期，同一事务内会出现“刚挂到新版本又被删掉”的中间态；虽然外部不可见，
     * 但一旦事务隔离级别或重试策略改动，就会变成真实的悬空切片。单事务写两张表（kb_chunk、kb_doc），
     * 符合规范 3.8 的三表上限。</p>
     *
     * @param doc       文档（处于向量化中）
     * @param changed   需要写入的切片（含向量）
     * @param staleKeys 需要删除的过期业务键
     * @param activeKeys 当前版本仍存在的业务键，用于版本切换时重绑归属
     * @return 更新后的文档
     */
    @Transactional
    public KbDocRecord persist(KbDocRecord doc, List<KbChunkRecord> changed, List<String> staleKeys,
                               List<String> activeKeys) {
        chunkRepository.deleteByKeys(staleKeys);
        chunkRepository.upsertChanged(changed);
        chunkRepository.rebindDocId(doc.tenantId(), doc.id(), activeKeys);
        Instant now = clock.instant();
        KbDocRecord synced = doc.synced(doc.sourceHash(), now);
        docRepository.update(synced);
        return synced;
    }

    /**
     * 标记失败。
     *
     * @param doc   文档
     * @param cause 失败原因
     */
    @Transactional
    public void markFailed(KbDocRecord doc, RuntimeException cause) {
        try {
            docRepository.update(doc.withStatus(KbSyncStatusEnum.FAILED));
        } catch (RuntimeException e) {
            log.error("知识库同步失败状态落库失败 docId={}", doc.id(), e);
        }
        log.error("知识库同步失败 docId={} cause={}", doc.id(), cause.getMessage());
    }
}
