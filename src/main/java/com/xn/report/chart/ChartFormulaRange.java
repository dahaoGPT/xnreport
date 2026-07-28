package com.xn.report.chart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChartFormulaRange {

    private final String sheetName;
    private final int headerRow;
    private final int firstDataRow;
    private final int pointCount;
    private final Map<String, Integer> fieldColumns;

    public ChartFormulaRange(
            String sheetName,
            int headerRow,
            int firstDataRow,
            int pointCount,
            Map<String, Integer> fieldColumns) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Chart range sheetName must not be blank");
        }
        if (headerRow < 0 || firstDataRow < 0 || pointCount < 0) {
            throw new IllegalArgumentException(
                    "Chart range rows and pointCount must not be negative");
        }
        this.sheetName = sheetName;
        this.headerRow = headerRow;
        this.firstDataRow = firstDataRow;
        this.pointCount = pointCount;
        this.fieldColumns = immutableColumns(fieldColumns);
    }

    public static ChartFormulaRange empty(String sheetName) {
        return new ChartFormulaRange(
                sheetName, 0, 1, 0,
                Collections.<String, Integer>emptyMap());
    }

    private static Map<String, Integer> immutableColumns(
            Map<String, Integer> columns) {
        Map<String, Integer> copy =
                new LinkedHashMap<String, Integer>();
        if (columns != null) {
            for (Map.Entry<String, Integer> entry : columns.entrySet()) {
                if (entry.getKey() == null
                        || entry.getKey().trim().isEmpty()
                        || entry.getValue() == null
                        || entry.getValue().intValue() < 0) {
                    throw new IllegalArgumentException(
                            "Invalid chart field column");
                }
                copy.put(entry.getKey().toLowerCase(
                        java.util.Locale.ROOT), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public int column(String field) {
        if (field == null) {
            throw new IllegalArgumentException(
                    "Chart field must not be null");
        }
        Integer column = fieldColumns.get(
                field.toLowerCase(java.util.Locale.ROOT));
        if (column == null) {
            throw new IllegalArgumentException(
                    "Chart field is not present on dataset sheet "
                            + sheetName + ": " + field);
        }
        return column.intValue();
    }

    public String formula(String field) {
        return new ChartFormulaBuilder().range(
                sheetName, column(field), firstDataRow, pointCount);
    }

    public String titleFormula(String field) {
        return new ChartFormulaBuilder().cell(
                sheetName, column(field), headerRow);
    }

    public String getSheetName() {
        return sheetName;
    }

    public int getHeaderRow() {
        return headerRow;
    }

    public int getFirstDataRow() {
        return firstDataRow;
    }

    public int getPointCount() {
        return pointCount;
    }

    public Map<String, Integer> getFieldColumns() {
        return fieldColumns;
    }
}
