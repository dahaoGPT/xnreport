package com.xn.report.excel;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ExcelTableNameRules {

    private static final Pattern SAFE_NAME =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_.]*$");
    private static final Pattern A1_REFERENCE =
            Pattern.compile("(?i)^[A-Z]{1,3}[1-9][0-9]*$");
    private static final Pattern R1C1_REFERENCE =
            Pattern.compile("(?i)^R[1-9][0-9]*C[1-9][0-9]*$");

    private ExcelTableNameRules() {
    }

    public static void validate(String tableName) {
        if (tableName == null
                || tableName.trim().isEmpty()
                || tableName.length() > 255
                || !SAFE_NAME.matcher(tableName).matches()
                || A1_REFERENCE.matcher(tableName).matches()
                || R1C1_REFERENCE.matcher(tableName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Excel table name: " + tableName);
        }
    }

    public static String normalized(String tableName) {
        validate(tableName);
        return tableName.toLowerCase(Locale.ROOT);
    }
}
