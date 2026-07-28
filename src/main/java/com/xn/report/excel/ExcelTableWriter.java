package com.xn.report.excel;

import com.xn.report.dataset.DatasetRow;
import java.util.List;
import java.util.ArrayList;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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

        List<CellStyle> prototypeDataStyles =
                prototypeStyles(sheet.getRow(startRow + 1), fields.size());
        CellStyle prototypeHeaderStyle =
                firstStyle(sheet.getRow(startRow));
        clearSheetData(sheet, startRow);
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = sheet.createRow(startRow);
        for (int column = 0; column < fields.size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(headers.get(column));
            cell.setCellStyle(prototypeHeaderStyle == null
                    ? headerStyle : prototypeHeaderStyle);
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
            Row row = sheet.createRow(startRow + rowIndex + 1);
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
                sheet, tableName, startRow,
                fields.size(), rows.size());
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
        sheet.setAutoFilter(new CellRangeAddress(
                startRow, startRow + rows.size(),
                0, fields.size() - 1));
        return table;
    }

    private static void clearSheetData(
            XSSFSheet sheet, int startRow) {
        for (int rowIndex = sheet.getLastRowNum();
                rowIndex >= startRow; rowIndex--) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
        for (XSSFTable table : sheet.getTables()) {
            table.getCTTable().setRef("A1:A1");
        }
        while (!sheet.getTables().isEmpty()) {
            sheet.removeTable(sheet.getTables().get(0));
        }
    }

    private static XSSFTable recreateTable(
            XSSFSheet sheet,
            String tableName,
            int startRow,
            int columnCount,
            int rowCount) {
        AreaReference area = new AreaReference(
                new CellReference(startRow, 0),
                new CellReference(startRow + rowCount, columnCount - 1),
                SpreadsheetVersion.EXCEL2007);
        XSSFTable table = sheet.createTable(area);
        table.setName(tableName);
        table.setDisplayName(tableName);
        table.getCTTable().setRef(area.formatAsString());
        if (!table.getCTTable().isSetAutoFilter()) {
            table.getCTTable().addNewAutoFilter();
        }
        table.getCTTable().getAutoFilter().setRef(area.formatAsString());
        table.getCTTable().setHeaderRowCount(1L);
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

    private static CellStyle firstStyle(Row row) {
        Cell cell = row == null ? null : row.getCell(0);
        return cell == null ? null : cell.getCellStyle();
    }

    private static CellStyle[] explicitFormats(
            XSSFWorkbook workbook,
            List<String> formats,
            List<CellStyle> prototypes) {
        CellStyle[] styles = new CellStyle[formats.size()];
        for (int column = 0; column < formats.size(); column++) {
            String format = formats.get(column);
            if (format != null && !format.trim().isEmpty()) {
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
}
