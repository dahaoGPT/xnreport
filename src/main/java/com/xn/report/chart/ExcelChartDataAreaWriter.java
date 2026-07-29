package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetResult;
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

    private static final String MARKER_PREFIX = "图表数据：";

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
        long columnCount = 1L + model.getSeries().size();
        for (ChartSeriesModel series : model.getSeries()) {
            if (!series.getSizes().isEmpty()) {
                columnCount++;
            }
        }
        ExcelChartBounds.validateDataArea(
                startColumn, columnCount, firstDataRow,
                model.getCategories().size());
        ChartSourceCategoryIndex categoryIndex =
                ChartSourceCategoryIndex.build(
                        definition, result, model.getGroupKey());
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

        for (int ordinal = 0;
                ordinal < model.getSeries().size(); ordinal++) {
            ChartSeriesModel series = model.getSeries().get(ordinal);
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
                        ChartSeriesConfigurationResolver.resolve(
                                definition, series, ordinal)
                                .getSizeField();
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
            Object category = categoryIndex.source(
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
        for (int ordinal = 0;
                ordinal < model.getSeries().size(); ordinal++) {
            ChartSeriesModel series = model.getSeries().get(ordinal);
            seriesColumns.add(Integer.valueOf(column));
            if (!columns.containsKey(series.getField())) {
                columns.put(series.getField(), column);
            }
            column++;
            if (!series.getSizes().isEmpty()) {
                String sizeField =
                        ChartSeriesConfigurationResolver.resolve(
                                definition, series, ordinal)
                                .getSizeField();
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

    private static String marker(
            ChartDefinition definition, ChartModel model) {
        return MARKER_PREFIX + definition.getId()
                + (model.getGroupKey() == null
                ? "" : ":" + model.getGroupKey());
    }

    private static int firstFreeColumn(XSSFSheet sheet) {
        long rightmost = -1L;
        for (Row row : sheet) {
            if (row.getLastCellNum() > 0) {
                rightmost = Math.max(
                        rightmost, row.getLastCellNum() - 1);
            }
        }
        for (org.apache.poi.ss.util.CellRangeAddress merged
                : sheet.getMergedRegions()) {
            rightmost = Math.max(
                    rightmost, (long) merged.getLastColumn());
        }
        for (org.apache.poi.xssf.usermodel.XSSFTable table
                : sheet.getTables()) {
            rightmost = Math.max(
                    rightmost,
                    (long) table.getArea().getLastCell().getCol());
        }
        if (sheet.getDrawingPatriarch() != null) {
            for (org.openxmlformats.schemas.drawingml.x2006
                    .spreadsheetDrawing.CTTwoCellAnchor anchor
                    : sheet.getDrawingPatriarch().getCTDrawing()
                            .getTwoCellAnchorList()) {
                rightmost = Math.max(
                        rightmost, (long) anchor.getTo().getCol());
            }
        }
        long first = rightmost < 0L ? 0L : rightmost + 2L;
        if (first >= org.apache.poi.ss.SpreadsheetVersion.EXCEL2007
                .getMaxColumns()) {
            throw new IllegalArgumentException(
                    "No columns remain for chart data area on sheet "
                            + sheet.getSheetName());
        }
        return (int) first;
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
