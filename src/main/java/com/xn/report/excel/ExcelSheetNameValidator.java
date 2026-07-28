package com.xn.report.excel;

import java.util.ArrayList;
import java.util.List;

public final class ExcelSheetNameValidator {

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
