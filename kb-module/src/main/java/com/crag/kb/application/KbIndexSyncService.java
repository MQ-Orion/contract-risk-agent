package com.crag.kb.application;

import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.common.api.ErrorCode;
import com.crag.common.text.ClauseSplitter;
import com.crag.common.text.ParsedClause;
import com.crag.kb.application.port.EmbeddingPort;
import com.crag.kb.application.port.KbChunkRepository;
import com.crag.kb.application.port.KbDocRepository;
import com.crag.kb.application.port.KbSyncLockPort;
import com.crag.kb.domain.KbChunkKeys;
import com.crag.kb.domain.event.KbIndexChangedEvent;
import com.crag.kb.domain.model.KbChunkRecord;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.SyncOutcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 知识库增量同步（规范 4.3、5.6）：五步流程 + 条件 upsert。
 *
 * <p>步骤：取文档 → 逐切片比对 content_hash → 仅变化切片调用嵌入 → 条件 upsert（未变化跳过）→ 删除过期切片并失效缓存。
 * 嵌入调用在事务外，落库在 {@link KbSyncWriter} 的短事务中（规范 3.8）。</p>
 */
@Service
public class KbIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(KbIndexSyncService.class);
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final String LOCK_PREFIX = "lock:embedding:";

    private final KbDocRepository docRepository;
    private final KbChunkRepository chunkRepository;
    private final EmbeddingPort embeddingPort;
    private final ClauseSplitter splitter;
    private final KbSyncLockPort lockPort;
    private final KbSyncWriter writer;
    private final SnowflakeIdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造同步服务。
     *
     * @param docRepository   文档仓储
     * @param chunkRepository 切片仓储
     * @param embeddingPort   向量化端口
     * @param splitter        共享切分器
     * @param lockPort        同步锁
     * @param writer          落库器
     * @param idGenerator     雪花标识
     * @param eventPublisher  事件发布器
     */
    public KbIndexSyncService(KbDocRepository docRepository,
                              KbChunkRepository chunkRepository,
                              EmbeddingPort embeddingPort,
                              ClauseSplitter splitter,
                              KbSyncLockPort lockPort,
                              KbSyncWriter writer,
                              SnowflakeIdGenerator idGenerator,
                              ApplicationEventPublisher eventPublisher) {
        this.docRepository = docRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingPort = embeddingPort;
        this.splitter = splitter;
        this.lockPort = lockPort;
        this.writer = writer;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 同步单个文档。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @return 同步结果
     */
    public SyncOutcome sync(long tenantId, long docId) {
        KbDocRecord doc = docRepository.findById(tenantId, docId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "kb doc " + docId));
        String lockKey = LOCK_PREFIX + docId;
        if (!lockPort.tryLock(lockKey, LOCK_TTL)) {
            log.info("增量同步锁被占用，跳过 docId={}", docId);
            return new SyncOutcome(0, 0, 0, 0);
        }
        KbDocRecord embedding = null;
        try {
            embedding = writer.markEmbedding(doc);
            List<ParsedClause> clauses = splitter.split(doc.contentText());
            // 按 docKey 取哈希而不是按 docId：chunk_key 跨版本稳定（规范 4.3、9.5），版本切换后新文档标识是新的，
            // 按 docId 取会让所有切片都被判成新增，绕过增量、触发整篇重嵌入——正是 9.5 要排除的做法。
            Map<String, String> existing = chunkRepository.contentHashesByDocKey(tenantId, doc.docKey());

            List<KbChunkRecord> changed = new ArrayList<>();
            // currentKeys 保留顺序用于重绑，seen 用于去重判重；两者职责不同，不能合成一个 Set（Set 无稳定顺序，
            // 分批重绑时顺序无关紧要，但日志与排错需要可复现的列表）。
            List<String> currentKeys = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            for (ParsedClause clause : clauses) {
                String key = KbChunkKeys.chunkKey(doc.docKey(), clause.clauseNo(), clause.partIndex());
                if (!seen.add(key)) {
                    log.warn("重复切片业务键，已跳过 key={} docId={}", key, docId);
                    continue;
                }
                currentKeys.add(key);
                String hash = KbChunkKeys.contentHash(clause.text());
                String previous = existing.get(key);
                if (hash.equals(previous)) {
                    skipped++;
                    continue;
                }
                if (previous == null) {
                    inserted++;
                } else {
                    updated++;
                }
                changed.add(new KbChunkRecord(idGenerator.nextId(), tenantId, docId, key, clause.clauseNo(),
                        clause.text(), clause.clauseType(), hash, null));
            }
            List<String> staleKeys = existing.keySet().stream()
                    .filter(key -> !seen.contains(key))
                    .toList();

            List<KbChunkRecord> withVectors = embedChanged(changed);
            writer.persist(embedding, withVectors, staleKeys, currentKeys);
            eventPublisher.publishEvent(new KbIndexChangedEvent(tenantId, docId, "sync"));
            SyncOutcome outcome = new SyncOutcome(inserted, updated, skipped, staleKeys.size());
            log.info("增量同步完成 docId={} inserted={} updated={} skipped={} deleted={} skipRatio={}",
                    docId, inserted, updated, skipped, staleKeys.size(), outcome.skipRatio());
            return outcome;
        } catch (RuntimeException e) {
            if (embedding != null) {
                writer.markFailed(embedding, e);
            }
            throw e;
        } finally {
            lockPort.unlock(lockKey);
        }
    }

    private List<KbChunkRecord> embedChanged(List<KbChunkRecord> changed) {
        if (changed.isEmpty()) {
            return List.of();
        }
        List<String> texts = changed.stream().map(KbChunkRecord::chunkText).toList();
        List<float[]> vectors = embeddingPort.embed(texts);
        List<KbChunkRecord> result = new ArrayList<>(changed.size());
        for (int i = 0; i < changed.size(); i++) {
            result.add(changed.get(i).withEmbedding(vectors.get(i)));
        }
        return result;
    }
}
