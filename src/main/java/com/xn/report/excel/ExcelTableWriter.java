package com.xn.report.excel;

import com.xn.report.dataset.DatasetRow;
import java.util.List;
import java.util.ArrayList;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;

public final class ExcelTableWriter {

    static final int MIN_COLUMN_WIDTH = 8 * 256;
    static final int MAX_COLUMN_WIDTH = 60 * 256;

    public XSSFTable write(
            XSSFWorkbook workbook,
            XSSFSheet sheet,
            String tableName,
            List<String> fields,
            List<DatasetRow> rows) {
        return write(
                workbook, sheet, tableName, 0,
                fields, fields,
                java.util.Collections.<String>nCopies(
                        fields == null ? 0 : fields.size(), null),
                rows);
    }

    public XSSFTable write(
            XSSFWorkbook workbook,
            XSSFSheet sheet,
            String tableName,
            int startRow,
            List<String> fields,
            List<String> headers,
            List<String> formats,
            List<DatasetRow> rows) {
        if (workbook == null || sheet == null) {
            throw new IllegalArgumentException(
                    "workbook and sheet must not be null");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel dataset table requires at least one field");
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows must not be null");
        }
        if (headers == null || headers.size() != fields.size()
                || formats == null || formats.size() != fields.size()) {
            throw new IllegalArgumentException(
                    "headers and formats must match fields");
        }
        if (startRow < 0 || startRow >= SpreadsheetVersion.EXCEL2007
                .getMaxRows()) {
            throw new IllegalArgumentException(
                    "startRow is outside XLSX bounds: " + startRow);
        }
        if ((long) startRow + rows.size()
                >= SpreadsheetVersion.EXCEL2007.getMaxRows()) {
            throw new IllegalArgumentException(
                    "Dataset table exceeds XLSX row limit");
        }

        ExcelTableNameRules.validate(tableName);
        AreaReference newArea = area(
                startRow, fields.size(), rows.size());
        XSSFTable target = findTargetTable(
                workbook, sheet, tableName, startRow);
        AreaReference oldArea =
                target == null ? null : target.getArea();
        validateWriteArea(sheet, target, oldArea, newArea);
        CTTableStyleInfo tableStyleInfo =
                copyStyleInfo(target);
        List<CellStyle> prototypeDataStyles =
                prototypeStyles(
                        sheet.getRow(oldArea == null
                                ? startRow + 1
                                : oldArea.getFirstCell().getRow() + 1),
                        fields.size());
        List<CellStyle> prototypeHeaderStyles =
                prototypeStyles(
                        sheet.getRow(oldArea == null
                                ? startRow
                                : oldArea.getFirstCell().getRow()),
                        fields.size());
        if (oldArea != null) {
            clearArea(sheet, oldArea);
        }
        if (target != null) {
            sheet.removeTable(target);
        }
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = row(sheet, startRow);
        for (int column = 0; column < fields.size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(headers.get(column));
            cell.setCellStyle(
                    prototypeHeaderStyles.get(column) == null
                            ? headerStyle
                            : prototypeHeaderStyles.get(column));
        }

        ExcelValueBinder binder = new ExcelValueBinder(workbook);
        CellStyle[] explicitFormats =
                explicitFormats(
                        workbook, formats, prototypeDataStyles);
        int[] maximumCharacters = new int[fields.size()];
        for (int column = 0; column < fields.size(); column++) {
            maximumCharacters[column] = headers.get(column).length();
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            DatasetRow datasetRow = rows.get(rowIndex);
            Row row = row(sheet, startRow + rowIndex + 1);
            for (int column = 0; column < fields.size(); column++) {
                String field = fields.get(column);
                if (!datasetRow.containsField(field)) {
                    throw new IllegalArgumentException(
                            "Dataset row is missing field " + field
                                    + " at row " + rowIndex);
                }
                Object value = datasetRow.get(field);
                Cell cell = row.createCell(column);
                if (prototypeDataStyles.get(column) != null) {
                    cell.setCellStyle(prototypeDataStyles.get(column));
                }
                binder.bind(cell, value);
                if (explicitFormats[column] != null) {
                    cell.setCellStyle(explicitFormats[column]);
                }
                maximumCharacters[column] = Math.max(
                        maximumCharacters[column], displayLength(value));
            }
        }

        XSSFTable table = recreateTable(
                sheet, tableName, newArea, tableStyleInfo);
        if (sheet.getPaneInformation() == null) {
            sheet.createFreezePane(0, startRow + 1);
        }
        for (int column = 0; column < fields.size(); column++) {
            int width = Math.max(
                    MIN_COLUMN_WIDTH,
                    Math.min(MAX_COLUMN_WIDTH,
                            (maximumCharacters[column] + 2) * 256));
            int defaultWidth = sheet.getDefaultColumnWidth() * 256;
            if (sheet.getColumnWidth(column) == defaultWidth) {
                sheet.setColumnWidth(column, width);
            }
        }
        return table;
    }

    private static Row row(XSSFSheet sheet, int rowIndex) {
        Row existing = sheet.getRow(rowIndex);
        return existing == null
                ? sheet.createRow(rowIndex) : existing;
    }

    private static void clearArea(
            XSSFSheet sheet, AreaReference area) {
        int firstRow = area.getFirstCell().getRow();
        int lastRow = area.getLastCell().getRow();
        int firstColumn = area.getFirstCell().getCol();
        int lastColumn = area.getLastCell().getCol();
        for (int rowIndex = firstRow;
                rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                for (int column = firstColumn;
                        column <= lastColumn; column++) {
                    Cell cell = row.getCell(column);
                    if (cell != null) {
                        row.removeCell(cell);
                    }
                }
                if (row.getPhysicalNumberOfCells() == 0) {
                    sheet.removeRow(row);
                }
            }
        }
    }

    private static XSSFTable recreateTable(
            XSSFSheet sheet,
            String tableName,
            AreaReference area,
            CTTableStyleInfo styleInfo) {
        XSSFTable table = sheet.createTable(area);
        table.setName(tableName);
        table.setDisplayName(tableName);
        table.getCTTable().setRef(area.formatAsString());
        if (!table.getCTTable().isSetAutoFilter()) {
            table.getCTTable().addNewAutoFilter();
        }
        table.getCTTable().getAutoFilter().setRef(area.formatAsString());
        table.getCTTable().setHeaderRowCount(1L);
        if (styleInfo != null) {
            table.getCTTable().setTableStyleInfo(styleInfo);
        }
        return table;
    }

    private static CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBottomBorderColor(IndexedColors.WHITE.getIndex());
        style.setBorderBottom(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private static int displayLength(Object value) {
        return value == null ? 0
                : Math.min(1000, String.valueOf(value).length());
    }

    private static List<CellStyle> prototypeStyles(
            Row row, int count) {
        List<CellStyle> styles = new ArrayList<CellStyle>(count);
        for (int column = 0; column < count; column++) {
            Cell cell = row == null ? null : row.getCell(column);
            styles.add(cell == null ? null : cell.getCellStyle());
        }
        return styles;
    }

    private static CellStyle[] explicitFormats(
            XSSFWorkbook workbook,
            List<String> formats,
            List<CellStyle> prototypes) {
        CellStyle[] styles = new CellStyle[formats.size()];
        for (int column = 0; column < formats.size(); column++) {
            String format = formats.get(column);
            if (format != null && format.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Excel table column format must be non-blank: "
                                + column);
            }
            if (format != null) {
                CellStyle style = workbook.createCellStyle();
                if (prototypes.get(column) != null) {
                    style.cloneStyleFrom(prototypes.get(column));
                }
                style.setDataFormat(
                        workbook.createDataFormat().getFormat(format));
                styles[column] = style;
            }
        }
        return styles;
    }

    private static AreaReference area(
            int startRow, int columnCount, int rowCount) {
        return new AreaReference(
                new CellReference(startRow, 0),
                new CellReference(
                        startRow + rowCount, columnCount - 1),
                SpreadsheetVersion.EXCEL2007);
    }

    private static XSSFTable findTargetTable(
            XSSFWorkbook workbook,
            XSSFSheet sheet,
            String tableName,
            int startRow) {
        XSSFTable named = null;
        for (int sheetIndex = 0;
                sheetIndex < workbook.getNumberOfSheets();
                sheetIndex++) {
            XSSFSheet candidateSheet =
                    workbook.getSheetAt(sheetIndex);
            for (XSSFTable table : candidateSheet.getTables()) {
                if (table.getName() != null
                        && table.getName().equalsIgnoreCase(
                                tableName)) {
                    if (candidateSheet != sheet) {
                        throw new IllegalArgumentException(
                                "Excel table name " + tableName
                                        + " collides with existing table "
                                        + table.getName()
                                        + " on sheet "
                                        + candidateSheet.getSheetName());
                    }
                    if (named != null && named != table) {
                        throw new IllegalArgumentException(
                                "Duplicate Excel table name: "
                                        + tableName);
                    }
                    named = table;
                }
            }
        }
        if (named != null) {
            return named;
        }
        XSSFTable byHeader = null;
        for (XSSFTable table : sheet.getTables()) {
            if (table.getArea().getFirstCell().getRow()
                            == startRow
                    && table.getArea().getFirstCell().getCol()
                            == 0) {
                if (byHeader != null) {
                    throw new IllegalArgumentException(
                            "Multiple Excel tables start at header row "
                                    + (startRow + 1)
                                    + " on sheet "
                                    + sheet.getSheetName());
                }
                byHeader = table;
            }
        }
        return byHeader;
    }

    private static void validateWriteArea(
            XSSFSheet sheet,
            XSSFTable target,
            AreaReference oldArea,
            AreaReference newArea) {
        for (XSSFTable table : sheet.getTables()) {
            if (table != target
                    && intersects(table.getArea(), newArea)) {
                throw new IllegalArgumentException(
                        "Excel dataset table range "
                                + newArea.formatAsString()
                                + " overlaps table "
                                + table.getName()
                                + " on sheet "
                                + sheet.getSheetName());
            }
        }
        int firstRow = newArea.getFirstCell().getRow();
        int lastRow = newArea.getLastCell().getRow();
        int firstColumn = newArea.getFirstCell().getCol();
        int lastColumn = newArea.getLastCell().getCol();
        for (int rowIndex = firstRow;
                rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int column = firstColumn;
                    column <= lastColumn; column++) {
                if (contains(oldArea, rowIndex, column)) {
                    continue;
                }
                Cell cell = row.getCell(column);
                if (cell != null
                        && cell.getCellType() != CellType.BLANK) {
                    throw new IllegalArgumentException(
                            "Excel dataset table range "
                                    + newArea.formatAsString()
                                    + " conflicts with preserved cell "
                                    + new CellReference(
                                            rowIndex, column)
                                            .formatAsString()
                                    + " on sheet "
                                    + sheet.getSheetName());
                }
            }
        }
    }

    private static boolean intersects(
            AreaReference left, AreaReference right) {
        return left.getFirstCell().getRow()
                        <= right.getLastCell().getRow()
                && right.getFirstCell().getRow()
                        <= left.getLastCell().getRow()
                && left.getFirstCell().getCol()
                        <= right.getLastCell().getCol()
                && right.getFirstCell().getCol()
                        <= left.getLastCell().getCol();
    }

    private static boolean contains(
            AreaReference area, int row, int column) {
        return area != null
                && row >= area.getFirstCell().getRow()
                && row <= area.getLastCell().getRow()
                && column >= area.getFirstCell().getCol()
                && column <= area.getLastCell().getCol();
    }

    private static CTTableStyleInfo copyStyleInfo(
            XSSFTable table) {
        if (table == null
                || !table.getCTTable().isSetTableStyleInfo()) {
            return null;
        }
        return (CTTableStyleInfo) table.getCTTable()
                .getTableStyleInfo().copy();
    }
}
