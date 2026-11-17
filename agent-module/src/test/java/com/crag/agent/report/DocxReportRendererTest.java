package com.crag.agent.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.agent.review.ReportMeta;
import com.crag.agent.review.RiskItem;
import com.crag.common.domain.RiskLevelEnum;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 报告渲染测试（规范 9.3）：声明、溯源、版本信息必须出现在文档里。
 */
class DocxReportRendererTest {

    private final DocxReportRenderer renderer = new DocxReportRenderer();

    private static ReportMeta meta() {
        return new ReportMeta(9L, "采购合同", Instant.parse("2026-11-14T12:00:00Z"), "qwen3.8-flash",
                "risk-v1", 1794660969289L, true, false, "VERIFIED", 1.0D, "未评估", 12);
    }

    private static RiskItem risk() {
        return new RiskItem(RiskLevelEnum.HIGH, "第十三条", "BREACH", "赔偿无上限", "赔偿责任未设上限",
                "建议增加封顶条款", "风控制度汇编", "kb:doc#1", "乙方应赔偿由此产生的全部损失", "RULE",
                "BREACH.NO_LIABILITY_CAP.02", 3, true, true, "乙方应赔偿由此产生的全部损失", "[]");
    }

    private static String documentXml(byte[] docx) throws IOException {
        try (InputStream in = new ByteArrayInputStream(docx); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    @Test
    @DisplayName("报告含页脚声明与四条禁止")
    void containsDisclaimer() throws IOException {
        String xml = documentXml(renderer.render(List.of(risk()), meta()));
        assertThat(xml).contains("本报告由系统辅助生成，仅作初审参考，不构成法律意见。");
        assertThat(xml).contains("不做法律意见");
        assertThat(xml).contains("不替代法务决策");
        assertThat(xml).contains("不承诺审核结论必然正确");
        assertThat(xml).contains("不对合同的法律效力作出判断");
    }

    @Test
    @DisplayName("报告含生成时间、模型版本、Prompt 版本与检索降级标记")
    void containsVersionInfo() throws IOException {
        String xml = documentXml(renderer.render(List.of(risk()), meta()));
        assertThat(xml).contains("生成时间");
        assertThat(xml).contains("qwen3.8-flash");
        assertThat(xml).contains("risk-v1");
        assertThat(xml).contains("已降级");
        assertThat(xml).contains("抽取回验通过率：100.0%");
    }

    @Test
    @DisplayName("每条风险带来源文档名、切片定位与引用原文")
    void containsProvenance() throws IOException {
        String xml = documentXml(renderer.render(List.of(risk()), meta()));
        assertThat(xml).contains("风控制度汇编");
        assertThat(xml).contains("kb:doc#1");
        assertThat(xml).contains("乙方应赔偿由此产生的全部损失");
        assertThat(xml).contains("引用已回验");
        assertThat(xml).contains("BREACH.NO_LIABILITY_CAP.02");
    }

    @Test
    @DisplayName("无风险时仍输出提示行，不生成空报告")
    void rendersEmptyRiskNotice() throws IOException {
        String xml = documentXml(renderer.render(List.of(), meta()));
        assertThat(xml).contains("未发现规则命中或模型判定的风险项");
    }
}
