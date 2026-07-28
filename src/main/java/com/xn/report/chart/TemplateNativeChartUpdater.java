package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

/**
 * Rebinds an existing native template chart while leaving its plot types,
 * layout, theme and shape formatting untouched.
 */
public final class TemplateNativeChartUpdater {

    private static final String CHART_NS =
            "http://schemas.openxmlformats.org/"
            + "drawingml/2006/chart";
    private static final String NS =
            "declare namespace c='" + CHART_NS + "'; ";

    private final ChartLocator locator;

    public TemplateNativeChartUpdater() {
        this(new ChartLocator());
    }

    public TemplateNativeChartUpdater(ChartLocator locator) {
        if (locator == null) {
            throw new IllegalArgumentException(
                    "locator must not be null");
        }
        this.locator = locator;
    }

    public XSSFChart update(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartFormulaRange range) {
        if (workbook == null || definition == null || range == null) {
            throw new IllegalArgumentException(
                    "workbook, definition and range must not be null");
        }
        String targetSheet = definition.getExcelSheet() == null
                ? range.getSheetName() : definition.getExcelSheet();
        String marker = definition.getTemplateChartMarker();
        if (marker == null && definition.getTemplateChartIndex() == null) {
            marker = "REPORT_CHART:" + definition.getId();
        }
        XSSFChart chart = locator.findUnique(
                workbook, targetSheet, marker,
                definition.getTemplateChartIndex());
        XSSFSheet dataSheet = workbook.getSheet(range.getSheetName());
        if (dataSheet == null) {
            throw new IllegalArgumentException(
                    "Missing template chart data sheet: "
                            + range.getSheetName());
        }

        List<XDDFChartData.Series> existing = flatten(chart);
        if (!existing.isEmpty()
                && existing.size() != definition.getSeries().size()) {
            throw new IllegalArgumentException(
                    "Template chart series count " + existing.size()
                            + " does not match configured series count "
                            + definition.getSeries().size());
        }
        if (!existing.isEmpty()) {
            XDDFCategoryDataSource categories =
                    categorySource(
                            dataSheet, definition.getCategoryField(), range);
            for (int index = 0; index < existing.size(); index++) {
                ChartSeriesDefinition configured =
                        definition.getSeries().get(index);
                XDDFNumericalDataSource<Double> values =
                        numericalSource(
                                dataSheet, configured.getField(), range);
                existing.get(index).replaceData(categories, values);
                existing.get(index).setTitle(
                        configured.getName(),
                        new CellReference(
                                range.getSheetName(),
                                range.getHeaderRow(),
                                range.column(configured.getField()),
                                true, true));
            }
        }

        // This also covers special template-only plot types that XDDF cannot
        // construct from scratch. Removing stale caches is preferable to
        // retaining values for the old row count; Excel rebuilds them from
        // the direct worksheet formulas on open.
        rebindFormulaXml(chart, definition, range, existing.isEmpty());
        workbook.setForceFormulaRecalculation(true);
        return chart;
    }

    private static List<XDDFChartData.Series> flatten(
            XSSFChart chart) {
        List<XDDFChartData.Series> result =
                new ArrayList<XDDFChartData.Series>();
        for (XDDFChartData data : chart.getChartSeries()) {
            result.addAll(data.getSeries());
        }
        return result;
    }

    private static XDDFCategoryDataSource categorySource(
            XSSFSheet sheet,
            String field,
            ChartFormulaRange range) {
        String[] values = new String[range.getPointCount()];
        int column = range.column(field);
        for (int index = 0; index < values.length; index++) {
            Cell cell = cell(
                    sheet, range.getFirstDataRow() + index, column);
            values[index] = cell == null
                    ? null : display(cell);
        }
        return XDDFDataSourcesFactory.fromArray(
                values, range.formula(field), column);
    }

    private static XDDFNumericalDataSource<Double> numericalSource(
            XSSFSheet sheet,
            String field,
            ChartFormulaRange range) {
        Double[] values = new Double[range.getPointCount()];
        int column = range.column(field);
        for (int index = 0; index < values.length; index++) {
            Cell cell = cell(
                    sheet, range.getFirstDataRow() + index, column);
            values[index] = cell == null
                    || cell.getCellType() == CellType.BLANK
                    ? null : Double.valueOf(cell.getNumericCellValue());
        }
        return XDDFDataSourcesFactory.fromArray(
                values, range.formula(field), column);
    }

    private static Cell cell(
            XSSFSheet sheet, int rowIndex, int column) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(column);
    }

    private static String display(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return Double.toString(cell.getNumericCellValue());
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private static void rebindFormulaXml(
            XSSFChart chart,
            ChartDefinition definition,
            ChartFormulaRange range,
            boolean removeCaches) {
        XmlObject[] series = chart.getCTChart().selectPath(
                NS + ".//c:ser");
        if (series.length != definition.getSeries().size()) {
            throw new IllegalArgumentException(
                    "Template chart OOXML series count "
                            + series.length
                            + " does not match configured series count "
                            + definition.getSeries().size());
        }
        for (int index = 0; index < series.length; index++) {
            ChartSeriesDefinition configured =
                    definition.getSeries().get(index);
            setFormula(series[index],
                    ".//c:cat//c:f | .//c:xVal//c:f",
                    range.formula(definition.getCategoryField()));
            setFormula(series[index],
                    ".//c:val//c:f | .//c:yVal//c:f",
                    range.formula(configured.getField()));
            setFormula(series[index],
                    ".//c:tx//c:f",
                    range.titleFormula(configured.getField()));
            if (configured.getSizeField() != null) {
                setFormula(series[index],
                        ".//c:bubbleSize//c:f",
                        range.formula(configured.getSizeField()));
            }
            if (removeCaches) {
                remove(series[index], ".//c:strCache | .//c:numCache");
            }
        }
    }

    private static void setFormula(
            XmlObject parent, String path, String formula) {
        XmlObject[] formulas =
                parent.selectPath(NS + path);
        for (XmlObject item : formulas) {
            try (XmlCursor cursor = item.newCursor()) {
                cursor.setTextValue(formula);
            }
        }
    }

    private static void remove(
            XmlObject parent, String path) {
        XmlObject[] values = parent.selectPath(NS + path);
        for (XmlObject item : values) {
            try (XmlCursor cursor = item.newCursor()) {
                cursor.removeXml();
            }
        }
    }
}
