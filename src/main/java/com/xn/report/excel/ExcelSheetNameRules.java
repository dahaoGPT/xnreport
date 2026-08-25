package com.xn.report.excel;

import org.apache.poi.ss.util.WorkbookUtil;

/**
 * Excel 工作表（Sheet）命名规则校验工具类。
 * <p>
 * 遵循 Excel 命名约束规范：长度不超过 31 字符，且不得包含 <code>: \ / ? * [ ]</code> 等特殊字符。
 * </p>
 */
public final class ExcelSheetNameRules {

    private ExcelSheetNameRules() {
    }

    /**
     * 校验工作表名称是否符合 Excel 规范。
     *
     * @param sheetName 待校验工作表名
     * @throws IllegalArgumentException 若名称非法
     */
    public static void validate(String sheetName) {
        WorkbookUtil.validateSheetName(sheetName);
    }

    /**
     * 忽略大小写检查已存在的工作表名集合中是否包含候选名称。
     *
     * @param existingNames 已有名称迭代器
     * @param candidate 待检查候选名称
     * @return true 若已存在同名表（不区分大小写）
     */
    public static boolean containsIgnoreCase(
            Iterable<String> existingNames, String candidate) {
        for (String existingName : existingNames) {
            if (existingName.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }
}
