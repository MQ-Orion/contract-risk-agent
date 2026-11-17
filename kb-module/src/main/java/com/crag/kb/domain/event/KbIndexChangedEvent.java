package com.crag.kb.domain.event;

/**
 * 知识库索引变更事件（规范 5.6、6.6）。
 *
 * <p>kb 模块在文档上架/下架/版本切换/切片变化后发布本事件；retrieval 模块监听并按租户失效检索缓存。
 * 事件是模块间的唯一耦合方式，避免 kb 依赖 retrieval（规范 2.2）。</p>
 *
 * @param tenantId 租户
 * @param kbDocId  文档标识
 * @param reason   变更原因，用于日志
 */
public record KbIndexChangedEvent(long tenantId, long kbDocId, String reason) {
}
