package com.xn.report.excel;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Excel 结构化表格（Table / ListObject）命名规则校验工具类。
 * <p>
 * 遵循 Excel 结构化表格命名标准约束：
 * <ul>
 *   <li>非空且长度不超过 255 字符。</li>
 *   <li>以字母或下划线开头，由字母、数字、下划线、句点构成。</li>
 *   <li>不能与标准单元格引用冲突（如 A1、BC12 等）。</li>
 *   <li>不能与 R1C1 引用格式冲突（如 R1C1）。</li>
 * </ul>
 * </p>
 */
public final class ExcelTableNameRules {

    private static final Pattern SAFE_NAME =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_.]*$");
    private static final Pattern A1_REFERENCE =
            Pattern.compile("(?i)^[A-Z]{1,3}[1-9][0-9]*$");
    private static final Pattern R1C1_REFERENCE =
            Pattern.compile("(?i)^R[1-9][0-9]*C[1-9][0-9]*$");

    private ExcelTableNameRules() {
    }

    /**
     * 校验表格名称是否符合 Excel 规范。
     *
     * @param tableName 表格名称
     * @throws IllegalArgumentException 若名称非法
     */
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

    /**
     * 校验并返回小写规范化名称。
     *
     * @param tableName 表格名称
     * @return 规范化名称
     */
    public static String normalized(String tableName) {
        validate(tableName);
        return tableName.toLowerCase(Locale.ROOT);
    }
}
