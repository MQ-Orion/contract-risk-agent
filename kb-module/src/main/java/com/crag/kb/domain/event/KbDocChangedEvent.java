package com.crag.kb.domain.event;

/**
 * 知识库文档变更事件（规范 5.6）：新建上架、正文更新、版本上架时发布，由同步监听器消费。
 *
 * <p>与 {@link KbIndexChangedEvent} 必须区分：后者表示“索引已经写完”，只用于失效检索缓存；
 * 若两者混用，同步完成发布索引事件会再次触发同步，形成自触发循环。</p>
 *
 * @param tenantId 租户
 * @param kbDocId  文档标识
 * @param reason   变更原因
 */
public record KbDocChangedEvent(long tenantId, long kbDocId, String reason) {
}
