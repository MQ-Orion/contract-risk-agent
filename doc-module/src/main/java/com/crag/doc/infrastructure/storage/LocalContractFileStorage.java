package com.crag.doc.infrastructure.storage;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.ContractFileStorage;
import com.crag.doc.domain.model.DocumentFormatEnum;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地文件存储：按“租户/年月/合同标识.扩展名”生成路径（规范 5.1）。
 *
 * <p>原始文件名不参与路径拼接，杜绝路径穿越；存储根目录不得位于网站静态资源目录下，
 * 合同原文只能经由鉴权接口读取（规范 5.1、11.2）。</p>
 */
public class LocalContractFileStorage implements ContractFileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalContractFileStorage.class);
    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final Path root;
    private final Clock clock;

    /**
     * 构造存储。
     *
     * @param root  存储根目录（绝对路径）
     * @param clock 统一时钟
     */
    public LocalContractFileStorage(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create storage root " + this.root, e);
        }
        log.info("合同存储根目录 root={}", this.root);
    }

    @Override
    public String store(Path sourceFile, long tenantId, long contractId, DocumentFormatEnum format) {
        String month = MONTH_FORMAT.format(clock.instant());
        String relative = tenantId + "/" + month + "/" + contractId + "." + format.extension();
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            return relative;
        } catch (IOException e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "store file failed: " + e.getMessage());
        }
    }

    @Override
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("..")) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "illegal storage path");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "path escapes storage root");
        }
        return resolved;
    }
}
