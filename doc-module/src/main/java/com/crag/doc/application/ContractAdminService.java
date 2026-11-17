package com.crag.doc.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.domain.model.ContractRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同管理用例：删除合同（任意审核状态均可删，逻辑删除）。
 *
 * <p>调用链：{@code ContractOverviewController.delete} → 本类 → 合同仓储逻辑删除 + 切片仓储隐藏。
 * 只删“本期可见性”，不动物理文件与审核历史：审核任务仍有合同标识，报告与复核日志可追溯。</p>
 *
 * <p>切片同步逻辑删除：合同隐藏后若切片仍可被检索，会出现“合同已删但结论还能命中”的脏数据；
 * 切片是派生数据，随合同一起隐藏（规范 4.3）。</p>
 */
@Service
public class ContractAdminService {

    private static final Logger log = LoggerFactory.getLogger(ContractAdminService.class);

    private final ContractRepository contractRepository;
    private final ContractChunkRepository chunkRepository;

    /**
     * 构造服务。
     *
     * @param contractRepository 合同仓储
     * @param chunkRepository    切片仓储
     */
    public ContractAdminService(ContractRepository contractRepository, ContractChunkRepository chunkRepository) {
        this.contractRepository = contractRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * 删除合同。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     */
    @Transactional
    public void delete(long tenantId, long contractId) {
        ContractRecord contract = contractRepository.findById(tenantId, contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "contract " + contractId));
        chunkRepository.deleteByContractId(tenantId, contractId);
        contractRepository.markDeleted(tenantId, contractId);
        log.info("合同删除 tenantId={} contractId={} uploaderId={}", tenantId, contractId, contract.uploaderId());
    }
}
