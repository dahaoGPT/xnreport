package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.excel.ExcelValueBinder;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Materializes the exact ChartModel view beside its SQL detail data.
 */
public final class ExcelChartDataAreaWriter {

    private static final String MARKER_PREFIX = "Chart data: ";

    public ChartFormulaRange write(
            XSSFWorkbook workbook,
            DatasetDefinition dataset,
            DatasetResult result,
            ChartDefinition definition,
            ChartModel model) {
        if (workbook == null || dataset == null || result == null
                || definition == null || model == null) {
            throw new IllegalArgumentException(
                    "Chart data area arguments must not be null");
        }
        XSSFSheet sheet = workbook.getSheet(dataset.getSheetName());
        if (sheet == null) {
            throw new IllegalArgumentException(
                    "Missing chart SQL data sheet: "
                            + dataset.getSheetName());
        }
        int startColumn = firstFreeColumn(sheet);
        int markerRow = 0;
        int headerRow = 1;
        int firstDataRow = 2;
        ExcelValueBinder binder = new ExcelValueBinder(workbook);
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);

        Cell marker = cell(sheet, markerRow, startColumn);
        marker.setCellValue(marker(definition, model));
        marker.setCellStyle(titleStyle);

        Map<String, Integer> columns =
                new LinkedHashMap<String, Integer>();
        List<Integer> seriesColumns =
                new ArrayList<Integer>();
        List<Integer> sizeColumns =
                new ArrayList<Integer>();
        int column = startColumn;
        Cell categoryHeader = cell(sheet, headerRow, column);
        categoryHeader.setCellValue(definition.getCategoryField());
        categoryHeader.setCellStyle(headerStyle);
        columns.put(definition.getCategoryField(), column);

        for (ChartSeriesModel series : model.getSeries()) {
            column++;
            Cell header = cell(sheet, headerRow, column);
            header.setCellValue(series.getName());
            header.setCellStyle(headerStyle);
            seriesColumns.add(Integer.valueOf(column));
            if (!columns.containsKey(series.getField())) {
                columns.put(series.getField(), column);
            }
            if (!series.getSizes().isEmpty()) {
                column++;
                String sizeField =
                        sizeField(definition, series.getField());
                Cell sizeHeader = cell(
                        sheet, headerRow, column);
                sizeHeader.setCellValue(series.getName() + " size");
                sizeHeader.setCellStyle(headerStyle);
                sizeColumns.add(Integer.valueOf(column));
                if (!columns.containsKey(sizeField)) {
                    columns.put(sizeField, column);
                }
            } else {
                sizeColumns.add(null);
            }
        }

        for (int index = 0;
                index < model.getCategories().size(); index++) {
            Object category = sourceCategory(
                    definition, result, model,
                    model.getCategories().get(index));
            binder.bind(cell(
                    sheet, firstDataRow + index,
                    columns.get(definition.getCategoryField())), category);
            for (int ordinal = 0;
                    ordinal < model.getSeries().size();
                    ordinal++) {
                ChartSeriesModel series =
                        model.getSeries().get(ordinal);
                binder.bind(cell(
                        sheet, firstDataRow + index,
                        seriesColumns.get(ordinal).intValue()),
                        series.getValues().get(index));
                if (!series.getSizes().isEmpty()) {
                    binder.bind(cell(
                            sheet, firstDataRow + index,
                            sizeColumns.get(ordinal).intValue()),
                            series.getSizes().get(index));
                }
            }
        }
        for (int current = startColumn; current <= column; current++) {
            sheet.autoSizeColumn(current);
        }
        return new ChartFormulaRange(
                sheet.getSheetName(), headerRow, firstDataRow,
                model.getCategories().size(), columns,
                seriesColumns, sizeColumns);
    }

    public ChartFormulaRange findRange(
            XSSFWorkbook workbook,
            DatasetDefinition dataset,
            ChartDefinition definition,
            ChartModel model) {
        XSSFSheet sheet = workbook.getSheet(dataset.getSheetName());
        if (sheet == null) {
            throw new IllegalStateException(
                    "Missing chart SQL data sheet: "
                            + dataset.getSheetName());
        }
        String expected = marker(definition, model);
        Cell found = null;
        for (Row row : sheet) {
            for (Cell candidate : row) {
                if (candidate.getCellType()
                        == org.apache.poi.ss.usermodel.CellType.STRING
                        && expected.equals(
                                candidate.getStringCellValue())) {
                    if (found != null) {
                        throw new IllegalStateException(
                                "Duplicate chart data area: " + expected);
                    }
                    found = candidate;
                }
            }
        }
        if (found == null) {
            throw new IllegalStateException(
                    "Missing chart data area: " + expected);
        }
        int headerRow = found.getRowIndex() + 1;
        int firstDataRow = headerRow + 1;
        int column = found.getColumnIndex();
        Map<String, Integer> columns =
                new LinkedHashMap<String, Integer>();
        List<Integer> seriesColumns =
                new ArrayList<Integer>();
        List<Integer> sizeColumns =
                new ArrayList<Integer>();
        columns.put(definition.getCategoryField(), column++);
        for (ChartSeriesModel series : model.getSeries()) {
            seriesColumns.add(Integer.valueOf(column));
            if (!columns.containsKey(series.getField())) {
                columns.put(series.getField(), column);
            }
            column++;
            if (!series.getSizes().isEmpty()) {
                String sizeField =
                        sizeField(definition, series.getField());
                sizeColumns.add(Integer.valueOf(column));
                if (!columns.containsKey(sizeField)) {
                    columns.put(sizeField, column);
                }
                column++;
            } else {
                sizeColumns.add(null);
            }
        }
        return new ChartFormulaRange(
                sheet.getSheetName(), headerRow, firstDataRow,
                model.getCategories().size(), columns,
                seriesColumns, sizeColumns);
    }

    private static String sizeField(
            ChartDefinition definition, String seriesField) {
        for (com.xn.report.config.definition.ChartSeriesDefinition series
                : definition.getSeries()) {
            if (seriesField.equalsIgnoreCase(series.getField())
                    && series.getSizeField() != null) {
                return series.getSizeField();
            }
        }
        return seriesField + "__size";
    }

    private static Object sourceCategory(
            ChartDefinition definition,
            DatasetResult result,
            ChartModel model,
            String categoryLabel) {
        if (result.type()
                != com.xn.report.dataset.DatasetType.LIST) {
            return categoryLabel;
        }
        for (DatasetRow row : result.list()) {
            if (definition.getGroupByField() != null
                    && !display(row.getOrNull(
                            definition.getGroupByField()))
                            .equals(model.getGroupKey())) {
                continue;
            }
            Object raw = row.getOrNull(definition.getCategoryField());
            if (display(raw).equals(categoryLabel)) {
                return raw == null ? categoryLabel : raw;
            }
        }
        return categoryLabel;
    }

    private static String display(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private static String marker(
            ChartDefinition definition, ChartModel model) {
        return MARKER_PREFIX + definition.getId()
                + (model.getGroupKey() == null
                ? "" : " [" + model.getGroupKey() + "]");
    }

    private static int firstFreeColumn(XSSFSheet sheet) {
        int rightmost = -1;
        for (Row row : sheet) {
            if (row.getLastCellNum() > 0) {
                rightmost = Math.max(
                        rightmost, row.getLastCellNum() - 1);
            }
        }
        for (org.apache.poi.ss.util.CellRangeAddress merged
                : sheet.getMergedRegions()) {
            rightmost = Math.max(rightmost, merged.getLastColumn());
        }
        for (org.apache.poi.xssf.usermodel.XSSFTable table
                : sheet.getTables()) {
            rightmost = Math.max(
                    rightmost,
                    table.getArea().getLastCell().getCol());
        }
        if (sheet.getDrawingPatriarch() != null) {
            for (org.openxmlformats.schemas.drawingml.x2006
                    .spreadsheetDrawing.CTTwoCellAnchor anchor
                    : sheet.getDrawingPatriarch().getCTDrawing()
                            .getTwoCellAnchorList()) {
                rightmost = Math.max(
                        rightmost, anchor.getTo().getCol());
            }
        }
        int first = rightmost < 0 ? 0 : rightmost + 2;
        if (first >= org.apache.poi.ss.SpreadsheetVersion.EXCEL2007
                .getMaxColumns()) {
            throw new IllegalArgumentException(
                    "No columns remain for chart data area on sheet "
                            + sheet.getSheetName());
        }
        return first;
    }

    private static Cell cell(
            XSSFSheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(columnIndex);
        return cell == null ? row.createCell(columnIndex) : cell;
    }

    private static CellStyle titleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(
                org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE
                        .getIndex());
        style.setFillPattern(
                org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
