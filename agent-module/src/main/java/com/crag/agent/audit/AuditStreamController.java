package com.crag.agent.audit;

import com.crag.audit.application.AuditTaskService;
import com.crag.audit.domain.model.AuditRiskItemRecord;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 审核进度推送（规范 10.6）。
 *
 * <p>事件类型固定为：{@code progress}（进度）、{@code risk}（风险）、{@code complete}（完成）、
 * {@code error}（错误）；每 15 秒发送一次心跳注释。断线重连只按任务标识续订本端点，不会创建新任务
 * （端点只读，不写任何业务状态）。</p>
 *
 * <p>进度口径：按任务状态映射为粗粒度百分比，不伪造“已完成 37% 条款”这类无法验证的细粒度进度。
 * 风险事件在任务进入待复核时发送一次，携带风险条数与等级分布。</p>
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditStreamController {

    private static final Logger log = LoggerFactory.getLogger(AuditStreamController.class);

    /** 单连接最长 20 分钟，与规范 7.2 的整体超时一致。 */
    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(20).toMillis();
    /** 轮询间隔。 */
    private static final long POLL_MILLIS = 1_000L;
    /** 心跳间隔（轮询次数）。 */
    private static final int HEARTBEAT_TICKS = 15;

    private final AuditTaskService auditTaskService;
    private final ThreadPoolTaskExecutor sseExecutor;

    /**
     * 构造接口。
     *
     * @param auditTaskService 审核任务用例
     * @param sseExecutor      SSE 线程池
     */
    public AuditStreamController(AuditTaskService auditTaskService,
                                 @Qualifier("sseExecutor") ThreadPoolTaskExecutor sseExecutor) {
        this.auditTaskService = auditTaskService;
        this.sseExecutor = sseExecutor;
    }

    /**
     * 订阅任务进度。
     *
     * @param taskId 任务标识
     * @return SSE 连接
     */
    @GetMapping(value = "/tasks/{taskId}/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@PathVariable long taskId) {
        long tenantId = TenantContext.require();
        // 先校验任务存在，避免为不存在的任务占用连接
        auditTaskService.get(tenantId, taskId);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        sseExecutor.execute(() -> streamLoop(tenantId, taskId, emitter, closed));
        return emitter;
    }

    private void streamLoop(long tenantId, long taskId, SseEmitter emitter, AtomicBoolean closed) {
        // SSE 线程没有请求上下文：必须显式设置租户，否则仓储层会因上下文缺失拒绝查询（规范 4.4）
        TenantContext.set(tenantId);
        Instant deadline = Instant.now().plusMillis(TIMEOUT_MILLIS);
        AuditTaskStatusEnum lastStatus = null;
        int lastPercent = -1;
        int ticks = 0;
        try {
            while (!closed.get() && Instant.now().isBefore(deadline)) {
                AuditTaskRecord task = auditTaskService.get(tenantId, taskId);
                int currentPercent = percent(task.status());
                // 只在状态或百分比变化时推送 progress：每秒重复同一进度会淹没前端日志，
                // 保活交给下方每 15 秒的 heartbeat 注释事件（规范 10.6）
                if (task.status() != lastStatus || currentPercent != lastPercent) {
                    send(emitter, "progress", new ProgressEvent(task.status().name(), currentPercent));
                }
                // 每次进入待复核都推一次汇总：驳回重审会产生新一轮清单，不能只推首轮
                if (task.status() == AuditTaskStatusEnum.PENDING_REVIEW
                        && lastStatus != AuditTaskStatusEnum.PENDING_REVIEW) {
                    List<AuditRiskItemRecord> risks = auditTaskService.risks(tenantId, taskId);
                    send(emitter, "risk", new RiskEvent(risks.size(), countHigh(risks), countMedium(risks)));
                }
                lastStatus = task.status();
                lastPercent = currentPercent;
                if (task.status() == AuditTaskStatusEnum.COMPLETED) {
                    send(emitter, "complete", new CompleteEvent(task.status().name(), task.reportUrl()));
                    emitter.complete();
                    return;
                }
                if (task.status() == AuditTaskStatusEnum.FAILED || task.status() == AuditTaskStatusEnum.CANCELLED) {
                    send(emitter, "error", new ErrorEvent(task.status().name(), "审核未完成"));
                    emitter.complete();
                    return;
                }
                if (++ticks % HEARTBEAT_TICKS == 0) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                }
                Thread.sleep(POLL_MILLIS);
            }
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // 客户端断线：只结束连接，不改变任务状态（重连按任务标识续订）
            log.debug("SSE 连接关闭 taskId={} reason={}", taskId, e.getMessage());
            emitter.completeWithError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException e) {
            log.warn("SSE 推送失败 taskId={}", taskId, e);
            try {
                send(emitter, "error", new ErrorEvent("ERROR", "进度推送异常"));
            } catch (IOException ignored) {
                // 连接已断开，无需再次上报
            }
            emitter.complete();
        } finally {
            TenantContext.clear();
        }
    }

    private static void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private static int percent(AuditTaskStatusEnum status) {
        return switch (status) {
            case CREATED -> 5;
            case PARSING -> 15;
            case AUDITING -> 40;
            case PENDING_REVIEW -> 90;
            case COMPLETED -> 100;
            case FAILED, CANCELLED -> 100;
        };
    }

    private static long countHigh(List<AuditRiskItemRecord> risks) {
        return risks.stream().filter(risk -> risk.riskLevel() == com.crag.common.domain.RiskLevelEnum.HIGH).count();
    }

    private static long countMedium(List<AuditRiskItemRecord> risks) {
        return risks.stream().filter(risk -> risk.riskLevel() == com.crag.common.domain.RiskLevelEnum.MEDIUM).count();
    }

    /**
     * 进度事件。
     *
     * @param status  任务状态
     * @param percent 百分比
     */
    public record ProgressEvent(String status, int percent) {
    }

    /**
     * 风险事件。
     *
     * @param total  风险总数
     * @param high   高风险数
     * @param medium 中风险数
     */
    public record RiskEvent(int total, long high, long medium) {
    }

    /**
     * 完成事件。
     *
     * @param status    状态
     * @param reportUrl 报告位置
     */
    public record CompleteEvent(String status, String reportUrl) {
    }

    /**
     * 错误事件。
     *
     * @param status 状态
     * @param reason 原因
     */
    public record ErrorEvent(String status, String reason) {
    }
}
