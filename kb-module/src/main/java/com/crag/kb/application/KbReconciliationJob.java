package com.crag.kb.application;

import com.crag.common.domain.TenantContext;
import com.crag.kb.application.port.KbDocRepository;
import com.crag.kb.domain.model.KbDocRecord;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日轻量对账（规范 5.6、3.9）。
 *
 * <p>只读 {@code kb_doc} 已落库字段：源哈希与 {@code synced_file_hash} 不一致、或从未同步过的在架文档，
 * 判定为存在未同步变更并触发增量同步；<b>不重新解析源文件</b>，避免每日任务退化为全量解析。
 * 全量重解析属低频重量对账，另行手动触发，不纳入每日任务。</p>
 *
 * <p>多实例互斥由 ShedLock + Redis 提供，锁名与任务用途一一对应。</p>
 */
@Component
public class KbReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(KbReconciliationJob.class);

    private final KbDocRepository docRepository;
    private final KbIndexSyncService syncService;

    /**
     * 构造任务。
     *
     * @param docRepository 文档仓储
     * @param syncService   增量同步服务
     */
    public KbReconciliationJob(KbDocRepository docRepository, KbIndexSyncService syncService) {
        this.docRepository = docRepository;
        this.syncService = syncService;
    }

    /**
     * 每日对账（默认凌晨 2:30）。
     */
    @Scheduled(cron = "${crag.kb.reconcile-cron:0 30 2 * * *}")
    @SchedulerLock(name = "kbDailyReconcile", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void scheduledReconcile() {
        ReconcileReport report = reconcileAllTenants();
        log.info("每日对账完成 tenants={} pending={} synced={} failed={}",
                report.tenants(), report.pendingDocs(), report.synced(), report.failed());
    }

    /**
     * 对全部租户执行一次对账，供定时任务与测试复用。
     *
     * <p>逐租户设置上下文再查：租户拦截器从 ThreadLocal 取值，不设置会直接抛未认证；
     * 每个租户单独 try/finally 清理，避免一个租户的异常把租户标识带到下一个租户（规范 4.4）。</p>
     *
     * @return 对账结果
     */
    public ReconcileReport reconcileAllTenants() {
        List<Long> tenants = docRepository.findActiveTenants();
        int pending = 0;
        int synced = 0;
        int failed = 0;
        for (Long tenantId : tenants) {
            TenantContext.set(tenantId);
            try {
                for (KbDocRecord doc : docRepository.findPendingSync(tenantId)) {
                    pending++;
                    try {
                        syncService.sync(tenantId, doc.id());
                        synced++;
                    } catch (RuntimeException e) {
                        failed++;
                        log.warn("对账同步失败 tenantId={} docId={}", tenantId, doc.id(), e);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
        return new ReconcileReport(tenants.size(), pending, synced, failed);
    }

    /**
     * 对账结果。
     *
     * @param tenants     扫描租户数
     * @param pendingDocs 待同步文档数
     * @param synced      同步成功数
     * @param failed      同步失败数
     */
    public record ReconcileReport(int tenants, int pendingDocs, int synced, int failed) {
    }
}
