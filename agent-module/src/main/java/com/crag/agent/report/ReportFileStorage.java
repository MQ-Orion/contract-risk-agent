package com.crag.agent.report;

import com.crag.agent.tool.ReportExportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 报告文件落盘（规范 9.3：每次导出重新生成并覆盖）。
 *
 * <p>路径按“租户/年月/合同”组织，文件名固定为合同标识，因此重复导出天然覆盖（report_url 语义为
 * “最近一次导出文件的位置”，不是第二份事实来源）。</p>
 */
public class ReportFileStorage {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;
    private final Clock clock;

    /**
     * 构造存储。
     *
     * @param root  报告根目录
     * @param clock 统一时钟
     */
    public ReportFileStorage(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
    }

    /**
     * 写入报告并返回路径信息。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param content    DOCX 字节
     * @param riskCount  风险条数
     * @return 导出结果
     * @throws IOException 写入失败
     */
    public ReportExportResult write(long tenantId, long contractId, byte[] content, int riskCount)
            throws IOException {
        String month = MONTH.format(java.time.LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))));
        Path dir = root.resolve(String.valueOf(tenantId)).resolve(month);
        Files.createDirectories(dir);
        Path file = dir.resolve(contractId + ".docx");
        // TRUNCATE_EXISTING：覆盖写，避免旧内容拼在新内容之后（规范 9.3 的覆盖语义）
        Files.write(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        String relative = "reports/" + tenantId + "/" + month + "/" + contractId + ".docx";
        return new ReportExportResult(relative, file.toString(), riskCount);
    }
}
