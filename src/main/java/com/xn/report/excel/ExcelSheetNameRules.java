package com.xn.report.excel;

import org.apache.poi.ss.util.WorkbookUtil;

public final class ExcelSheetNameRules {

    private ExcelSheetNameRules() {
    }

    public static void validate(String sheetName) {
        WorkbookUtil.validateSheetName(sheetName);
    }

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
