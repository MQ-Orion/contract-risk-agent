package com.crag.doc.domain.split;

import com.crag.common.text.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.crag.common.text.ClauseBlock;
import com.crag.common.text.ClauseTypeEnum;
import com.crag.common.text.ParsedClause;
import com.crag.common.text.TypeSourceEnum;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 条款切分器测试（规范 13.1：切分核心逻辑覆盖率不低于 80%）。
 */
class ClauseSplitterTest {

    private final ClauseSplitter splitter = new ClauseSplitter(SplitConfig.defaults(), new ClauseTypeClassifier());

    @Test
    @DisplayName("中文条序号切分：前言独立成块，条款序号与类型正确")
    void splitsChineseClauseMarkers() {
        String text = String.join("\n",
                "设备采购合同",
                "第一条 合同标的",
                "甲方向乙方采购服务器十台。",
                "第二条 付款方式",
                "合同签订后十日内支付百分之三十预付款。");

        List<ParsedClause> clauses = splitter.split(text);

        assertThat(clauses).hasSize(3);
        assertThat(clauses.get(0).clauseNo()).isEqualTo(ClauseSplitter.PREAMBLE_CLAUSE_NO);
        assertThat(clauses.get(1).clauseNo()).isEqualTo("第一条");
        assertThat(clauses.get(2).clauseNo()).isEqualTo("第二条");
        assertThat(clauses.get(2).clauseType()).isEqualTo(ClauseTypeEnum.PAYMENT);
        assertThat(clauses.get(2).typeSource()).isEqualTo(TypeSourceEnum.KEYWORD);
        assertThat(clauses).extracting(ParsedClause::fullTextIndex).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("阿拉伯数字多级编号切分")
    void splitsArabicMultiLevelMarkers() {
        String text = String.join("\n",
                "1. 服务内容",
                "乙方提供运维服务。",
                "1.1 运维范围",
                "包含系统巡检与故障处理。",
                "1.2.1 响应时限",
                "故障响应时间不超过两小时。");

        List<ParsedClause> clauses = splitter.split(text);

        assertThat(clauses).extracting(ParsedClause::clauseNo)
                .containsExactly("1", "1.1", "1.2.1");
    }

    @Test
    @DisplayName("中文括号序号切分")
    void splitsChineseParenMarkers() {
        String text = String.join("\n",
                "保密协议",
                "（一）保密范围",
                "包括技术资料与商业计划。",
                "（二）保密期限",
                "自签署之日起五年。");

        List<ParsedClause> clauses = splitter.split(text);

        assertThat(clauses).hasSize(3);
        assertThat(clauses.get(1).clauseNo()).isEqualTo("（一）");
        assertThat(clauses.get(2).clauseType()).isEqualTo(ClauseTypeEnum.CONFIDENTIALITY);
    }

    @Test
    @DisplayName("切分结果可回溯：切片偏移在原文中逐字一致")
    void chunkOffsetsPointBackToSourceText() {
        String text = String.join("\n",
                "技术服务合同",
                "第一条 服务内容",
                "乙方提供系统运维与巡检服务。",
                "第二条 违约责任",
                "逾期交付的，每逾期一日按合同总额的千分之三支付违约金。");

        for (ParsedClause clause : splitter.split(text)) {
            assertThat(text.substring(clause.charStart(), clause.charEnd())).isEqualTo(clause.text());
        }
    }

    @Test
    @DisplayName("超长条款按 800 字窗口二次切分，重叠 10%，不跨条款边界")
    void splitsOverlongClauseWithOverlap() {
        String longBody = "验收标准与运输保险约定。".repeat(150);
        String text = String.join("\n",
                "第一条 其他约定",
                longBody,
                "第二条 管辖",
                "由甲方所在地法院管辖。");

        List<ParsedClause> clauses = splitter.split(text);
        SplitConfig config = SplitConfig.defaults();
        List<ParsedClause> longParts = clauses.stream()
                .filter(clause -> "第一条".equals(clause.clauseNo()))
                .toList();

        assertThat(longParts.size()).isGreaterThan(1);
        assertThat(longParts).allSatisfy(part -> {
            assertThat(part.length()).isLessThanOrEqualTo(config.maxClauseLengthChars());
            assertThat(part.blockIndex()).isEqualTo(longParts.get(0).blockIndex());
            assertThat(part.partCount()).isEqualTo(longParts.size());
        });
        for (int i = 1; i < longParts.size(); i++) {
            ParsedClause previous = longParts.get(i - 1);
            ParsedClause current = longParts.get(i);
            assertThat(current.charStart() - previous.charStart()).isEqualTo(config.stepChars());
            int overlap = previous.charEnd() - current.charStart();
            assertThat(overlap).isEqualTo(config.overlapChars());
        }
        assertThat(longParts.get(longParts.size() - 1).charEnd())
                .isEqualTo("第一条 其他约定".length() + 1 + longBody.length());
    }

    @Test
    @DisplayName("表格数量行（数字+空格+单位）不被误判为条款标记")
    void ignoresTableQuantityLines() {
        String text = String.join("\n",
                "第一条 合同标的与设备清单",
                "甲方向乙方采购下列设备：",
                "12 台   86,500.00       充电基座 CB-12",
                "4 套    1,200.00        调试工具包 TK-05",
                "第二条 质量标准",
                "设备应为全新未使用产品。");

        List<ClauseBlock> blocks = splitter.splitIntoBlocks(text);

        assertThat(blocks).extracting(ClauseBlock::clauseNo).containsExactly("第一条", "第二条");
        assertThat(blocks.get(0).length()).isGreaterThan(40);
    }

    @Test
    @DisplayName("单级阿拉伯编号必须带分隔符才识别")
    void requiresSeparatorForPlainArabicMarker() {
        assertThat(splitter.splitIntoBlocks("1、定义\n本合同术语如下。"))
                .extracting(ClauseBlock::clauseNo).containsExactly("1");
        assertThat(splitter.splitIntoBlocks("1 定义\n本合同术语如下。"))
                .extracting(ClauseBlock::clauseNo).containsExactly(ClauseSplitter.BODY_CLAUSE_NO);
    }

    @Test
    @DisplayName("无条款标记时整体作为正文块，不丢内容")
    void fallsBackToSingleBodyBlock() {
        String text = "本备忘录就双方合作意向作出说明，未设条款编号。";
        List<ParsedClause> clauses = splitter.split(text);

        assertThat(clauses).hasSize(1);
        assertThat(clauses.get(0).clauseNo()).isEqualTo(ClauseSplitter.BODY_CLAUSE_NO);
        assertThat(clauses.get(0).text()).isEqualTo(text);
    }

    @Test
    @DisplayName("行内出现的条款字样不触发切分")
    void ignoresInlineClauseMentions() {
        String text = String.join("\n",
                "第一条 定义",
                "本合同依据第一条与第二条的约定执行。");
        List<ClauseBlock> blocks = splitter.splitIntoBlocks(text);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).clauseNo()).isEqualTo("第一条");
    }

    @Test
    @DisplayName("空白文本返回空结果")
    void returnsEmptyForBlankText() {
        assertThat(splitter.split("")).isEmpty();
        assertThat(splitter.split("   \n  ")).isEmpty();
        assertThat(splitter.split(null)).isEmpty();
    }

    @Test
    @DisplayName("全角空格与缩进不影响标记识别，块区间已去除首尾空白")
    void trimsBlockBoundaries() {
        String text = "\u3000\u3000第一条 付款\n\u3000\u3000合同签订后支付。";
        List<ClauseBlock> blocks = splitter.splitIntoBlocks(text);

        assertThat(blocks).hasSize(1);
        assertThat(text.substring(blocks.get(0).charStart(), blocks.get(0).charEnd()))
                .isEqualTo("第一条 付款\n\u3000\u3000合同签订后支付。");
    }
}
