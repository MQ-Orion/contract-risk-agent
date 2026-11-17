package com.crag.doc.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.doc.application.port.ClauseTypeFallbackPort;
import com.crag.doc.application.port.DocumentParsingPort;
import com.crag.doc.application.port.ParseLockPort;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.doc.domain.model.ParseStatusEnum;
import com.crag.doc.domain.model.ParseTaskRecord;
import com.crag.common.text.ClauseSplitter;
import com.crag.common.text.ClauseTypeClassifier;
import com.crag.common.text.SplitConfig;
import com.crag.doc.domain.validation.TextQualityChecker;
import com.crag.doc.infrastructure.lock.RedisParseLock;
import com.crag.doc.infrastructure.parse.DocxTableExtractor;
import com.crag.doc.infrastructure.parse.PdfTableExtractor;
import com.crag.doc.infrastructure.parse.TikaDocumentParsingAdapter;
import com.crag.doc.infrastructure.storage.LocalContractFileStorage;
import com.crag.doc.support.FakeChunkRepository;
import com.crag.doc.support.FakeContractRepository;
import com.crag.doc.support.FakeParseTaskRepository;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 解析流水线测试（规范 5.2、5.5）：状态推进、切片落库、失败重试记录、锁互斥。
 */
class ParsePipelineServiceTest {

    private static final long TENANT = 5L;
    private static final long CONTRACT_ID = 1001L;
    private static final long TASK_ID = 2001L;
    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final String FILE_HASH = "a".repeat(64);

    @TempDir
    Path tempDir;

    private FakeContractRepository contractRepository;
    private FakeParseTaskRepository taskRepository;
    private FakeChunkRepository chunkRepository;
    private LocalContractFileStorage storage;
    private ParseLockPort lock;
    private Clock clock;

    @BeforeEach
    void setUp() {
        contractRepository = new FakeContractRepository();
        taskRepository = new FakeParseTaskRepository();
        chunkRepository = new FakeChunkRepository();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        storage = new LocalContractFileStorage(tempDir, clock);
        lock = new RedisParseLock.InMemory();
    }

    @Test
    @DisplayName("解析成功：状态推进到就绪并写入切片")
    void executesPipelineSuccessfully() throws Exception {
        seedContract(docxWithClauses());
        ParsePipelineService pipeline = newPipeline(new TikaDocumentParsingAdapter(
                new DocxTableExtractor(), new PdfTableExtractor()));

        pipeline.execute(TASK_ID);

        ParseTaskRecord task = taskRepository.findById(TASK_ID).orElseThrow();
        assertThat(task.status()).isEqualTo(ParseStatusEnum.READY);
        assertThat(task.retryCount()).isZero();
        assertThat(chunkRepository.findByContractId(TENANT, CONTRACT_ID)).isNotEmpty();
    }

    @Test
    @DisplayName("解析失败：记录错误码、重试次数与指数退避时间")
    void recordsFailureWithBackoff() throws Exception {
        seedContract(docxWithClauses());
        DocumentParsingPort failing = (file, format) -> {
            throw BizException.of(ErrorCode.PARSE_FAILED, "synthetic failure");
        };
        ParsePipelineService pipeline = newPipeline(failing);

        pipeline.execute(TASK_ID);

        ParseTaskRecord task = taskRepository.findById(TASK_ID).orElseThrow();
        assertThat(task.status()).isEqualTo(ParseStatusEnum.FAILED);
        assertThat(task.retryCount()).isEqualTo(1);
        assertThat(task.errorCode()).isEqualTo("PARSE_FAILED");
        assertThat(task.nextRetryAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(chunkRepository.findByContractId(TENANT, CONTRACT_ID)).isEmpty();
    }

    @Test
    @DisplayName("锁被占用时跳过执行，任务保持待处理，避免重复解析")
    void skipsExecutionWhenLockHeld() throws Exception {
        seedContract(docxWithClauses());
        assertThat(lock.tryLock("lock:parse:" + FILE_HASH, Duration.ofMinutes(10))).isTrue();
        ParsePipelineService pipeline = newPipeline(new TikaDocumentParsingAdapter(
                new DocxTableExtractor(), new PdfTableExtractor()));

        pipeline.execute(TASK_ID);

        assertThat(taskRepository.findById(TASK_ID).orElseThrow().status()).isEqualTo(ParseStatusEnum.PENDING);
        assertThat(chunkRepository.findByContractId(TENANT, CONTRACT_ID)).isEmpty();
    }

    @Test
    @DisplayName("空文本解析失败并给出解析失败错误码")
    void failsOnEmptyText() throws Exception {
        seedContract(docxWithClauses());
        DocumentParsingPort emptyParser = (file, format) -> new com.crag.doc.domain.model.ParsedDocument(
                "   ", 0, false, DocumentFormatEnum.DOCX, java.util.List.of(), java.util.Map.of());
        ParsePipelineService pipeline = newPipeline(emptyParser);

        pipeline.execute(TASK_ID);

        ParseTaskRecord task = taskRepository.findById(TASK_ID).orElseThrow();
        assertThat(task.status()).isEqualTo(ParseStatusEnum.FAILED);
        assertThat(task.errorCode()).isEqualTo("PARSE_FAILED");
    }

    private ParsePipelineService newPipeline(DocumentParsingPort parsingPort) {
        ClauseSplitter splitter = new ClauseSplitter(SplitConfig.defaults(), new ClauseTypeClassifier());
        ClauseSplitService splitService = new ClauseSplitService(splitter, ClauseTypeFallbackPort.noop(),
                new SnowflakeIdGenerator(2L));
        ParseProgressWriter writer = new ParseProgressWriter(taskRepository, contractRepository, chunkRepository);
        return new ParsePipelineService(taskRepository, contractRepository, storage, parsingPort,
                new TextQualityChecker(), splitService, writer, lock, clock);
    }

    private void seedContract(byte[] content) throws Exception {
        Path temp = tempDir.resolve("upload.docx");
        Files.write(temp, content);
        String relative = storage.store(temp, TENANT, CONTRACT_ID, DocumentFormatEnum.DOCX);
        contractRepository.seed(ContractRecord.created(CONTRACT_ID, TENANT, "采购合同.docx",
                FILE_HASH, relative, DocumentFormatEnum.DOCX, content.length, 1L, NOW));
        taskRepository.insert(ParseTaskRecord.pending(TASK_ID, CONTRACT_ID, TENANT, NOW));
    }

    private byte[] docxWithClauses() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("设备采购合同");
            document.createParagraph().createRun().setText("第一条 合同标的");
            document.createParagraph().createRun().setText("甲方向乙方采购服务器十台。");
            document.createParagraph().createRun().setText("第二条 付款方式");
            document.createParagraph().createRun().setText("合同签订后十日内支付预付款。");
            document.write(out);
            return out.toByteArray();
        }
    }
}
