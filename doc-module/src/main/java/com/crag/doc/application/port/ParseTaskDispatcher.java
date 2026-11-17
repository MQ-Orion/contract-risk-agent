package com.crag.doc.application.port;

/**
 * 解析任务调度端口：把任务提交到解析线程池（规范 3.9：禁止裸建线程）。
 *
 * <p>提交方必须传入租户：异步线程没有请求上下文，调度实现要在执行前设置租户上下文，
 * 否则 Mapper 层的租户拦截器会拒绝查询（规范 4.4）。</p>
 */
public interface ParseTaskDispatcher {

    /**
     * 提交解析任务。
     *
     * @param tenantId 租户
     * @param taskId   任务标识
     */
    void submit(long tenantId, long taskId);
}
