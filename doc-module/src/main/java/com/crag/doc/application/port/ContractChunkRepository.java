package com.crag.doc.application.port;

import com.crag.doc.domain.model.ContractChunkRecord;
import java.util.List;

/**
 * 合同切片仓储端口。
 *
 * <p>合同切片不含 embedding，不参与向量召回（规范 4.2），因此重新解析时整篇替换是安全的；
 * 知识库切片的增量规则见规范 4.3，与本端口无关。</p>
 */
public interface ContractChunkRepository {

    /**
     * 覆盖写入某合同的全部切片。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param chunks     切片
     */
    void replaceAll(long tenantId, long contractId, List<ContractChunkRecord> chunks);

    /**
     * 查询某合同的切片，按全文索引升序。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 切片列表
     */
    List<ContractChunkRecord> findByContractId(long tenantId, long contractId);

    /**
     * 逻辑删除某合同全部切片（合同删除时同步隐藏，规范 4.3）。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     */
    void deleteByContractId(long tenantId, long contractId);
}
