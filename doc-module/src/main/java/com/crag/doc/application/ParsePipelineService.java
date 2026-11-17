package com.crag.doc.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.ContractFileStorage;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.application.port.DocumentParsingPort;
import com.crag.doc.application.port.ParseLockPort;
import com.crag.doc.application.port.ParseTaskRepository;
import com.crag.doc.domain.DocConstants;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.doc.domain.model.ParseStatusEnum;
import com.crag.doc.domain.model.ParseTaskRecord;
import com.crag.common.text.ParsedClause;
import com.crag.doc.domain.model.ParsedDocument;
import com.crag.doc.domain.validation.QualityReport;
import com.crag.doc.domain.validation.TextQualityChecker;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 解析流水线：解析 → 质量判定 → 切分 → 入库（规范 5.2、5.5）。
 *
 * <p>并发控制：以文件哈希为粒度加分布式锁 {@code lock:parse:{hash}}，有效期 10 分钟，
 * 保证同一文件不会被并发解析（规范 5.2）。失败按指数退避记录下次可重试时间，最多 3 次（规范 5.5）。</p>
 *
 * <p>本方法不开启事务：解析与切分是 CPU/IO 密集操作，事务只包裹落库（规范 3.8）。</p>
 */
@Service
public class ParsePipelineService {

    private static final Logger log = LoggerFactory.getLogger(ParsePipelineService.class);

    private final ParseTaskRepository taskRepository;
    private final ContractRepository contractRepository;
    private final ContractFileStorage storage;
    private final DocumentParsingPort parsingPort;
    private final TextQualityChecker qualityChecker;
    private final ClauseSplitService splitService;
    private final ParseProgressWriter progressWriter;
    private final ParseLockPort lockPort;
    private final Clock clock;

    /**
     * 构造流水线。
     *
     * @param taskRepository     任务仓储
     * @param contractRepository 合同仓储
     * @param storage            文件存储
     * @param parsingPort        解析端口
     * @param qualityChecker     文本质量校验器
     * @param splitService       切分服务
     * @param progressWriter     进度写入器
     * @param lockPort           分布式锁端口
     * @param clock              统一时钟
     */
    public ParsePipelineService(ParseTaskRepository taskRepository,
                                ContractRepository contractRepository,
                                ContractFileStorage storage,
                                DocumentParsingPort parsingPort,
                                TextQualityChecker qualityChecker,
                                ClauseSplitService splitService,
                                ParseProgressWriter progressWriter,
                                ParseLockPort lockPort,
                                Clock clock) {
        this.taskRepository = taskRepository;
        this.contractRepository = contractRepository;
        this.storage = storage;
        this.parsingPort = parsingPort;
        this.qualityChecker = qualityChecker;
        this.splitService = splitService;
        this.progressWriter = progressWriter;
        this.lockPort = lockPort;
        this.clock = clock;
    }

    /**
     * 执行解析任务。
     *
     * @param taskId 任务标识
     */
    public void execute(long taskId) {
        ParseTaskRecord task = taskRepository.findById(taskId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "parse task " + taskId));
        long contractId = task.contractId();
        ContractRecord contract = contractRepository.findById(task.tenantId(), contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "contract " + contractId));
        String lockKey = DocConstants.PARSE_LOCK_KEY_PREFIX + contract.fileHash();
        if (!lockPort.tryLock(lockKey, DocConstants.PARSE_LOCK_TTL)) {
            log.info("解析锁被占用，跳过本次执行 taskId={} contractId={}", taskId, contract.id());
            return;
        }
        try {
            task = progressWriter.advance(task, ParseStatusEnum.PARSING, now());
            Path file = storage.resolve(contract.filePath());
            ParsedDocument document = parsingPort.parse(file, contract.format());
            QualityReport quality = qualityChecker.check(document.text());
            if (!quality.acceptable()) {
                throw BizException.of(ErrorCode.PARSE_FAILED, "quality: " + quality.reason());
            }
            if (document.pageCountKnown() && document.pageCount() > DocConstants.MAX_PAGE_COUNT) {
                throw BizException.of(ErrorCode.FILE_LIMIT_EXCEEDED, "pages=" + document.pageCount());
            }
            task = progressWriter.advance(task, ParseStatusEnum.SPLITTING, now());
            List<ParsedClause> clauses = splitService.split(document.text());
            if (clauses.isEmpty()) {
                throw BizException.of(ErrorCode.SPLIT_FAILED, "no clause produced");
            }
            List<ContractChunkRecord> chunks = splitService.toRecords(task.tenantId(), task.contractId(), clauses);
            progressWriter.persistChunksAndReady(task, chunks, document.pageCount(), now());
            log.info("解析完成 contractId={} taskId={} chunks={} pages={} tables={}",
                    contract.id(), taskId, chunks.size(), document.pageCount(), document.tables().size());
        } catch (BizException e) {
            handleFailure(task, e.errorCode().name(), e.detail() == null ? e.getMessage() : e.detail());
        } catch (RuntimeException e) {
            handleFailure(task, ErrorCode.PARSE_FAILED.name(), e.getClass().getName() + ": " + e.getMessage());
            log.error("解析异常 contractId={} taskId={}", contract.id(), taskId, e);
        } finally {
            lockPort.unlock(lockKey);
        }
    }

    private void handleFailure(ParseTaskRecord task, String errorCode, String errorMessage) {
        int retries = task.retryCount();
        Instant nextRetryAt = retries < DocConstants.MAX_PARSE_RETRY
                ? now().plus(DocConstants.backoff(retries))
                : null;
        try {
            progressWriter.fail(task, errorCode, truncate(errorMessage), nextRetryAt, now());
        } catch (RuntimeException e) {
            log.error("失败状态落库失败 taskId={}", task.id(), e);
        }
        log.warn("解析失败 taskId={} contractId={} code={} retry={} nextRetryAt={} detail={}",
                task.id(), task.contractId(), errorCode, retries, nextRetryAt, truncate(errorMessage));
    }

    /**
     * 查询任务。
     *
     * @param taskId 任务标识
     * @return 任务
     */
    public Optional<ParseTaskRecord> findTask(long taskId) {
        return taskRepository.findById(taskId);
    }

    private Instant now() {
        return clock.instant();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
