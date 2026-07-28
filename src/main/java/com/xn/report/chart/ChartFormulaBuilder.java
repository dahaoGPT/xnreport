package com.xn.report.chart;

import org.apache.poi.ss.util.CellReference;

/**
 * Builds external-looking chart formulas which remain directly traceable to
 * a visible worksheet in Excel's "Select Data" dialog.
 */
public final class ChartFormulaBuilder {

    public String range(
            String sheetName,
            int column,
            int firstDataRow,
            int pointCount) {
        validate(sheetName, column, firstDataRow);
        int lastRow = pointCount <= 0
                ? firstDataRow : firstDataRow + pointCount - 1;
        return quoted(sheetName) + "!"
                + new CellReference(firstDataRow, column, true, true)
                        .formatAsString()
                + ":"
                + new CellReference(lastRow, column, true, true)
                        .formatAsString();
    }

    public String cell(String sheetName, int column, int row) {
        validate(sheetName, column, row);
        return quoted(sheetName) + "!"
                + new CellReference(row, column, true, true)
                        .formatAsString();
    }

    private static String quoted(String sheetName) {
        return "'" + sheetName.replace("'", "''") + "'";
    }

    private static void validate(
            String sheetName, int column, int row) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Chart formula sheet name must not be blank");
        }
        if (column < 0 || row < 0) {
            throw new IllegalArgumentException(
                    "Chart formula row and column must not be negative");
        }
    }
}
