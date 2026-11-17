package com.crag.agent.audit;

import com.crag.agent.review.ContractReviewOrchestrator;
import com.crag.agent.review.ReviewOutcome;
import com.crag.agent.review.RiskItem;
import com.crag.audit.application.AuditTaskService;
import com.crag.audit.application.port.AuditRiskInput;
import com.crag.audit.domain.model.AuditTaskRecord;
import com.crag.audit.domain.model.AuditTaskStatusEnum;
import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.domain.model.ContractRecord;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 审核发起与异步执行（规范 9.1、3.9、10.7）。
 *
 * <p>调用链：{@code AuditRunController.start} → 本类：创建/复用任务（幂等）→ 置审核中 → 提交审核线程池
 * → {@link ContractReviewOrchestrator#review} → 回写风险清单（任务进入待复核）。接口立即返回任务标识，
 * 客户端凭任务标识订阅 SSE 进度（规范 10.6）。</p>
 *
 * <p>为什么先落状态再提交执行：线程池可能在提交瞬间拒绝任务，若先提交后写状态，任务会永远停在“已创建”；
 * 先写 AUDITING 再提交，失败时还能置 FAILED 让用户看到（规范 3.9 异步任务必须先落库状态）。</p>
 *
 * <p>线程池拒绝策略为调用者执行：队列满时在请求线程内同步执行，不会静默丢任务。</p>
 */
@Service
public class AuditRunService {

    private static final Logger log = LoggerFactory.getLogger(AuditRunService.class);

    private final AuditTaskService auditTaskService;
    private final ContractReviewOrchestrator orchestrator;
    private final ContractRepository contractRepository;
    private final ThreadPoolTaskExecutor auditRunExecutor;

    /**
     * 构造服务。
     *
     * @param auditTaskService 审核任务用例
     * @param orchestrator     初审编排
     * @param contractRepository 合同只读端口
     * @param auditRunExecutor 审核线程池
     */
    public AuditRunService(AuditTaskService auditTaskService, ContractReviewOrchestrator orchestrator,
                           ContractRepository contractRepository,
                           @Qualifier("auditRunExecutor") ThreadPoolTaskExecutor auditRunExecutor) {
        this.auditTaskService = auditTaskService;
        this.orchestrator = orchestrator;
        this.contractRepository = contractRepository;
        this.auditRunExecutor = auditRunExecutor;
    }

    /**
     * 发起审核（幂等）。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param operatorId 操作人
     * @param rulesOnly  是否仅规则模式
     * @return 任务标识
     */
    public long start(long tenantId, long contractId, long operatorId, boolean rulesOnly) {
        ContractRecord contract = contractRepository.findById(tenantId, contractId)
                .orElseThrow(() -> BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "contract " + contractId));
        long taskId = auditTaskService.createOrGet(tenantId, contractId, contract.versionNo(), operatorId);
        AuditTaskRecord task = auditTaskService.get(tenantId, taskId);
        if (task.status() == AuditTaskStatusEnum.PENDING_REVIEW || task.status() == AuditTaskStatusEnum.COMPLETED) {
            // 已出结论的任务不重跑：重跑会覆盖人工复核结果（规范 9.1）
            return taskId;
        }
        if (task.status() == AuditTaskStatusEnum.CREATED || task.status() == AuditTaskStatusEnum.AUDITING) {
            if (task.status() == AuditTaskStatusEnum.CREATED) {
                auditTaskService.markAuditing(tenantId, taskId, operatorId);
            }
            auditRunExecutor.execute(() -> run(tenantId, taskId, contractId, operatorId, rulesOnly));
        }
        return taskId;
    }

    private void run(long tenantId, long taskId, long contractId, long operatorId, boolean rulesOnly) {
        TenantContext.set(tenantId);
        try {
            ReviewOutcome outcome = orchestrator.review(tenantId, contractId, operatorId, rulesOnly);
            List<AuditRiskInput> risks = new ArrayList<>(outcome.risks().size());
            for (RiskItem risk : outcome.risks()) {
                risks.add(new AuditRiskInput(risk.level(), risk.content(), risk.suggestion(), risk.source(),
                        risk.ruleCode(), risk.ruleVersion(), risk.sourceDocName(), risk.chunkKey(), risk.excerpt(),
                        risk.citationVerified(), risk.clauseNo(), risk.clauseType(), risk.clauseText(),
                        risk.retrievalSnapshot()));
            }
            auditTaskService.submitResult(tenantId, taskId, risks, outcome.reportPath(),
                    outcome.stats().modelVersion(), outcome.stats().promptVersion(), operatorId);
            log.info("审核执行完成 taskId={} risks={} report={}", taskId, risks.size(), outcome.reportPath());
        } catch (RuntimeException e) {
            log.error("审核执行失败 taskId={}", taskId, e);
            auditTaskService.fail(tenantId, taskId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
