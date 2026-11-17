package com.crag.agent.api;

import com.crag.agent.extract.ExtractionField;
import com.crag.agent.extract.ExtractionResult;
import com.crag.agent.review.ReviewOutcome;
import com.crag.agent.review.RiskItem;
import com.crag.agent.review.ReviewStats;
import com.crag.agent.review.ContractReviewOrchestrator;
import com.crag.agent.tool.ToolDescriptor;
import com.crag.agent.tool.ToolRegistry;
import com.crag.common.api.ApiResponse;
import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import com.crag.common.security.PermissionPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 初审预演接口（第 5–7 周验收用；第 8–9 周由审核任务承载并落 audit_task）。
 *
 * <p>调用链：本类 → {@link ContractReviewOrchestrator#review} → 抽取服务 → 工具注册中心（规则/检索/报告）。
 * 本接口不写审核任务，只用于验证规则热更新、抽取回验与报告溯源三项验收条件。</p>
 *
 * <p>租户与操作人只从上下文获取（规范 10.3）。</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "初审预演", description = "规则校验、风控依据检索、模型判断、报告导出")
public class ReviewController {

    private final ContractReviewOrchestrator orchestrator;
    private final ToolRegistry toolRegistry;
    private final PermissionPort permissionPort;

    /**
     * 构造接口。
     *
     * @param orchestrator 编排器
     * @param toolRegistry 工具注册中心
     */
    public ReviewController(ContractReviewOrchestrator orchestrator, ToolRegistry toolRegistry,
                           PermissionPort permissionPort) {
        this.orchestrator = orchestrator;
        this.toolRegistry = toolRegistry;
        this.permissionPort = permissionPort;
    }

    /**
     * 执行初审预演。
     *
     * @param contractId 合同标识
     * @param rulesOnly  是否仅规则模式（等价于熔断期降级，也用于规则热更新的快速验证）
     * @return 预演结果
     */
    @PostMapping("/contracts/{contractId}/review")
    @Operation(summary = "初审预演", description = "规则优先、检索取证、模型判断、报告导出；不写审核任务（第 8–9 周接入）")
    public ApiResponse<ReviewResponse> review(
            @PathVariable long contractId,
            @org.springframework.web.bind.annotation.RequestParam(name = "rulesOnly", defaultValue = "false")
            boolean rulesOnly) {
        long userId = UserContext.require();
        permissionPort.requireStartAudit(userId);
        ReviewOutcome outcome = orchestrator.review(TenantContext.require(), contractId, userId,
                rulesOnly);
        return ApiResponse.ok(ReviewResponse.from(outcome));
    }

    /**
     * 工具清单。
     *
     * @return 工具描述
     */
    @GetMapping("/agent/tools")
    @Operation(summary = "工具清单", description = "注册中心统一注册的三个工具及其超时、幂等属性")
    public ApiResponse<List<ToolDescriptor>> tools() {
        return ApiResponse.ok(toolRegistry.descriptors());
    }

    /**
     * 预演响应。
     *
     * @param contractId 合同标识
     * @param extraction 抽取结果
     * @param risks      风险清单
     * @param reportPath 报告相对路径
     * @param stats      运行统计
     */
    public record ReviewResponse(long contractId, ExtractionView extraction, List<RiskView> risks,
                                 String reportPath, ReviewStats stats) {

        /**
         * 由用例结果转换。
         *
         * @param outcome 预演结果
         * @return 响应
         */
        public static ReviewResponse from(ReviewOutcome outcome) {
            List<RiskView> riskViews = new ArrayList<>(outcome.risks().size());
            for (RiskItem risk : outcome.risks()) {
                riskViews.add(new RiskView(risk.level().name(), risk.level().label(), risk.clauseNo(),
                        risk.clauseType(), risk.title(), risk.content(), risk.suggestion(), risk.sourceDocName(),
                        risk.chunkKey(), risk.excerpt(), risk.source(), risk.ruleCode(), risk.ruleVersion(),
                        risk.needsReview(), risk.citationVerified()));
            }
            return new ReviewResponse(outcome.contractId(), ExtractionView.from(outcome.extraction()),
                    List.copyOf(riskViews), outcome.reportPath(), outcome.stats());
        }
    }

    /**
     * 抽取视图。
     *
     * @param contractAmount    金额
     * @param paymentDays       账期
     * @param jurisdiction      管辖地
     * @param hasJointLiability 连带责任
     * @param status            状态
     * @param verifiedRate      回验通过率
     * @param verifiedCount     通过字段数
     * @param valueCount        有值字段数
     * @param promptVersion     Prompt 版本
     * @param modelName         模型名
     * @param degraded          是否降级
     * @param degradeReason     降级原因
     * @param fields            字段明细
     */
    public record ExtractionView(java.math.BigDecimal contractAmount, Integer paymentDays, String jurisdiction,
                                 Boolean hasJointLiability, String status, double verifiedRate, int verifiedCount,
                                 int valueCount, String promptVersion, String modelName, boolean degraded,
                                 String degradeReason, List<FieldView> fields) {

        /**
         * 由抽取结果转换。
         *
         * @param result 抽取结果
         * @return 视图
         */
        public static ExtractionView from(ExtractionResult result) {
            List<FieldView> fieldViews = new ArrayList<>(result.fields().size());
            for (ExtractionField field : result.fields()) {
                fieldViews.add(new FieldView(field.name(), field.value() == null ? null : String.valueOf(field.value()),
                        field.span(), field.verified(), field.reason()));
            }
            return new ExtractionView(result.contractAmount(), result.paymentDays(), result.jurisdiction(),
                    result.hasJointLiability(), result.status().name(), result.verifiedRate(), result.verifiedCount(),
                    result.valueCount(), result.promptVersion(), result.modelName(), result.degraded(),
                    result.degradeReason(), List.copyOf(fieldViews));
        }
    }

    /**
     * 字段视图。
     *
     * @param name     字段名
     * @param value    值
     * @param span     原文片段
     * @param verified 是否回验通过
     * @param reason   未通过原因
     */
    public record FieldView(String name, String value, String span, boolean verified, String reason) {
    }

    /**
     * 风险视图。
     *
     * @param level            等级 code
     * @param levelLabel       等级中文
     * @param clauseNo         条款序号
     * @param clauseType       条款类型
     * @param title            标题
     * @param content          说明
     * @param suggestion       建议
     * @param sourceDocName    来源文档
     * @param chunkKey         切片定位
     * @param excerpt          引用原文
     * @param source           来源
     * @param ruleCode         规则编码
     * @param ruleVersion      规则版本
     * @param manualRequired   是否需人工复核
     * @param citationVerified 引用是否通过回验
     */
    public record RiskView(String level, String levelLabel, String clauseNo, String clauseType, String title,
                           String content, String suggestion, String sourceDocName, String chunkKey, String excerpt,
                           String source, String ruleCode, Integer ruleVersion, boolean manualRequired,
                           boolean citationVerified) {
    }
}
