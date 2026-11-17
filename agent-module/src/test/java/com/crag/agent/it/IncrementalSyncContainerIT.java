package com.crag.agent.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.domain.TenantContext;
import com.crag.kb.application.KbDocService;
import com.crag.kb.application.KbIndexSyncService;
import com.crag.kb.application.KbQueryService;
import com.crag.kb.application.port.EmbeddingPort;
import com.crag.kb.domain.KbChunkKeys;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbDocTypeEnum;
import com.crag.kb.domain.model.KbSyncStatusEnum;
import com.crag.kb.domain.model.SyncOutcome;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 增量向量写入的容器化集成测试（规范 13.1、4.3）。
 *
 * <p>验证验收条件：局部修改单条款后，仅该切片的内容哈希与向量被重写，其余切片的哈希与更新时间不变。
 * 使用本地镜像 {@code crag-postgres:16-zhparser}（pgvector + zhparser）与 {@code redis:8}，
 * 按 {@code -Dcrag.it=true} 启用。</p>
 */
@SpringBootTest
@Testcontainers
@EnabledIfSystemProperty(named = "crag.it", matches = "true")
class IncrementalSyncContainerIT {

    private static final long TENANT = 1L;
    private static final long USER = 1L;
    private static final String TITLE = "增量同步测试制度";
    private static final String SECOND_CLAUSE = "第二条";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("crag-postgres:16-zhparser").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("contract_risk")
            .withUsername("crag")
            .withPassword("crag-dev");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
        REDIS.start();
        applyInitScripts();
    }

    @Autowired
    private KbDocService docService;
    @Autowired
    private KbQueryService queryService;
    @Autowired
    private KbIndexSyncService syncService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private com.crag.kb.application.port.KbChunkRepository chunkRepository;

    /**
     * 用确定性向量替身替换真实模型调用，保证测试离线可跑。
     */
    @TestConfiguration
    static class StubEmbeddingConfig {

        /**
         * 主向量端口。
         *
         * @return 替身
         */
        @Bean
        @Primary
        EmbeddingPort stubEmbeddingPort() {
            return new EmbeddingPort() {
                @Override
                public List<float[]> embed(List<String> texts) {
                    List<float[]> vectors = new java.util.ArrayList<>(texts.size());
                    for (String text : texts) {
                        float[] vector = new float[1024];
                        for (int i = 0; i < vector.length; i++) {
                            vector[i] = ((text.hashCode() + i) % 97) / 97F;
                        }
                        vectors.add(vector);
                    }
                    return vectors;
                }

                @Override
                public int dimension() {
                    return 1024;
                }
            };
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("crag.embedding.api-key", () -> "test-key");
    }

    @Test
    @DisplayName("局部修改单条款：仅该切片重写向量，其余切片哈希与更新时间不变")
    void rewritesOnlyChangedChunk() {
        TenantContext.set(TENANT);
        try {
            long docId = docService.create(TENANT, USER, TITLE, KbDocTypeEnum.INSTITUTION,
                    initialContent(), true).id();
            awaitReady(docId);

            Map<String, ChunkRow> before = chunkSnapshot(docId);
            assertThat(before).hasSize(3);

            String updatedContent = initialContent().replace("百分之三十", "百分之二十");
            docService.updateContent(TENANT, docId, updatedContent);
            // 更新正文会发布事件并异步增量同步；等状态回到就绪后再核对快照
            awaitReady(docId);

            Map<String, ChunkRow> after = chunkSnapshot(docId);
            String changedKey = KbChunkKeys.chunkKey(KbChunkKeys.docKeyOf(TITLE), SECOND_CLAUSE, 0);
            assertThat(after.get(changedKey).hash()).isNotEqualTo(before.get(changedKey).hash());
            assertThat(after.get(changedKey).updatedAt()).isAfter(before.get(changedKey).updatedAt());

            for (Map.Entry<String, ChunkRow> entry : before.entrySet()) {
                if (entry.getKey().equals(changedKey)) {
                    continue;
                }
                assertThat(after.get(entry.getKey()).hash())
                        .as("未变化切片哈希应保持不变：%s", entry.getKey())
                        .isEqualTo(entry.getValue().hash());
                assertThat(after.get(entry.getKey()).updatedAt())
                        .as("未变化切片更新时间应保持不变：%s", entry.getKey())
                        .isEqualTo(entry.getValue().updatedAt());
            }

            // 增量判据必须命中已有切片；若此处为空，说明按 docKey 的哈希查询失效（会导致整篇重嵌入）
            assertThat(chunkRepository.contentHashesByDocKey(TENANT, KbChunkKeys.docKeyOf(TITLE)))
                    .as("按 docKey 查询到的切片哈希")
                    .hasSize(3);

            // 再同步一次应为幂等：全部跳过、不重写向量。
            // 注意：异步同步在置 READY 之后、释放锁之前还有一个极短窗口，此时手动同步会拿到“锁被占用”的空结果，
            // 因此这里重试到拿到一次真实执行结果，避免把并发时序当成幂等性失败。
            SyncOutcome again = syncUntilExecuted(docId);
            assertThat(again.updated()).as("outcome=%s", again).isZero();
            assertThat(again.skipped()).as("outcome=%s", again).isEqualTo(3);
            assertThat(again.inserted()).as("outcome=%s", again).isZero();

            Integer withEmbedding = jdbcTemplate.queryForObject(
                    "select count(*) from kb_chunk where kb_doc_id = ? and embedding is not null",
                    Integer.class, docId);
            assertThat(withEmbedding).isEqualTo(3);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("版本切换：未变化切片不重嵌入，但全部切片重绑到新版本")
    void versionSwitchRebindsWithoutReembedding() {
        TenantContext.set(TENANT);
        try {
            String title = "版本切换测试制度";
            KbDocRecord v1 = docService.create(TENANT, USER, title, KbDocTypeEnum.INSTITUTION, initialContent(), true);
            awaitReady(v1.id());
            Map<String, ChunkRow> before = chunkSnapshot(v1.id());
            assertThat(before).hasSize(3);

            String v2Content = initialContent().replace("百分之三十", "百分之二十");
            KbDocRecord v2 = docService.create(TENANT, USER, title, KbDocTypeEnum.INSTITUTION, v2Content, true);
            assertThat(v2.versionNo()).isEqualTo(2);
            awaitReady(v2.id());

            // 切片行跨版本唯一（规范 4.3 的 chunk_key 全局唯一），切换后必须整体挂到新版本，
            // 否则检索按 kb_doc_id 关联上架版本会得到空结果（规范 9.5）。
            assertThat(chunkCount(v1.id())).isZero();
            Map<String, ChunkRow> after = chunkSnapshot(v2.id());
            assertThat(after).hasSize(3);

            String changedKey = KbChunkKeys.chunkKey(KbChunkKeys.docKeyOf(title), SECOND_CLAUSE, 0);
            assertThat(after.get(changedKey).hash()).isNotEqualTo(before.get(changedKey).hash());
            for (Map.Entry<String, ChunkRow> entry : before.entrySet()) {
                if (entry.getKey().equals(changedKey)) {
                    continue;
                }
                assertThat(after.get(entry.getKey()).hash())
                        .as("未变化切片哈希应保持不变：%s", entry.getKey())
                        .isEqualTo(entry.getValue().hash());
                assertThat(after.get(entry.getKey()).updatedAt())
                        .as("未变化切片只重绑归属，更新时间不应变化：%s", entry.getKey())
                        .isEqualTo(entry.getValue().updatedAt());
            }
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 重试同步直到真正执行（非“锁被占用”的空结果）。
     *
     * @param docId 文档标识
     * @return 执行结果
     */
    private SyncOutcome syncUntilExecuted(long docId) {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            SyncOutcome outcome = syncService.sync(TENANT, docId);
            boolean lockBusy = outcome.inserted() == 0 && outcome.updated() == 0 && outcome.skipped() == 0;
            if (!lockBusy) {
                return outcome;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
        throw new AssertionError("同步锁长时间被占用，无法取得幂等性证据");
    }

    /**
     * 统计某文档标识下的切片数。
     *
     * @param docId 文档标识
     * @return 切片数
     */
    private int chunkCount(long docId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from kb_chunk where kb_doc_id = ? and deleted = 0", Integer.class, docId);
        return count == null ? 0 : count;
    }

    private void awaitReady(long docId) {
        long deadline = System.currentTimeMillis() + 120_000L;
        while (System.currentTimeMillis() < deadline) {
            KbDocRecord doc = queryService.detail(TENANT, docId);
            if (doc.syncStatus() == KbSyncStatusEnum.READY) {
                return;
            }
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
        throw new AssertionError("知识库同步超时");
    }

    private Map<String, ChunkRow> chunkSnapshot(long docId) {
        Map<String, ChunkRow> snapshot = new HashMap<>();
        jdbcTemplate.query(
                "select chunk_key, content_hash, updated_at from kb_chunk where kb_doc_id = ?",
                rs -> {
                    snapshot.put(rs.getString("chunk_key"),
                            new ChunkRow(rs.getString("content_hash"), rs.getTimestamp("updated_at")));
                }, docId);
        return snapshot;
    }

    private static String initialContent() {
        return "第一条 适用范围\n本制度适用于增量同步测试场景。\n"
                + "第二条 付款要求\n预付款不得超过合同总价的百分之三十。\n"
                + "第三条 保密义务\n双方应对合同内容承担保密义务。";
    }

    private static void applyInitScripts() {
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        Path initDir = root.resolve("db/init");
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String file : new String[]{"00_extensions.sql", "10_tsearch_cn.sql", "20_functions.sql"}) {
                String sql = Files.readString(initDir.resolve(file), StandardCharsets.UTF_8);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化脚本执行失败", e);
        }
    }

    /**
     * 切片快照行。
     *
     * @param hash      内容哈希
     * @param updatedAt 更新时间
     */
    record ChunkRow(String hash, Timestamp updatedAt) {
    }
}
