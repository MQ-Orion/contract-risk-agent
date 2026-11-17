package com.crag.kb.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.common.text.ClauseSplitter;
import com.crag.common.text.ClauseTypeClassifier;
import com.crag.common.text.SplitConfig;
import com.crag.kb.domain.KbChunkKeys;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbDocTypeEnum;
import com.crag.kb.domain.model.KbSyncStatusEnum;
import com.crag.kb.support.AlwaysFreeLock;
import com.crag.kb.support.FakeKbChunkRepository;
import com.crag.kb.support.FakeKbDocRepository;
import com.crag.kb.support.FixedEmbeddingPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 每日对账任务测试（规范 5.6、3.9）：只处理“在架且源哈希与同步快照不一致”的文档。
 */
class KbReconciliationJobTest {

    private static final Instant NOW = Instant.parse("2026-11-12T18:30:00Z");

    private FakeKbDocRepository docRepository;
    private FakeKbChunkRepository chunkRepository;
    private FixedEmbeddingPort embeddingPort;
    private KbReconciliationJob job;

    @BeforeEach
    void setUp() {
        docRepository = new FakeKbDocRepository();
        chunkRepository = new FakeKbChunkRepository();
        embeddingPort = new FixedEmbeddingPort(8);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        KbSyncWriter writer = new KbSyncWriter(docRepository, chunkRepository, clock);
        KbIndexSyncService syncService = new KbIndexSyncService(docRepository, chunkRepository, embeddingPort,
                new ClauseSplitter(SplitConfig.defaults(), new ClauseTypeClassifier()),
                new AlwaysFreeLock(), writer, new SnowflakeIdGenerator(1L), event -> { });
        job = new KbReconciliationJob(docRepository, syncService);
    }

    @Test
    @DisplayName("仅对账在架且哈希不一致的文档，已同步文档不重复处理")
    void reconcilesOnlyPendingDocs() {
        seed(1L, 11L, true, "hash-a", null);
        seed(1L, 12L, true, "hash-b", "hash-b");
        seed(2L, 21L, true, "hash-c", "old-hash");
        seed(2L, 22L, false, "hash-d", null);

        KbReconciliationJob.ReconcileReport report = job.reconcileAllTenants();

        assertThat(report.tenants()).isEqualTo(2);
        assertThat(report.pendingDocs()).isEqualTo(2);
        assertThat(report.synced()).isEqualTo(2);
        assertThat(report.failed()).isZero();
        assertThat(docRepository.get(11L).syncStatus()).isEqualTo(KbSyncStatusEnum.READY);
        assertThat(docRepository.get(21L).syncStatus()).isEqualTo(KbSyncStatusEnum.READY);
        assertThat(docRepository.get(12L).syncStatus()).isEqualTo(KbSyncStatusEnum.PENDING);
        assertThat(docRepository.get(22L).syncStatus()).isEqualTo(KbSyncStatusEnum.PENDING);
        assertThat(chunkRepository.chunksOf(11L)).isNotEmpty();
    }

    @Test
    @DisplayName("单文档失败不阻断其它文档，失败数被统计")
    void countsFailuresPerDoc() {
        seed(1L, 31L, true, "hash-x", null);
        embeddingPort.failAlways();

        KbReconciliationJob.ReconcileReport report = job.reconcileAllTenants();

        assertThat(report.pendingDocs()).isEqualTo(1);
        assertThat(report.synced()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        assertThat(docRepository.get(31L).syncStatus()).isEqualTo(KbSyncStatusEnum.FAILED);
    }

    private void seed(long tenantId, long docId, boolean active, String sourceHash, String syncedHash) {
        KbDocRecord doc = new KbDocRecord(docId, tenantId, KbChunkKeys.docKeyOf("制度-" + docId),
                "制度-" + docId, KbDocTypeEnum.INSTITUTION, 1, active, sourceHash, syncedHash,
                syncedHash == null ? null : NOW, KbSyncStatusEnum.PENDING,
                "第一条 适用范围\n本制度适用于测试场景。\n第二条 付款\n预付款不得超过三成。", NOW);
        docRepository.seed(doc);
    }
}
