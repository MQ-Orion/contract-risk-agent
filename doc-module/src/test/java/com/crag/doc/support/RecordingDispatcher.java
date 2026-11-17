package com.crag.doc.support;

import com.crag.doc.application.port.ParseTaskDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 解析任务调度测试替身：记录提交并可按需同步执行。
 */
public class RecordingDispatcher implements ParseTaskDispatcher {

    private final List<Long> submitted = new ArrayList<>();
    private Consumer<Long> handler;

    /**
     * 设置提交后的处理逻辑。
     *
     * @param handler 处理逻辑
     */
    public void onSubmit(Consumer<Long> handler) {
        this.handler = handler;
    }

    @Override
    public void submit(long tenantId, long taskId) {
        submitted.add(taskId);
        if (handler != null) {
            handler.accept(taskId);
        }
    }

    /**
     * 已提交的任务。
     *
     * @return 任务标识列表
     */
    public List<Long> submitted() {
        return List.copyOf(submitted);
    }
}
