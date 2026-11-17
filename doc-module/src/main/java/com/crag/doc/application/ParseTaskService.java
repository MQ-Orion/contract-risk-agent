package com.crag.doc.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.ParseTaskDispatcher;
import com.crag.doc.application.port.ParseTaskRepository;
import com.crag.doc.domain.DocConstants;
import com.crag.doc.domain.model.ParseStatusEnum;
import com.crag.doc.domain.model.ParseTaskRecord;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析任务查询与人工重试用例（规范 5.5：失败必须可在管理端重试，禁止静默失败）。
 */
@Service
public class ParseTaskService {

    private final ParseTaskRepository taskRepository;
    private final ParseTaskDispatcher dispatcher;
    private final Clock clock;

    /**
     * 构造服务。
     *
     * @param taskRepository 任务仓储
     * @param dispatcher     解析任务调度端口
     * @param clock          统一时钟
     */
    public ParseTaskService(ParseTaskRepository taskRepository, ParseTaskDispatcher dispatcher, Clock clock) {
        this.taskRepository = taskRepository;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /**
     * 查询合同最近一次解析任务。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 任务
     */
    public ParseTaskRecord latest(long tenantId, long contractId) {
        return taskRepository.findLatestByContractId(tenantId, contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "parse task of " + contractId));
    }

    /**
     * 人工重试：仅允许失败态，且总尝试次数不超过三次。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 重新排队后的任务
     */
    @Transactional
    public ParseTaskRecord retry(long tenantId, long contractId) {
        ParseTaskRecord task = latest(tenantId, contractId);
        if (task.status() != ParseStatusEnum.FAILED) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL, "status=" + task.status());
        }
        if (task.retryCount() >= DocConstants.MAX_PARSE_RETRY) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL, "retry exhausted=" + task.retryCount());
        }
        ParseTaskRecord pending = task.retry(clock.instant());
        taskRepository.update(pending);
        dispatcher.submit(tenantId, pending.id());
        return pending;
    }
}
