package com.crag.common.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条款级切分器（规范 5.3）：正则粗分割、超长条款二次切分、条款类型标注三段式。
 *
 * <p>三类条款标记：中文条序号（第X条）、阿拉伯数字多级编号（1.1 / 1.2.1 / 1、）、中文括号序号（（一））。
 * 超长条款以滑动窗口二次切分，窗口重叠 10%，且严禁跨越条款边界。</p>
 *
 * <p>线程安全性：本类无可变状态，可安全共享。</p>
 */
public final class ClauseSplitter {

    /** 中文条序号标记。 */
    private static final Pattern CN_CLAUSE_MARKER =
            Pattern.compile("^(第[一二三四五六七八九十百千零〇两0-9]{1,10}条)");
    /** 阿拉伯数字多级编号标记：必须含小数点分级，如 1.1、1.2.1。 */
    private static final Pattern ARABIC_DOTTED_MARKER =
            Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){1,3})(?=[\\s、.．:：]|$)");
    /** 单级阿拉伯编号标记：必须带分隔符（、．.：:），避免把“12 台”“4 套”这类表格数量误判为条款。 */
    private static final Pattern ARABIC_PLAIN_MARKER =
            Pattern.compile("^(\\d{1,3})(?=[、.．:：])");
    /** 中文括号序号标记：（一）。 */
    private static final Pattern CN_PAREN_MARKER =
            Pattern.compile("^([（(][一二三四五六七八九十]{1,3}[）)])");

    /** 前言块序号文案。 */
    public static final String PREAMBLE_CLAUSE_NO = "前言";
    /** 无任何条款标记时的正文块序号文案。 */
    public static final String BODY_CLAUSE_NO = "正文";

    private final SplitConfig config;
    private final ClauseTypeClassifier classifier;

    /**
     * 构造切分器。
     *
     * @param config     切分参数
     * @param classifier 条款类型判定器
     */
    public ClauseSplitter(SplitConfig config, ClauseTypeClassifier classifier) {
        this.config = config;
        this.classifier = classifier;
    }

    /**
     * 按自然条款边界切成块，不做超长二次切分。
     *
     * @param text 原文
     * @return 条款块列表，按出现顺序
     */
    public List<ClauseBlock> splitIntoBlocks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<RawBlock> raw = scanBlocks(text);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<ClauseBlock> blocks = new ArrayList<>(raw.size());
        int index = 0;
        for (RawBlock block : raw) {
            int start = block.start();
            int end = block.end();
            while (start < end && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            if (end <= start) {
                continue;
            }
            blocks.add(new ClauseBlock(index++, block.clauseNo(), start, end));
        }
        return List.copyOf(blocks);
    }

    /**
     * 切分为检索单元。
     *
     * @param text 原文
     * @return 切分结果，按全文顺序；原文为空时返回空列表
     */
    public List<ParsedClause> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ClauseBlock> blocks = splitIntoBlocks(text);
        List<ParsedClause> clauses = new ArrayList<>();
        int fullTextIndex = 0;
        for (ClauseBlock block : blocks) {
            String blockText = text.substring(block.charStart(), block.charEnd());
            // 规范 5.3：类型先看标题行，再看正文。标题行是作者显式声明的类别，正文只是佐证；
            // 反过来会让“违约责任”条款因正文出现“付款”而被误判为付款类。
            String headline = firstLine(blockText);
            TypeClassification classification = classifier.classify(headline, blockText);
            // 二次切分以“条款块”为单位，窗口绝不跨块：跨块会把两条独立条款粘成一个检索单元，
            // 引用溯源时无法定位到唯一条款（规范 5.3、9.3）。
            List<int[]> windows = windowRanges(blockText.length());
            int partIndex = 0;
            for (int[] window : windows) {
                String chunkText = blockText.substring(window[0], window[1]);
                clauses.add(new ParsedClause(
                        block.clauseNo(),
                        classification.clauseType(),
                        classification.typeSource(),
                        chunkText,
                        fullTextIndex++,
                        block.charStart() + window[0],
                        block.charStart() + window[1],
                        block.blockIndex(),
                        partIndex,
                        windows.size()));
                partIndex++;
            }
        }
        return List.copyOf(clauses);
    }

    /**
     * 计算块内二次切分窗口；长度不超限时返回单个全覆盖窗口。
     *
     * <p>窗口步长 = 最大长度 ×（1 - 重叠比例），默认 800 × 0.9 = 720，重叠 80 字。
     * 必须重叠：条款被硬切时，跨窗口的句子在两片中各留半句，重叠区保证至少有一片含完整句，
     * 否则检索会因半句而漏召（规范 6.2 的召回质量前提）。</p>
     *
     * @param length 块长度
     * @return 窗口数组，元素为 [start, end)
     */
    List<int[]> windowRanges(int length) {
        int max = config.maxClauseLengthChars();
        int step = config.stepChars();
        if (length <= max) {
            return List.of(new int[]{0, length});
        }
        List<int[]> windows = new ArrayList<>();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + max, length);
            windows.add(new int[]{start, end});
            if (end >= length) {
                break;
            }
            start += step;
        }
        return windows;
    }

    private List<RawBlock> scanBlocks(String text) {
        List<RawBlock> blocks = new ArrayList<>();
        int length = text.length();
        int lineStart = 0;
        int currentStart = -1;
        String currentClauseNo = null;
        // 逐行扫描而不是全文正则 split：条款标记只在行首有效，全文正则会命中正文里引用的“第 5 条规定”，
        // 把一条条款切成两段（规范 5.3）。行首约束 + 行内 strip 兼顾 PDF 解析出的行首空格。
        for (int i = 0; i <= length; i++) {
            if (i != length && text.charAt(i) != '\n') {
                continue;
            }
            String line = text.substring(lineStart, i);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                String marker = matchMarker(trimmed);
                if (marker != null) {
                    if (currentStart < 0) {
                        // 首个条款标记之前的内容（合同标题、甲乙方）单独成块并标为“前言”：
                        // 丢掉会丢失主体信息，混入第一条会污染该条款的类型判定与引用原文。
                        if (lineStart > 0) {
                            blocks.add(new RawBlock(0, lineStart, PREAMBLE_CLAUSE_NO));
                        }
                    } else {
                        blocks.add(new RawBlock(currentStart, lineStart, currentClauseNo));
                    }
                    currentStart = lineStart;
                    currentClauseNo = marker;
                }
            }
            lineStart = i + 1;
        }
        if (currentStart >= 0) {
            blocks.add(new RawBlock(currentStart, length, currentClauseNo));
        }
        // 全文无任何条款标记时（如扫描件只剩正文），整篇作为一个块，保证后续流程不因空列表而判切分失败。
        if (blocks.isEmpty() && !text.isBlank()) {
            blocks.add(new RawBlock(0, length, BODY_CLAUSE_NO));
        }
        return blocks;
    }

    private String matchMarker(String trimmedLine) {
        // 顺序不可交换：多级编号必须先于单级匹配，否则 "1.1" 会被 ARABIC_PLAIN_MARKER 当作 "1"。
        Matcher cn = CN_CLAUSE_MARKER.matcher(trimmedLine);
        if (cn.find()) {
            return cn.group(1);
        }
        Matcher dotted = ARABIC_DOTTED_MARKER.matcher(trimmedLine);
        if (dotted.find()) {
            return dotted.group(1);
        }
        Matcher plain = ARABIC_PLAIN_MARKER.matcher(trimmedLine);
        if (plain.find()) {
            return plain.group(1);
        }
        Matcher paren = CN_PAREN_MARKER.matcher(trimmedLine);
        if (paren.find()) {
            return paren.group(1);
        }
        return null;
    }

    private static String firstLine(String blockText) {
        int newline = blockText.indexOf('\n');
        return newline < 0 ? blockText : blockText.substring(0, newline);
    }

    /**
     * 原始块区间。
     *
     * @param start    起始偏移
     * @param end      结束偏移
     * @param clauseNo 条款序号
     */
    private record RawBlock(int start, int end, String clauseNo) {
    }
}
