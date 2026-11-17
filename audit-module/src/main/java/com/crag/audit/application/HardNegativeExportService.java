package com.crag.audit.application;

import com.crag.audit.application.port.AuditRepository;
import com.crag.audit.domain.model.ReviewActionEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * hard negatives 导出（规范 9.4）。
 *
 * <p>字段固定为：条款文本、召回片段、期望结论为空、原因码、来源标记。
 * 只导出，不训练、不自动调参（规范 9.4 明令禁止在线学习）。</p>
 *
 * <p>输出 JSONL（每行一个样本），按租户与日期落盘，路径可由 {@code crag.audit.hard-negative-root} 配置。
 * 该文件是下一轮检索回归集的输入，不是业务数据表，因此不做逻辑删除。</p>
 */
@Service
public class HardNegativeExportService {

    private static final Logger log = LoggerFactory.getLogger(HardNegativeExportService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SOURCE_MARKER = "REVIEW_REJECT";

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path root;

    /**
     * 构造服务。
     *
     * @param repository 审核仓储
     * @param objectMapper JSON 序列化
     * @param clock      统一时钟
     * @param root       导出根目录
     */
    public HardNegativeExportService(AuditRepository repository, ObjectMapper objectMapper, Clock clock,
                                     @Value("${crag.audit.hard-negative-root:./data/hard-negatives}") String root) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /**
     * 导出指定时间之后的驳回样本。
     *
     * @param tenantId 租户
     * @param since    起始时间
     * @return 导出结果
     */
    public ExportResult export(long tenantId, Instant since) {
        List<AuditRepository.RejectedSample> samples = repository.findRejectedSamples(tenantId, since);
        StringBuilder jsonl = new StringBuilder();
        for (AuditRepository.RejectedSample sample : samples) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("clauseText", sample.risk().clauseText());
            node.set("retrievedChunks", parseChunks(sample.risk().retrievalSnapshot()));
            // 期望结论为空：hard negative 的语义就是“不应命中风险”
            node.putNull("expectedConclusion");
            node.put("reasonCode", sample.reason());
            node.put("sourceMarker", SOURCE_MARKER);
            node.put("reviewedAt", sample.reviewedAt() == null ? null : sample.reviewedAt().toString());
            jsonl.append(node.toString()).append('\n');
        }
        String day = DAY.format(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))));
        Path file = root.resolve(String.valueOf(tenantId)).resolve("hard-negatives-" + day + ".jsonl");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, jsonl.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("hard negative export failed", e);
        }
        log.info("hard negatives 导出完成 tenantId={} samples={} file={}", tenantId, samples.size(), file);
        return new ExportResult(file.toString(), samples.size());
    }

    private ArrayNode parseChunks(String snapshot) {
        ArrayNode array = objectMapper.createArrayNode();
        if (snapshot == null || snapshot.isBlank()) {
            return array;
        }
        try {
            JsonNode parsed = objectMapper.readTree(snapshot);
            if (parsed.isArray()) {
                for (JsonNode node : parsed) {
                    array.add(node);
                }
            }
        } catch (IOException e) {
            // 快照损坏不应阻断回流：保留空数组并记录，样本仍可按条款文本使用
            log.warn("召回快照解析失败，按空片段导出");
        }
        return array;
    }

    /**
     * 导出结果。
     *
     * @param filePath 文件绝对路径
     * @param total    样本总数
     */
    public record ExportResult(String filePath, long total) {
    }
}
