package com.xn.report.excel;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 工作表批量命名合法性与唯一性校验器。
 * <p>
 * 遍历检查所有工作表名称的字符合规性，并确保全工作簿范围内工作表名称唯一（不区分大小写）。
 * </p>
 */
public final class ExcelSheetNameValidator {

    /**
     * 校验全部工作表名称列表。
     *
     * @param sheetNames 工作表名称集合
     * @throws IllegalArgumentException 若名称非法或存在重复
     */
    public void validateAll(Iterable<String> sheetNames) {
        if (sheetNames == null) {
            throw new IllegalArgumentException("sheetNames must not be null");
        }
        List<String> seen = new ArrayList<String>();
        for (String sheetName : sheetNames) {
            ExcelSheetNameRules.validate(sheetName);
            if (ExcelSheetNameRules.containsIgnoreCase(seen, sheetName)) {
                throw new IllegalArgumentException(
                        "Duplicate Excel sheet name: " + sheetName);
            }
            seen.add(sheetName);
        }
    }
}
