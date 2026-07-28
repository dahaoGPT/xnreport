package com.xn.report.text;

public final class FormulaInjectionGuard {

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
