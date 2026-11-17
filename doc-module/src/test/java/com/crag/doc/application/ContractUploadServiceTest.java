package com.crag.doc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.doc.domain.model.ParseStatusEnum;
import com.crag.doc.domain.model.ParseTaskRecord;
import com.crag.doc.domain.validation.DocumentFormatDetector;
import com.crag.doc.infrastructure.storage.LocalContractFileStorage;
import com.crag.doc.support.FakeContractRepository;
import com.crag.doc.support.FakeParseTaskRepository;
import com.crag.doc.support.RecordingDispatcher;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文件接收用例测试（规范 5.1、10.5）：类型校验、大小限制、哈希秒传、任务创建。
 */
class ContractUploadServiceTest {

    private static final long TENANT = 7L;
    private static final long USER = 9L;

    @TempDir
    Path tempDir;

    private FakeContractRepository contractRepository;
    private FakeParseTaskRepository taskRepository;
    private RecordingDispatcher dispatcher;
    private ContractUploadService service;

    @BeforeEach
    void setUp() {
        contractRepository = new FakeContractRepository();
        taskRepository = new FakeParseTaskRepository();
        dispatcher = new RecordingDispatcher();
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T02:00:00Z"), ZoneOffset.UTC);
        service = new ContractUploadService(contractRepository, taskRepository,
                new LocalContractFileStorage(tempDir, clock), new DocumentFormatDetector(),
                dispatcher, new SnowflakeIdGenerator(1L), clock);
    }

    @Test
    @DisplayName("首次上传落盘、建任务并提交解析")
    void uploadsNewContract() throws IOException {
        byte[] content = docxBytes("设备采购合同", "第一条 合同标的");

        UploadResult result = service.upload(TENANT, USER, "采购合同.docx", content.length,
                new ByteArrayInputStream(content));

        assertThat(result.contractId()).isPositive();
        assertThat(result.taskId()).isPositive();
        assertThat(result.deduplicated()).isFalse();
        assertThat(contractRepository.size()).isEqualTo(1);
        assertThat(dispatcher.submitted()).containsExactly(result.taskId());
        ContractRecord stored = contractRepository.findById(TENANT, result.contractId()).orElseThrow();
        assertThat(stored.fileHash()).hasSize(64);
        assertThat(stored.filePath()).startsWith(TENANT + "/202611/");
        assertThat(stored.filePath()).doesNotContain("采购合同.docx");
        assertThat(stored.name()).isEqualTo("采购合同.docx");
    }

    @Test
    @DisplayName("同租户同哈希秒传，不重复解析")
    void reusesExistingContractByHash() throws IOException {
        byte[] content = docxBytes("保密协议", "第一条 保密范围");
        UploadResult first = service.upload(TENANT, USER, "a.docx", content.length, new ByteArrayInputStream(content));
        UploadResult second = service.upload(TENANT, USER, "b.docx", content.length, new ByteArrayInputStream(content));

        assertThat(second.deduplicated()).isTrue();
        assertThat(second.contractId()).isEqualTo(first.contractId());
        assertThat(second.taskId()).isEqualTo(first.taskId());
        assertThat(contractRepository.size()).isEqualTo(1);
        // 秒传不得重复触发解析（规范 5.1：命中时直接复用已有合同与解析结果）
        assertThat(dispatcher.submitted()).containsExactly(first.taskId());
    }

    @Test
    @DisplayName("合同已有就绪任务时不再新建任务")
    void reusesReadyTask() throws IOException {
        byte[] content = docxBytes("服务合同", "第一条 服务内容");
        UploadResult first = service.upload(TENANT, USER, "a.docx", content.length, new ByteArrayInputStream(content));
        Instant now = Instant.now();
        ParseTaskRecord task = taskRepository.findById(first.taskId()).orElseThrow();
        taskRepository.update(task.moveTo(ParseStatusEnum.PARSING, now)
                .moveTo(ParseStatusEnum.SPLITTING, now)
                .moveTo(ParseStatusEnum.READY, now));

        UploadResult again = service.upload(TENANT, USER, "b.docx", content.length, new ByteArrayInputStream(content));

        assertThat(again.taskId()).isEqualTo(first.taskId());
        assertThat(dispatcher.submitted()).hasSize(1);
    }

    @Test
    @DisplayName("声明体积超限直接拒绝")
    void rejectsOversizedDeclaredSize() {
        long tooLarge = 50L * 1024 * 1024 + 1;

        assertThatThrownBy(() -> service.upload(TENANT, USER, "big.pdf", tooLarge,
                new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("非白名单扩展名拒绝")
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> service.upload(TENANT, USER, "note.txt", 3,
                new ByteArrayInputStream("abc".getBytes())))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED);
    }

    private byte[] docxBytes(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(out);
            return out.toByteArray();
        }
    }
}
