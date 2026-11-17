package com.crag.doc.application.port;

import com.crag.doc.domain.model.ContractRecord;
import java.util.List;
import java.util.Optional;

/**
 * 合同仓储端口（表 contract_main 的属主是 doc-module，规范 2.2 单写者原则）。
 */
public interface ContractRepository {

    /**
     * 按租户与标识查询。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 合同；不存在或跨租户返回空
     */
    Optional<ContractRecord> findById(long tenantId, long contractId);

    /**
     * 按租户与文件哈希查询，用于秒传（规范 5.1）。
     *
     * @param tenantId 租户
     * @param fileHash SHA-256
     * @return 合同
     */
    Optional<ContractRecord> findByHash(long tenantId, String fileHash);

    /**
     * 新增合同。
     *
     * @param contract 合同
     * @return 已落库的合同
     */
    ContractRecord insert(ContractRecord contract);

    /**
     * 分页查询租户合同（按创建时间倒序，逻辑删除自动排除）。
     *
     * @param tenantId 租户
     * @param offset   偏移
     * @param limit    条数
     * @return 合同列表
     */
    List<ContractRecord> findPage(long tenantId, int offset, int limit);

    /**
     * 统计租户合同总数。
     *
     * @param tenantId 租户
     * @return 总数
     */
    long countByTenant(long tenantId);

    /**
     * 逻辑删除合同；调用方负责存在性与权限校验。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     */
    void markDeleted(long tenantId, long contractId);

    /**
     * 更新页数。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param pageCount  页数
     */
    void updatePageCount(long tenantId, long contractId, int pageCount);
}
