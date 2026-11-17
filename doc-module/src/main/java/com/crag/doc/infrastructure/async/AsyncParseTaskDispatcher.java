package com.crag.doc.infrastructure.async;

import com.crag.common.domain.TenantContext;
import com.crag.doc.application.ParsePipelineService;
import com.crag.doc.application.port.ParseTaskDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 解析任务异步调度：提交到解析专用线程池（规范 3.9）。
 *
 * <p>拒绝策略为调用者执行，队列满时在提交线程内同步执行；执行前设置租户上下文并在结束后清理，
 * 保证异步线程里的 SQL 仍受租户拦截器约束（规范 4.4）。</p>
 *
 * <p>时序：调用方在事务提交前调用本方法也能安全工作，因为解析任务的状态流转全部在自己取锁后重读任务，
 * 不依赖提交前快照；代价是极小概率读到 PENDING 前的旧行，此时 {@code execute} 会直接返回。</p>
 *
 * <p>为什么先 set 再执行：ThreadLocal 不跨线程传递（见 {@code TenantContext}），不设置会让租户拦截器在
 * 异步线程里抛未认证；为什么用 try/finally 清理：线程池线程会被复用，残留上下文等于把租户 A 的数据写进租户 B。</p>
 */
public class AsyncParseTaskDispatcher implements ParseTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AsyncParseTaskDispatcher.class);

    private final ThreadPoolTaskExecutor executor;
    private final ParsePipelineService pipelineService;

    /**
     * 构造调度器。
     *
     * @param executor        解析线程池
     * @param pipelineService 解析流水线
     */
    public AsyncParseTaskDispatcher(ThreadPoolTaskExecutor executor, ParsePipelineService pipelineService) {
        this.executor = executor;
        this.pipelineService = pipelineService;
    }

    @Override
    public void submit(long tenantId, long taskId) {
        log.info("提交解析任务 taskId={} tenantId={}", taskId, tenantId);
        executor.execute(() -> {
            TenantContext.set(tenantId);
            try {
                pipelineService.execute(taskId);
            } catch (RuntimeException e) {
                log.error("解析任务执行异常 taskId={}", taskId, e);
            } finally {
                TenantContext.clear();
            }
        });
    }
}
