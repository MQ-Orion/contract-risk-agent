package com.crag.kb.application;

import com.crag.common.domain.TenantContext;
import com.crag.kb.domain.event.KbDocChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档变更事件监听：事务提交后异步执行增量同步（规范 5.6 事件驱动）。
 *
 * <p>异步线程没有请求上下文，这里显式设置并在结束后清理租户上下文，保证 Mapper 层租户过滤生效（规范 4.4）。</p>
 *
 * <p>为什么用 AFTER_COMMIT 而不是直接调用：同步会调用嵌入接口（秒级），放进事务会长期占用连接并放大锁范围（规范 3.8）。
 * 为什么只监听 {@link KbDocChangedEvent} 而不监听 {@code KbIndexChangedEvent}：后者由同步自身在成功后发布，
 * 若也被本监听器消费会形成“同步→发布→再同步”的自触发循环（已发生过的缺陷，见提交 9f4dea6）。
 * 事件分离后，索引变更事件只用于失效检索缓存。</p>
 *
 * <p>失败语义：异步异常只记录不重抛，文档停在 FAILED 或遗留 PENDING，由每日对账任务补做（规范 5.6、3.9）。</p>
 */
@Component
public class KbSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(KbSyncEventListener.class);

    private final KbIndexSyncService syncService;

    /**
     * 构造监听器。
     *
     * @param syncService 同步服务
     */
    public KbSyncEventListener(KbIndexSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * 处理索引变更。
     *
     * @param event 事件
     */
    @Async("kbSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDocChanged(KbDocChangedEvent event) {
        TenantContext.set(event.tenantId());
        try {
            syncService.sync(event.tenantId(), event.kbDocId());
        } catch (RuntimeException e) {
            log.error("异步增量同步失败 docId={} reason={}", event.kbDocId(), event.reason(), e);
        } finally {
            TenantContext.clear();
        }
    }
}
