package com.crag.agent.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.common.domain.TenantContext;
import com.crag.doc.application.ContractQueryService;
import com.crag.doc.application.ContractUploadService;
import com.crag.doc.application.UploadResult;
import com.crag.kb.application.KbDocService;
import com.crag.kb.application.KbQueryService;
import com.crag.kb.domain.model.KbDocRecord;
import com.crag.kb.domain.model.KbDocTypeEnum;
import com.crag.retrieval.application.RetrievalService;
import com.crag.retrieval.domain.model.RetrievalResult;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 租户隔离集成测试（规范 13.1、11.1）。
 *
 * <p>覆盖：跨租户读取知识库文档、跨租户检索、跨租户读取合同、上下文缺失时拒绝访问。
 * 需要真实 PostgreSQL/Redis，按 {@code -Dcrag.it=true} 启用。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "crag.it", matches = "true")
class TenantIsolationIT {

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final long USER = 1L;

    @Autowired
    private KbDocService kbDocService;
    @Autowired
    private KbQueryService kbQueryService;
    @Autowired
    private RetrievalService retrievalService;
    @Autowired
    private ContractQueryService contractQueryService;
    @Autowired
    private ContractUploadService contractUploadService;

    @Test
    @DisplayName("跨租户读取知识库文档按不存在处理")
    void crossTenantKbDocIsHidden() {
        KbDocRecord doc = createKbDoc(TENANT_A, "隔离测试制度-" + System.nanoTime());

        TenantContext.set(TENANT_B);
        try {
            assertThatThrownBy(() -> kbQueryService.detail(TENANT_B, doc.id()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).errorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(kbQueryService.list(TENANT_B, null)).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("跨租户检索返回空结果，不泄漏他人知识库")
    void crossTenantRetrievalIsEmpty() {
        createKbDoc(TENANT_A, "采购隔离制度-" + System.nanoTime());

        TenantContext.set(TENANT_B);
        RetrievalResult result;
        try {
            result = retrievalService.retrieve("预付款比例上限是多少", "PAYMENT", TENANT_B);
        } finally {
            TenantContext.clear();
        }
        assertThat(result.chunks()).isEmpty();
    }

    @Test
    @DisplayName("越权检索：入参租户与上下文不一致按不存在处理")
    void retrievalWithForeignTenantIsRejected() {
        TenantContext.set(TENANT_A);
        try {
            assertThatThrownBy(() -> retrievalService.retrieve("预付款比例", "PAYMENT", TENANT_B))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).errorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("跨租户读取合同按不存在处理")
    void crossTenantContractIsHidden() throws Exception {
        Path sample = Path.of(System.getProperty("user.dir")).getParent()
                .resolve("eval/samples/purchase_contract.docx");
        assertThat(Files.exists(sample)).isTrue();
        long contractId;
        long taskId;
        TenantContext.set(TENANT_A);
        try (InputStream in = Files.newInputStream(sample)) {
            UploadResult upload = contractUploadService.upload(TENANT_A, USER, "purchase_contract.docx",
                    Files.size(sample), in);
            contractId = upload.contractId();
            taskId = upload.taskId();
        } finally {
            TenantContext.clear();
        }
        assertThat(contractId).isPositive();
        assertThat(taskId).isPositive();

        TenantContext.set(TENANT_B);
        try {
            assertThatThrownBy(() -> contractQueryService.detail(TENANT_B, contractId))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).errorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("租户上下文缺失时拒绝访问，禁止回退默认租户")
    void missingTenantContextIsRejected() {
        TenantContext.clear();
        // MyBatis 会把拦截器抛出的业务异常包装为持久层异常，这里断言根因
        assertThatThrownBy(() -> kbQueryService.list(TENANT_A, null))
                .hasRootCauseInstanceOf(BizException.class)
                .rootCause()
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    private KbDocRecord createKbDoc(long tenantId, String title) {
        TenantContext.set(tenantId);
        try {
            return kbDocService.create(tenantId, USER, title, KbDocTypeEnum.INSTITUTION,
                    "第一条 适用范围\n本制度适用于隔离测试场景。\n"
                            + "第二条 付款要求\n预付款比例不得超过合同总价的百分之三十。", false);
        } finally {
            TenantContext.clear();
        }
    }
}
