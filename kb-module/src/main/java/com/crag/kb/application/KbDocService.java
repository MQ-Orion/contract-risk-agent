package com.crag.kb.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.kb.application.port.KbDocRepository;
import com.crag.kb.domain.KbChunkKeys;
import com.crag.kb.domain.event.KbDocChangedEvent;
import com.crag.kb.domain.event.KbIndexChangedEvent;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbDocTypeEnum;
import com.crag.kb.domain.model.KbSyncStatusEnum;
import com.crag.kb.domain.model.SyncOutcome;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库文档用例（规范 4.2、5.6、6.6）：创建、更新正文、上下架与手动同步。
 *
 * <p>文档变更通过应用内事件触发异步增量同步；上/下架与同步完成都会发布索引变更事件，
 * 由检索模块按租户失效缓存。</p>
 *
 * <p>调用链：{@code KbDocController} → 本类（事务）→ {@code KbDocRepository}；提交后由
 * {@code KbSyncEventListener}（AFTER_COMMIT + 异步）→ {@code KbIndexSyncService.sync}。
 * 事件在事务内发布、提交后投递：回滚时不会触发同步，避免“库里没数据、向量却已写入”的不一致（规范 3.8、5.6）。</p>
 *
 * <p>两类事件不可混用：{@code KbDocChangedEvent} 表示“正文/上下架变了，需要同步”，由同步监听器消费；
 * {@code KbIndexChangedEvent} 表示“索引已变，需失效缓存”，由检索模块消费。混用会造成同步自触发（见提交 9f4dea6）。</p>
 */
@Service
public class KbDocService {

    private static final Logger log = LoggerFactory.getLogger(KbDocService.class);

    private final KbDocRepository docRepository;
    private final KbIndexSyncService syncService;
    private final SnowflakeIdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 构造服务。
     *
     * @param docRepository  文档仓储
     * @param syncService    同步服务
     * @param idGenerator    雪花标识
     * @param eventPublisher 事件发布器
     * @param clock          统一时钟
     */
    public KbDocService(KbDocRepository docRepository, KbIndexSyncService syncService,
                        SnowflakeIdGenerator idGenerator, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.docRepository = docRepository;
        this.syncService = syncService;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 新建知识库文档。
     *
     * @param tenantId 租户
     * @param createdBy 创建人
     * @param title    标题
     * @param docType  文档类型
     * @param content  正文
     * @param activate 是否立即上架
     * @return 文档
     */
    @Transactional
    public KbDocRecord create(long tenantId, long createdBy, String title, KbDocTypeEnum docType,
                              String content, boolean activate) {
        if (title == null || title.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "title required");
        }
        if (content == null || content.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "content required");
        }
        // 逻辑文档标识：标题的 SHA-256 哈希值
        String docKey = KbChunkKeys.docKeyOf(title);
        // 版本号：逻辑文档版本号 + 1
        int version = docRepository.listVersions(tenantId, docKey).size() + 1;
        // 文档正文哈希值：正文的 SHA-256 哈希值
        String sourceHash = KbChunkKeys.contentHash(content);
        KbDocRecord doc = new KbDocRecord(idGenerator.nextId(), tenantId, docKey, title.trim(), docType, version,
                activate, sourceHash, null, null, KbSyncStatusEnum.PENDING, content, clock.instant());
        if (activate) {
            deactivateSameDocKey(tenantId, docKey);
        }
        docRepository.insert(doc);
        log.info("知识库文档创建 tenantId={} docId={} docKey={} version={} active={}",
                tenantId, doc.id(), docKey, version, activate);
        if (activate) {
            eventPublisher.publishEvent(new KbDocChangedEvent(tenantId, doc.id(), "create"));
        }
        return doc;
    }

    /**
     * 更新正文（同版本内就地更新，触发增量重嵌入）。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @param content  新正文
     * @return 更新后的文档
     */
    @Transactional
    public KbDocRecord updateContent(long tenantId, long docId, String content) {
        if (content == null || content.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "content required");
        }
        KbDocRecord doc = docRepository.findById(tenantId, docId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "kb doc " + docId));
        KbDocRecord updated = doc.withContent(content, KbChunkKeys.contentHash(content));
        docRepository.update(updated);
        if (doc.active()) {
            eventPublisher.publishEvent(new KbDocChangedEvent(tenantId, docId, "content-updated"));
        }
        return updated;
    }

    /**
     * 上架：同一逻辑文档只保留一个上架版本。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @return 上架后的文档
     */
    @Transactional
    public KbDocRecord publish(long tenantId, long docId) {
        KbDocRecord doc = docRepository.findById(tenantId, docId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "kb doc " + docId));
        deactivateSameDocKey(tenantId, doc.docKey());
        docRepository.updateActive(tenantId, docId, true);
        KbDocRecord published = doc.withActive(true);
        eventPublisher.publishEvent(new KbDocChangedEvent(tenantId, docId, "publish"));
        return published;
    }

    /**
     * 下架。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @return 下架后的文档
     */
    @Transactional
    public KbDocRecord unpublish(long tenantId, long docId) {
        KbDocRecord doc = docRepository.findById(tenantId, docId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "kb doc " + docId));
        docRepository.updateActive(tenantId, docId, false);
        KbDocRecord unpublished = doc.withActive(false);
        eventPublisher.publishEvent(new KbIndexChangedEvent(tenantId, docId, "unpublish"));
        return unpublished;
    }

    /**
     * 手动触发同步（管理端重试用）。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @return 同步结果
     */
    public SyncOutcome syncNow(long tenantId, long docId) {
        return syncService.sync(tenantId, docId);
    }

    // 下架：同一逻辑文档只保留一个上架版本。
    private void deactivateSameDocKey(long tenantId, String docKey) {
        List<KbDocRecord> versions = docRepository.listVersions(tenantId, docKey);
        for (KbDocRecord version : versions) {
            if (version.active()) {
                docRepository.updateActive(tenantId, version.id(), false);
            }
        }
    }
}
