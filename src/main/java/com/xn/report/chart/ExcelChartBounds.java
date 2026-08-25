package com.xn.report.chart;

import org.apache.poi.ss.SpreadsheetVersion;

/**
 * Excel 2007+ (OOXML) 行列坐标与图表锚点防溢出安全校验工具。
 * <p>
 * 保证生成的图表位置锚点（Anchor）与旁路数据区域（Data Area）不会超出 Excel 最大列数（16,384）与最大行数（1,048,576）。
 * </p>
 */
final class ExcelChartBounds {

    private static final long MAX_COLUMNS =
            SpreadsheetVersion.EXCEL2007.getMaxColumns();
    private static final long MAX_ROWS =
            SpreadsheetVersion.EXCEL2007.getMaxRows();

    private ExcelChartBounds() {
    }

    /**
     * 校验数据区行列坐标是否超出 Excel 物理边界。
     */
    static void validateDataArea(
            long startColumn,
            long columnCount,
            long firstDataRow,
            long pointCount) {
        if (startColumn < 0L || columnCount <= 0L
                || startColumn > MAX_COLUMNS - columnCount) {
            throw new IllegalArgumentException(
                    "Chart data area columns exceed Excel bounds");
        }
        if (firstDataRow < 0L || pointCount < 0L
                || firstDataRow > MAX_ROWS - pointCount) {
            throw new IllegalArgumentException(
                    "Chart data area rows exceed Excel bounds");
        }
    }

    /**
     * 校验并构建图表锚点坐标包装对象。
     */
    static Anchor validateAnchor(
            long row,
            long column,
            long height,
            long width) {
        if (row < 0L || column < 0L
                || height <= 0L || width <= 0L
                || row > (MAX_ROWS - 1L) - height
                || column > (MAX_COLUMNS - 1L) - width) {
            throw new IllegalArgumentException(
                    "Chart anchor exceeds Excel bounds");
        }
        return new Anchor(
                (int) row, (int) column,
                (int) (row + height),
                (int) (column + width));
    }

    /**
     * 图表锚点起始行、列与终止行、列数据封装。
     */
    static final class Anchor {
        private final int row1;
        private final int column1;
        private final int row2;
        private final int column2;

        private Anchor(
                int row1, int column1, int row2, int column2) {
            this.row1 = row1;
            this.column1 = column1;
            this.row2 = row2;
            this.column2 = column2;
        }

        int row1() {
            return row1;
        }

        int column1() {
            return column1;
        }

        int row2() {
            return row2;
        }

        int column2() {
            return column2;
        }
    }
}
