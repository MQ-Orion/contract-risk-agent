package com.crag.common.text;

/**
 * 文本归一化：只服务于“引用是否逐字存在于切片”的校验（规范 7.5），不用于展示。
 *
 * <p>为什么必须归一化：解析产生的空格与换行、全角与半角、中文与英文标点差异，会让同一个正确引用
 * 在不同版式的合同上得到不同校验结果——风险项被成批降级为“待人工确认”，引用准确率也随之不可测（规范 7.5）。</p>
 *
 * <p>规则：全角字符转半角（含全角空格）、去掉全部空白（含换行与制表）、中文标点映射为等价的 ASCII 标点。
 * 不做大小写折叠：英文条款的大小写可能承载含义，折叠会引入新的误判。</p>
 *
 * <p>线程安全性：无可变状态。</p>
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * 归一化文本。
     *
     * @param text 原文，可为 null
     * @return 归一化结果；null 返回空串
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '\u3000') {
                continue;
            }
            builder.append(unifyWidth(c));
        }
        return mapPunctuation(builder.toString());
    }

    /**
     * 归一化后判断包含关系。
     *
     * @param container 被校验文本（切片原文）
     * @param excerpt   引用片段
     * @return 引用逐字存在返回 true；任一方为空返回 false
     */
    public static boolean containsNormalized(String container, String excerpt) {
        String normalizedExcerpt = normalize(excerpt);
        if (normalizedExcerpt.isEmpty()) {
            return false;
        }
        return normalize(container).contains(normalizedExcerpt);
    }

    private static char unifyWidth(char c) {
        if (c == '\u3000') {
            return ' ';
        }
        if (c >= '\uFF01' && c <= '\uFF5E') {
            return (char) (c - 0xFEE0);
        }
        return c;
    }

    private static String mapPunctuation(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(switch (c) {
                case '。' -> '.';
                case '，', '、' -> ',';
                case '；' -> ';';
                case '：' -> ':';
                case '！' -> '!';
                case '？' -> '?';
                case '（' -> '(';
                case '）' -> ')';
                case '“', '”' -> '"';
                case '‘', '’' -> '\'';
                case '《' -> '<';
                case '》' -> '>';
                case '—', '－' -> '-';
                default -> c;
            });
        }
        return builder.toString();
    }
}
