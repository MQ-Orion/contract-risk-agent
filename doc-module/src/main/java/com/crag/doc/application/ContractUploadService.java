package com.crag.doc.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.doc.application.port.ContractFileStorage;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.application.port.ParseTaskDispatcher;
import com.crag.doc.application.port.ParseTaskRepository;
import com.crag.doc.domain.DocConstants;
import com.crag.doc.domain.model.ContractRecord;
import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.doc.domain.model.ParseTaskRecord;
import com.crag.doc.domain.validation.DocumentFormatDetector;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文件接收用例（规范 5.1、10.5）：白名单与魔数校验、大小限制、哈希去重秒传、按租户年月落盘、建解析任务。
 *
 * <p>文件先落临时文件并计算 SHA-256，校验通过后才进入存储目录；存储路径不使用原始文件名，避免路径穿越。</p>
 *
 * <p>调用链：{@code ContractController.upload} → 本方法 → 临时文件落盘/哈希（文件 IO）→ 仓储查询与写入（各自动提交）
 * → {@code ensureTask} → {@code ParseTaskDispatcher.submit}（解析线程池）→ {@code ParsePipelineService.execute}。</p>
 *
 * <p>本方法不开启事务：文件 IO 与哈希不能进事务（规范 3.8）。由此产生两处可接受的中间态：
 * 合同已插入但任务插入失败时，合同留在库中、无任务，用户重新上传会命中秒传并补建任务；
 * 任务已插入但提交线程池失败时，任务停在 PENDING，由人工重试入口兜底（规范 5.5）。</p>
 */
@Service
public class ContractUploadService {

    private static final Logger log = LoggerFactory.getLogger(ContractUploadService.class);
    private static final String HASH_ALGORITHM = "SHA-256";

    private final ContractRepository contractRepository;
    private final ParseTaskRepository taskRepository;
    private final ContractFileStorage storage;
    private final DocumentFormatDetector formatDetector;
    private final ParseTaskDispatcher dispatcher;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock clock;

    /**
     * 构造服务。
     *
     * @param contractRepository 合同仓储
     * @param taskRepository     任务仓储
     * @param storage            文件存储
     * @param formatDetector     格式校验器
     * @param dispatcher         解析任务调度端口
     * @param idGenerator        雪花标识
     * @param clock              统一时钟
     */
    public ContractUploadService(ContractRepository contractRepository,
                                 ParseTaskRepository taskRepository,
                                 ContractFileStorage storage,
                                 DocumentFormatDetector formatDetector,
                                 ParseTaskDispatcher dispatcher,
                                 SnowflakeIdGenerator idGenerator,
                                 Clock clock) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
        this.storage = storage;
        this.formatDetector = formatDetector;
        this.dispatcher = dispatcher;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 接收上传文件。
     *
     * @param tenantId         租户
     * @param uploaderId       上传人
     * @param originalFilename 原始文件名，仅用于取扩展名与展示
     * @param declaredSize     客户端声明的大小
     * @param content          文件内容
     * @return 上传结果
     */
    public UploadResult upload(long tenantId, long uploaderId, String originalFilename, long declaredSize,
                               InputStream content) {
        if (declaredSize > DocConstants.MAX_FILE_SIZE_BYTES) {
            throw BizException.of(ErrorCode.FILE_LIMIT_EXCEEDED, "declaredSize=" + declaredSize);
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("crag-upload-", ".tmp");
            long actualSize = copyWithDigest(content, temp);
            if (actualSize > DocConstants.MAX_FILE_SIZE_BYTES) {
                throw BizException.of(ErrorCode.FILE_LIMIT_EXCEEDED, "actualSize=" + actualSize);
            }
            String hash = sha256(temp);
            DocumentFormatEnum format = formatDetector.detect(temp, originalFilename);

            Optional<ContractRecord> existing = contractRepository.findByHash(tenantId, hash);
            if (existing.isPresent()) {
                // 规范 5.1：命中同租户同哈希即秒传，不重复落盘、不重复解析。
                // 判据必须是内容哈希而非文件名/大小：同一文件改名的场景很常见，按名判断会重复解析同一份合同。
                ContractRecord contract = existing.get();
                ParseTaskRecord task = ensureTask(tenantId, contract.id());
                log.info("秒传命中 tenantId={} contractId={} taskId={}", tenantId, contract.id(), task.id());
                return new UploadResult(contract.id(), task.id(), true);
            }

            long contractId = idGenerator.nextId();
            String relativePath = storage.store(temp, tenantId, contractId, format);
            ContractRecord contract = ContractRecord.created(contractId, tenantId, displayName(originalFilename),
                    hash, relativePath, format, actualSize, uploaderId, now());
            // 并发上传同一文件时，两侧都可能走到这里；由 uk_contract_main_tenant_hash 唯一索引兜底，
            // 后提交方得到唯一键冲突而不是两条合同记录（规范 5.1）。
            contractRepository.insert(contract);
            ParseTaskRecord task = ensureTask(tenantId, contractId);
            log.info("合同接收完成 tenantId={} contractId={} taskId={} size={} format={}",
                    tenantId, contractId, task.id(), actualSize, format);
            return new UploadResult(contractId, task.id(), false);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "upload io failed: " + e.getMessage());
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * 确保合同存在解析任务：已有任务一律复用，避免秒传命中后重复解析（规范 5.1）。
     *
     * <p>复用规则：READY 表示解析结果可直接使用；PENDING/PARSING/SPLITTING 表示正在进行；
     * FAILED 交由人工重试入口处理（规范 5.5），上传动作本身不重复触发解析，避免重复计费。</p>
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 任务
     */
    ParseTaskRecord ensureTask(long tenantId, long contractId) {
        Optional<ParseTaskRecord> latest = taskRepository.findLatestByContractId(tenantId, contractId);
        if (latest.isPresent()) {
            log.debug("复用已有解析任务 contractId={} status={}", contractId, latest.get().status());
            return latest.get();
        }
        ParseTaskRecord task = taskRepository.insert(
                ParseTaskRecord.pending(idGenerator.nextId(), contractId, tenantId, now()));
        dispatcher.submit(tenantId, task.id());
        return task;
    }

    private long copyWithDigest(InputStream content, Path target) throws IOException {
        MessageDigest digest = newDigest();
        long total = 0;
        byte[] buffer = new byte[8192];
        try (DigestInputStream in = new DigestInputStream(content, digest);
             var out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > DocConstants.MAX_FILE_SIZE_BYTES) {
                    throw BizException.of(ErrorCode.FILE_LIMIT_EXCEEDED, "stream exceeds limit");
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private String sha256(Path file) {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "hash failed: " + e.getMessage());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Instant now() {
        return clock.instant();
    }

    private static String displayName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "未命名合同";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("临时文件清理失败 path={}", path, e);
        }
    }
}
