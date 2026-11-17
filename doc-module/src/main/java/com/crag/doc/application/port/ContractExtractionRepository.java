package com.crag.doc.application.port;

import com.crag.doc.domain.model.ContractExtractionRecord;
import java.util.Optional;

/**
 * 合同抽取结果仓储端口（表 contract_extract_info，归属 doc-module）。
 *
 * <p>agent 模块通过本端口写入抽取结果，不接触 doc 的 Mapper（规范 2.2）。</p>
 */
public interface ContractExtractionRepository {

    /**
     * 保存或覆盖某合同的抽取结果（同一合同只保留一份，规范 9.3 的“不存第二份事实”）。
     *
     * @param record 抽取记录
     * @return 保存后的记录
     */
    ContractExtractionRecord saveOrUpdate(ContractExtractionRecord record);

    /**
     * 查询某合同的抽取结果。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 记录
     */
    Optional<ContractExtractionRecord> findByContract(long tenantId, long contractId);
}
