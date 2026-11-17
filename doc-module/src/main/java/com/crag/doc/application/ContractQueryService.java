package com.crag.doc.application;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.doc.domain.model.ContractRecord;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 合同查询用例：详情与切片列表。
 *
 * <p>调用链：{@code ContractController.detail/clauses} → 本类 → {@code ContractRepository}/{@code ContractChunkRepository}。
 * 只读，无事务；租户条件由 Mapper 层拦截器强制注入（规范 4.4）。</p>
 */
@Service
public class ContractQueryService {

    private final ContractRepository contractRepository;
    private final ContractChunkRepository chunkRepository;

    /**
     * 构造服务。
     *
     * @param contractRepository 合同仓储
     * @param chunkRepository    切片仓储
     */
    public ContractQueryService(ContractRepository contractRepository, ContractChunkRepository chunkRepository) {
        this.contractRepository = contractRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * 查询合同详情；跨租户按不存在处理（规范 4.4）。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 合同
     */
    public ContractRecord detail(long tenantId, long contractId) {
        return contractRepository.findById(tenantId, contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "contract " + contractId));
    }

    /**
     * 查询合同切片。
     *
     * <p>先查详情再查切片：跨租户或已删除的合同在详情处就返回 10004，切片列表不会出现“空数组但合同不存在”
     * 这种可被枚举的差异（规范 4.4：资源不存在与无权访问必须同码）。</p>
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @return 切片列表
     */
    public List<ContractChunkRecord> clauses(long tenantId, long contractId) {
        detail(tenantId, contractId);
        return chunkRepository.findByContractId(tenantId, contractId);
    }

    /**
     * 分页查询租户合同（合同管理列表）。
     *
     * <p>只按租户过滤，不跨租户；分页参数在服务层收敛，避免接口层拼 SQL（规范 3.3）。</p>
     *
     * @param tenantId 租户
     * @param page     页码，从 1 开始
     * @param size     每页条数，1–100
     * @return 当前页合同
     */
    public List<ContractRecord> list(long tenantId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return contractRepository.findPage(tenantId, (safePage - 1) * safeSize, safeSize);
    }

    /**
     * 统计租户合同总数。
     *
     * @param tenantId 租户
     * @return 总数
     */
    public long count(long tenantId) {
        return contractRepository.countByTenant(tenantId);
    }
}
