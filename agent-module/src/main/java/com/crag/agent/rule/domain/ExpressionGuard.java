package com.crag.agent.rule.domain;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import java.util.Set;

/**
 * 表达式静态校验（规范 8.2）：标识符白名单 + 长度上限 + 危险字符拒绝。
 *
 * <p>为什么在 Aviator 特性开关之外还要做标识符白名单：特性开关只能关掉 {@code new}、模块、静态方法等语法糖，
 * 而 Aviator 允许在对象上调用方法，{@code "x".getClass().forName("java.lang.Runtime")} 这类链式反射不依赖任何特性。
 * 白名单只放行 7 个变量与 2 组内置函数名，{@code getClass}、{@code forName}、{@code exec} 等标识符在解析前即被拒绝。</p>
 *
 * <p>调用链：保存规则时 `RuleConfigService.validate` → 本类；加载缓存时 `RuleCache` 对每条规则再校验一次，
 * 防止有人直接改库绕过接口校验。</p>
 *
 * <p>线程安全性：无可变状态。</p>
 */
public final class ExpressionGuard {

    /** 表达式长度上限（规范 8.2）。 */
    public static final int MAX_EXPR_LENGTH = 500;

    /** 允许的标识符：6 个固定变量 + presentTypes + 内置函数名 + 字面量。 */
    private static final Set<String> ALLOWED_IDENTIFIERS = Set.of(
            "contractAmount", "paymentDays", "jurisdiction", "hasJointLiability", "clauseText", "clauseType",
            "presentTypes",
            "string", "contains", "startsWith", "endsWith", "length",
            "include",
            "true", "false", "nil");

    /** 允许的标点与运算符；{@code [}、{@code {}、{@code ;}、反斜杠等一律拒绝。 */
    private static final String ALLOWED_PUNCTUATION = "+-*/%<>=!&|(),.";

    private ExpressionGuard() {
    }

    /**
     * 校验表达式，非法时抛参数错误（10001）。
     *
     * @param expression 表达式
     * @throws BizException 表达式非法
     */
    public static void check(String expression) {
        String violation = findViolation(expression);
        if (violation != null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "expression rejected: " + violation);
        }
    }

    /**
     * 检查表达式，返回第一个违规描述。
     *
     * @param expression 表达式
     * @return 违规描述；合法返回 null
     */
    public static String findViolation(String expression) {
        if (expression == null || expression.isBlank()) {
            return "empty expression";
        }
        if (expression.length() > MAX_EXPR_LENGTH) {
            return "length " + expression.length() + " exceeds " + MAX_EXPR_LENGTH;
        }
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                int end = expression.indexOf(c, i + 1);
                if (end < 0) {
                    return "unterminated string literal at " + i;
                }
                // 字符串内不允许反斜杠转义：既是安全考虑，也让表达式保持人类可读
                if (expression.substring(i + 1, end).indexOf('\\') >= 0) {
                    return "escape sequence not allowed at " + i;
                }
                i = end + 1;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int end = i + 1;
                while (end < expression.length()
                        && (Character.isLetterOrDigit(expression.charAt(end)) || expression.charAt(end) == '_')) {
                    end++;
                }
                String identifier = expression.substring(i, end);
                if (!ALLOWED_IDENTIFIERS.contains(identifier)) {
                    return "identifier not allowed: " + identifier;
                }
                i = end;
                continue;
            }
            if (Character.isDigit(c) || c == '.') {
                int end = i + 1;
                while (end < expression.length()
                        && (Character.isDigit(expression.charAt(end)) || expression.charAt(end) == '.')) {
                    end++;
                }
                i = end;
                continue;
            }
            if (ALLOWED_PUNCTUATION.indexOf(c) >= 0) {
                i++;
                continue;
            }
            return "character not allowed: '" + c + "' at " + i;
        }
        return null;
    }
}
