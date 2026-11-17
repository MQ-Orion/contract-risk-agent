package com.crag.agent.report;

import com.crag.agent.review.ReportMeta;
import com.crag.agent.review.RiskItem;
import com.crag.common.domain.RiskLevelEnum;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

/**
 * DOCX 报告渲染（规范 9.3）。
 *
 * <p>报告由风险明细实时渲染，不保存正文副本：入参是本次审核的明细模型，每次导出重新生成并覆盖。
 * 副本一旦存在，风险项修正后报告不会随之更新，就会形成两套事实（规范 9.3）。</p>
 *
 * <p>必备内容：生成时间、模型版本、Prompt 版本、检索降级标记、评估状态；每条风险附来源文档名、
 * 切片定位与引用原文；页脚固定声明“不构成法律意见”。</p>
 *
 * <p>线程安全性：无状态，可共享。</p>
 */
public class DocxReportRenderer {

    /** 页脚固定声明（规范 9.3）。 */
    public static final String DISCLAIMER = "本报告由系统辅助生成，仅作初审参考，不构成法律意见。";

    /** 四条禁止（规范 1.3），必须与 README、接口文档、System Prompt 一致。 */
    public static final List<String> PROHIBITIONS = List.of(
            "本报告不做法律意见。",
            "本报告不替代法务决策。",
            "本报告不承诺审核结论必然正确。",
            "本报告不对合同的法律效力作出判断。");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    /**
     * 渲染报告。
     *
     * @param risks 风险明细
     * @param meta  元信息
     * @return DOCX 字节
     * @throws IOException 渲染失败
     */
    public byte[] render(List<RiskItem> risks, ReportMeta meta) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeTitle(document, "合同风险初审报告");
            writeMeta(document, meta);
            writeSummary(document, risks);
            writeRiskTable(document, risks);
            writeDisclaimer(document);
            document.write(out);
            return out.toByteArray();
        }
    }

    private void writeTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(18);
        run.setFontFamily("宋体");
    }

    private void writeMeta(XWPFDocument document, ReportMeta meta) {
        line(document, "合同名称：" + meta.contractName());
        line(document, "合同标识：" + meta.contractId() + "　　条款数：" + meta.clauseCount());
        line(document, "生成时间：" + TIME_FORMAT.format(meta.generatedAt()));
        line(document, "模型版本：" + meta.modelVersion() + "　　Prompt 版本：" + meta.promptVersion()
                + "　　规则版本：" + meta.ruleCacheVersion());
        line(document, "检索状态：" + (meta.retrievalDegraded() ? "已降级（部分依据可能缺失）" : "正常")
                + "　　模型状态：" + (meta.modelDegraded() ? "不可用（仅规则结论）" : "正常"));
        line(document, "抽取状态：" + meta.extractionStatus() + "　　抽取回验通过率："
                + percent(meta.extractionVerifiedRate()));
        line(document, "评估状态：" + meta.evaluationStatus());
    }

    private void writeSummary(XWPFDocument document, List<RiskItem> risks) {
        long high = risks.stream().filter(risk -> risk.level() == RiskLevelEnum.HIGH).count();
        long medium = risks.stream().filter(risk -> risk.level() == RiskLevelEnum.MEDIUM).count();
        long low = risks.stream().filter(risk -> risk.level() == RiskLevelEnum.LOW).count();
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setText("风险汇总：高 " + high + " 条，中 " + medium + " 条，低 " + low + " 条；高风险与中风险须人工复核后方可出具结论。");
        run.setFontFamily("宋体");
    }

    private void writeRiskTable(XWPFDocument document, List<RiskItem> risks) {
        // 固定列宽 + 固定布局：默认自动布局会把“等级/条款”挤成窄列，长正文把表格撑成锯齿状
        int[] columnWidths = {760, 1500, 3560, 1900, 1640};
        XWPFTable table = document.createTable(1, columnWidths.length);
        table.setWidth("100%");
        table.setCellMargins(60, 80, 60, 80);
        CTTblLayoutType layout = table.getCTTbl().getTblPr().addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        // createTable 不保证已生成 tblGrid：先补齐列定义再写宽度，否则 getGridColList() 为空指针
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid grid =
                table.getCTTbl().getTblGrid();
        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() < columnWidths.length) {
            grid.addNewGridCol();
        }
        for (int i = 0; i < columnWidths.length; i++) {
            grid.getGridColList().get(i).setW(BigInteger.valueOf(columnWidths[i]));
        }
        XWPFTableRow header = table.getRow(0);
        header.setRepeatHeader(true);
        setCell(header, 0, "等级", true);
        setCell(header, 1, "条款", true);
        setCell(header, 2, "风险说明", true);
        setCell(header, 3, "建议", true);
        setCell(header, 4, "来源与引用", true);
        for (RiskItem risk : risks) {
            XWPFTableRow row = table.createRow();
            setCell(row, 0, risk.level().label(), false);
            setCell(row, 1, risk.clauseNo() + "（" + risk.clauseType() + "）", false);
            setCell(row, 2, risk.content() + sourceTag(risk), false);
            setCell(row, 3, risk.suggestion(), false);
            setCell(row, 4, provenance(risk), false);
        }
        if (risks.isEmpty()) {
            XWPFTableRow row = table.createRow();
            setCell(row, 0, "-", false);
            setCell(row, 1, "-", false);
            setCell(row, 2, "未发现规则命中或模型判定的风险项，仍须人工复核。", false);
            setCell(row, 3, "-", false);
            setCell(row, 4, "-", false);
        }
    }

    private static String sourceTag(RiskItem risk) {
        return switch (risk.source()) {
            case "RULE" -> "（规则 " + risk.ruleCode() + " v" + risk.ruleVersion() + "）";
            case "MODEL" -> "（模型判定，未经规则确认）";
            case "RULE_CONFLICT" -> "（规则与模型结论冲突，以规则为准，差异需人工确认）";
            default -> "";
        };
    }

    private static String provenance(RiskItem risk) {
        String citation = risk.citationVerified() ? "引用已回验" : "引用未通过回验，待人工确认";
        return "来源：" + (risk.sourceDocName() == null ? "合同条款" : risk.sourceDocName())
                + "；定位：" + (risk.chunkKey() == null ? risk.clauseNo() : risk.chunkKey())
                + "；原文：" + (risk.excerpt() == null ? "（无）" : risk.excerpt())
                + "；" + citation;
    }

    private void writeDisclaimer(XWPFDocument document) {
        document.createParagraph();
        XWPFParagraph footer = document.createParagraph();
        for (String text : PROHIBITIONS) {
            XWPFRun run = footer.createRun();
            run.setText(text);
            run.setFontFamily("宋体");
            run.addBreak();
        }
        XWPFRun run = footer.createRun();
        run.setText(DISCLAIMER);
        run.setBold(true);
        run.setFontFamily("宋体");
    }

    private static void line(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("宋体");
        run.setFontSize(10);
    }

    private static void setCell(XWPFTableRow row, int index, String text, boolean bold) {
        if (row.getCell(index) == null) {
            row.createCell();
        }
        var cell = row.getCell(index);
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        // 单元格内不留段前段后间距，避免多行文本把行高撑得参差不齐
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0);
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
        run.setBold(bold);
        run.setFontFamily("宋体");
        run.setFontSize(9);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
    }

    /**
     * 百分比格式化：保留一位小数，用于回验通过率（实测值，规范 12.6）。
     *
     * @param value 0–1
     * @return 百分比文本
     */
    static String percent(double value) {
        return BigDecimal.valueOf(value * 100).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
