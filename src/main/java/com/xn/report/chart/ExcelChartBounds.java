package com.xn.report.chart;

import org.apache.poi.ss.SpreadsheetVersion;

/**
 * Overflow-safe validation for Excel row and column coordinates.
 */
final class ExcelChartBounds {

    private static final long MAX_COLUMNS =
            SpreadsheetVersion.EXCEL2007.getMaxColumns();
    private static final long MAX_ROWS =
            SpreadsheetVersion.EXCEL2007.getMaxRows();

    private ExcelChartBounds() {
    }

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
