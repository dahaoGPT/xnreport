package com.xn.report.chart;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图表与 Excel 工作表数据区域物理映射范围模型。
 * <p>
 * 封装数据所在的工作表名、表头行号、起始数据行号、数据点行数，以及各系列/气泡大小字段与物理列序号的精准对应关系。
 * </p>
 */
public final class ChartFormulaRange {

    private final String sheetName;
    private final int headerRow;
    private final int firstDataRow;
    private final int pointCount;
    private final Map<String, Integer> fieldColumns;
    private final List<Integer> seriesColumns;
    private final List<Integer> sizeColumns;

    public ChartFormulaRange(
            String sheetName,
            int headerRow,
            int firstDataRow,
            int pointCount,
            Map<String, Integer> fieldColumns) {
        this(sheetName, headerRow, firstDataRow, pointCount,
                fieldColumns, Collections.<Integer>emptyList(),
                Collections.<Integer>emptyList());
    }

    public ChartFormulaRange(
            String sheetName,
            int headerRow,
            int firstDataRow,
            int pointCount,
            Map<String, Integer> fieldColumns,
            List<Integer> seriesColumns,
            List<Integer> sizeColumns) {
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
        this.seriesColumns = immutableOrdinals(
                seriesColumns, "series");
        this.sizeColumns = immutableOrdinals(
                sizeColumns, "size");
        if (!this.sizeColumns.isEmpty()
                && this.sizeColumns.size()
                != this.seriesColumns.size()) {
            throw new IllegalArgumentException(
                    "Chart range size columns must align with series columns");
        }
    }

    private static List<Integer> immutableOrdinals(
            List<Integer> columns, String kind) {
        List<Integer> copy = new ArrayList<Integer>();
        if (columns != null) {
            for (Integer column : columns) {
                if (column != null && column.intValue() < 0) {
                    throw new IllegalArgumentException(
                            "Invalid chart " + kind + " column");
                }
                copy.add(column);
            }
        }
        return Collections.unmodifiableList(copy);
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

    public int seriesColumn(int ordinal, String fallbackField) {
        return ordinalColumn(
                seriesColumns, ordinal, fallbackField, false);
    }

    public String seriesFormula(
            int ordinal, String fallbackField) {
        return new ChartFormulaBuilder().range(
                sheetName,
                seriesColumn(ordinal, fallbackField),
                firstDataRow, pointCount);
    }

    public String seriesTitleFormula(
            int ordinal, String fallbackField) {
        return new ChartFormulaBuilder().cell(
                sheetName,
                seriesColumn(ordinal, fallbackField),
                headerRow);
    }

    public int sizeColumn(int ordinal, String fallbackField) {
        return ordinalColumn(
                sizeColumns, ordinal, fallbackField, true);
    }

    public String sizeFormula(
            int ordinal, String fallbackField) {
        return new ChartFormulaBuilder().range(
                sheetName,
                sizeColumn(ordinal, fallbackField),
                firstDataRow, pointCount);
    }

    private int ordinalColumn(
            List<Integer> columns,
            int ordinal,
            String fallbackField,
            boolean nullable) {
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "Chart series ordinal must not be negative");
        }
        if (columns.isEmpty()) {
            return column(fallbackField);
        }
        if (ordinal >= columns.size()) {
            throw new IllegalArgumentException(
                    "Chart series ordinal is outside its data range: "
                            + ordinal);
        }
        Integer column = columns.get(ordinal);
        if (column == null) {
            if (nullable) {
                throw new IllegalArgumentException(
                        "Chart series has no size column at ordinal "
                                + ordinal);
            }
            throw new IllegalArgumentException(
                    "Chart series has no value column at ordinal "
                                + ordinal);
        }
        return column.intValue();
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
