package com.crag.doc.domain.validation;

/**
 * 解析文本质量判定器（规范 5.2）。
 *
 * <p>可疑字符包括 Unicode 替换字符（U+FFFD）、私用区字符与控制字符；这些字符在正常合同中占比极低，
 * 占比超过 5% 通常意味着字体缺字或编码错误，此时继续切分只会产出不可用的检索单元。</p>
 */
public final class TextQualityChecker {

    /** 可疑字符占比上限（单位：比例）。 */
    public static final double MAX_SUSPICIOUS_CHAR_RATIO = 0.05D;

    /**
     * 校验文本质量。
     *
     * @param text 解析出的纯文本
     * @return 质量报告
     */
    public QualityReport check(String text) {
        if (text == null || text.isBlank()) {
            return QualityReport.fail(1.0D, "text is empty");
        }
        int visible = 0;
        int suspicious = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            visible++;
            if (isSuspicious(c)) {
                suspicious++;
            }
        }
        if (visible == 0) {
            return QualityReport.fail(1.0D, "text has no visible characters");
        }
        double ratio = (double) suspicious / visible;
        if (ratio > MAX_SUSPICIOUS_CHAR_RATIO) {
            return QualityReport.fail(ratio, "suspicious char ratio " + ratio);
        }
        return QualityReport.pass(ratio);
    }

    private static boolean isSuspicious(char c) {
        if (c == '\uFFFD') {
            return true;
        }
        if (c >= '\uE000' && c <= '\uF8FF') {
            return true;
        }
        if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
            return true;
        }
        return false;
    }
}
