package com.crag.retrieval.infrastructure.listener;

import com.crag.kb.domain.event.KbIndexChangedEvent;
import com.crag.retrieval.application.port.RetrievalCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 知识库索引变更监听：按租户失效检索缓存（规范 6.6）。
 *
 * <p>通过事件解耦，retrieval 不需要反向依赖 kb 的写模型。</p>
 */
@Component
public class KbIndexChangedListener {

    private static final Logger log = LoggerFactory.getLogger(KbIndexChangedListener.class);

    private final RetrievalCachePort cachePort;

    /**
     * 构造监听器。
     *
     * @param cachePort 检索缓存
     */
    public KbIndexChangedListener(RetrievalCachePort cachePort) {
        this.cachePort = cachePort;
    }

    /**
     * 处理索引变更。
     *
     * @param event 事件
     */
    @EventListener
    public void onIndexChanged(KbIndexChangedEvent event) {
        cachePort.invalidateTenant(event.tenantId());
        log.info("索引变更触发缓存失效 tenantId={} docId={} reason={}",
                event.tenantId(), event.kbDocId(), event.reason());
    }
}
