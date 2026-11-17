package com.crag.doc.application.port;

import com.crag.doc.domain.model.ParseTaskRecord;
import java.util.Optional;

/**
 * 解析任务仓储端口（规范 5.5：状态必须落库）。
 */
public interface ParseTaskRepository {

    /**
     * 按标识查询。
     *
     * @param taskId 任务标识
     * @return 任务
     */
    Optional<ParseTaskRecord> findById(long taskId);

    /**
     * 查询合同最近一次解析任务。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 任务
     */
    Optional<ParseTaskRecord> findLatestByContractId(long tenantId, long contractId);

    /**
     * 新建任务。
     *
     * @param task 任务
     * @return 已落库任务
     */
    ParseTaskRecord insert(ParseTaskRecord task);

    /**
     * 更新任务状态。
     *
     * @param task 任务
     */
    void update(ParseTaskRecord task);
}
