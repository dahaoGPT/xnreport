package com.xn.report.text;

/**
 * Excel 公式注入（CSV/Excel Formula Injection / DDE）安全防护工具类。
 * <p>
 * 当文本以危险字符（{@code =}, {@code +}, {@code -}, {@code @} 等）开头时，
 * 自动在前缀追加单引号 {@code '} 消除公式执行风险，确保在电子表格软件中被作为纯文本安全显示。
 * </p>
 */
public final class FormulaInjectionGuard {

    /**
     * 将输入字符串转义为安全的纯文本形式（如果命中公式危险前缀则添加前导单引号）。
     *
     * @param value 原始文本
     * @return 安全净化后的字符串
     */
    public String asPlainText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int offset = 0;
        while (offset < value.length()) {
            int codePoint = value.codePointAt(offset);
            if (!isIgnorablePrefix(codePoint)) {
                return isFormulaPrefix(codePoint) ? "'" + value : value;
            }
            offset += Character.charCount(codePoint);
        }
        return value;
    }

    private static boolean isFormulaPrefix(int codePoint) {
        return codePoint == '='
                || codePoint == '+'
                || codePoint == '-'
                || codePoint == '@';
    }

    private static boolean isIgnorablePrefix(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }
}
