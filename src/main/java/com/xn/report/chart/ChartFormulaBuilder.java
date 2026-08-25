package com.xn.report.chart;

import org.apache.poi.ss.util.CellReference;

/**
 * Excel 图表原生公式表达式构建器。
 * <p>
 * 生成可直接在 Excel“选择数据”对话框中回溯可见工作表的标准绝对引用公式（如 <code>'Sheet1'!$A$2:$A$10</code>）。
 * </p>
 */
public final class ChartFormulaBuilder {

    /**
     * 构建连续列单元格范围公式。
     *
     * @param sheetName 工作表名称
     * @param column 0-based 列索引
     * @param firstDataRow 0-based 数据起始行索引
     * @param pointCount 数据行数
     * @return 范围公式字符串
     */
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

    /**
     * 构建单个单元格引用公式（常用于系列名称标题绑定）。
     *
     * @param sheetName 工作表名称
     * @param column 0-based 列索引
     * @param row 0-based 行索引
     * @return 单元格引用公式字符串
     */
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
