package com.xn.report.excel;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.chart.ChartFormulaRange;
import com.xn.report.chart.ChartLocator;
import com.xn.report.chart.ChartRangeResolver;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

public final class ExcelOutputValidator {

    public void validate(
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context) throws IOException {
        validate(
                output, definitions, context,
                Collections.<String, ExcelTableBinding>emptyMap(),
                Collections.<ChartDefinition>emptyList());
    }

    public void validate(
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context,
            Map<String, ExcelTableBinding> bindings) throws IOException {
        validate(output, definitions, context, bindings,
                Collections.<ChartDefinition>emptyList());
    }

    public void validate(
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context,
            Map<String, ExcelTableBinding> bindings,
            List<ChartDefinition> charts) throws IOException {
        try (InputStream stream = Files.newInputStream(output);
                XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            validateUniqueTableNames(workbook);
            for (DatasetDefinition definition : definitions) {
                DatasetResult result = context.get(definition.getId());
                XSSFSheet sheet = workbook.getSheet(
                        definition.getSheetName());
                if (sheet == null) {
                    throw new IllegalStateException(
                            "Missing dataset sheet: "
                                    + definition.getSheetName());
                }
                if (workbook.getSheetVisibility(
                        workbook.getSheetIndex(sheet))
                        != SheetVisibility.VISIBLE) {
                    throw new IllegalStateException(
                            "Dataset sheet is not visible: "
                                    + definition.getSheetName());
                }
                ExcelTableBinding binding =
                        bindings.get(definition.getId());
                int startRow = binding == null
                        ? 0 : binding.getStartRow().intValue();
                XSSFTable table = targetTable(
                        sheet, binding, startRow,
                        definition.getSheetName());
                int expectedLastRow =
                        startRow
                                + ExcelDatasetSheetWriter.rows(result).size();
                int expectedLastColumn =
                        ExcelDatasetSheetWriter.fields(
                                definition, result, binding).size() - 1;
                if (table.getArea().getFirstCell().getRow()
                                != startRow
                        || table.getArea().getFirstCell().getCol() != 0
                        || table.getArea().getLastCell().getRow()
                                != expectedLastRow
                        || table.getArea().getLastCell().getCol()
                                != expectedLastColumn) {
                    throw new IllegalStateException(
                            "Dataset table range is invalid: "
                                    + definition.getSheetName());
                }
            }
            validateCharts(
                    workbook, definitions, context, bindings,
                    charts == null
                            ? Collections.<ChartDefinition>emptyList()
                            : charts);
        }
    }

    private static void validateCharts(
            XSSFWorkbook workbook,
            List<DatasetDefinition> definitions,
            DatasetContext context,
            Map<String, ExcelTableBinding> bindings,
            List<ChartDefinition> charts) {
        Map<String, DatasetDefinition> datasets =
                new java.util.LinkedHashMap<String, DatasetDefinition>();
        for (DatasetDefinition definition : definitions) {
            datasets.put(definition.getId(), definition);
        }
        ChartRangeResolver ranges = new ChartRangeResolver();
        ChartLocator locator = new ChartLocator();
        for (ChartDefinition definition : charts) {
            DatasetDefinition dataset =
                    datasets.get(definition.getDataset());
            if (dataset == null) {
                throw new IllegalStateException(
                        "Missing chart dataset during output validation: "
                                + definition.getDataset());
            }
            ChartFormulaRange range = ranges.resolve(
                    workbook, dataset,
                    context.get(dataset.getId()),
                    bindings.get(dataset.getId()), definition);
            String targetSheet = definition.getExcelSheet() == null
                    ? range.getSheetName()
                    : definition.getExcelSheet();
            String marker = definition.getTemplateChartMarker();
            if (marker == null
                    && definition.getTemplateChartIndex() == null) {
                marker = "REPORT_CHART:" + definition.getId();
            }
            XSSFChart chart = locator.findUnique(
                    workbook, targetSheet, marker,
                    definition.getTemplateChartIndex());
            validateChartFormulas(chart, definition, range);
        }
    }

    private static void validateChartFormulas(
            XSSFChart chart,
            ChartDefinition definition,
            ChartFormulaRange range) {
        String namespaces =
                "declare namespace c='http://schemas.openxmlformats.org/"
                + "drawingml/2006/chart'; ";
        XmlObject[] series = chart.getCTChart().selectPath(
                namespaces + ".//c:ser");
        if (series.length != definition.getSeries().size()) {
            throw new IllegalStateException(
                    "Chart series count is invalid: "
                            + definition.getId());
        }
        for (int index = 0; index < series.length; index++) {
            String category = firstText(
                    series[index], namespaces,
                    ".//c:cat//c:f | .//c:xVal//c:f");
            String expectedCategory =
                    range.formula(definition.getCategoryField());
            if (!expectedCategory.equals(category)) {
                throw new IllegalStateException(
                        "Chart category formula is invalid for "
                                + definition.getId() + ": " + category);
            }
            ChartSeriesDefinition configured =
                    definition.getSeries().get(index);
            String values = firstText(
                    series[index], namespaces,
                    ".//c:val//c:f | .//c:yVal//c:f");
            String expectedValues =
                    range.formula(configured.getField());
            if (!expectedValues.equals(values)) {
                throw new IllegalStateException(
                        "Chart series formula is invalid for "
                                + definition.getId() + "/"
                                + configured.getName() + ": " + values);
            }
            String title = firstText(
                    series[index], namespaces, ".//c:tx//c:f");
            if (!range.titleFormula(configured.getField())
                    .equals(title)) {
                throw new IllegalStateException(
                        "Chart series title formula is invalid for "
                                + definition.getId() + "/"
                                + configured.getName());
            }
            if (configured.getSizeField() != null) {
                String size = firstText(
                        series[index], namespaces,
                        ".//c:bubbleSize//c:f");
                if (!range.formula(configured.getSizeField())
                        .equals(size)) {
                    throw new IllegalStateException(
                            "Chart bubble size formula is invalid for "
                                    + definition.getId() + "/"
                                    + configured.getName());
                }
            }
        }
    }

    private static String firstText(
            XmlObject parent, String namespaces, String path) {
        XmlObject[] values =
                parent.selectPath(namespaces + path);
        if (values.length != 1) {
            return null;
        }
        try (XmlCursor cursor = values[0].newCursor()) {
            return cursor.getTextValue();
        }
    }

    private static XSSFTable targetTable(
            XSSFSheet sheet,
            ExcelTableBinding binding,
            int startRow,
            String sheetName) {
        XSSFTable match = null;
        String configuredName = binding == null
                ? null : binding.getTable();
        for (XSSFTable candidate : sheet.getTables()) {
            boolean candidateMatches;
            if (configuredName != null
                    && !configuredName.trim().isEmpty()) {
                candidateMatches = configuredName.equalsIgnoreCase(
                        candidate.getName());
            } else {
                candidateMatches =
                        candidate.getArea().getFirstCell().getRow()
                                == startRow
                        && candidate.getArea().getFirstCell().getCol()
                                == 0;
            }
            if (!candidateMatches) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException(
                        "Multiple dataset tables match sheet: "
                                + sheetName);
            }
            match = candidate;
        }
        if (match == null) {
            throw new IllegalStateException(
                    "Missing dataset table: " + sheetName);
        }
        return match;
    }

    private static void validateUniqueTableNames(
            XSSFWorkbook workbook) {
        Set<String> names = new LinkedHashSet<String>();
        for (int sheetIndex = 0;
                sheetIndex < workbook.getNumberOfSheets();
                sheetIndex++) {
            XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
            for (XSSFTable table : sheet.getTables()) {
                String normalized =
                        ExcelTableNameRules.normalized(
                                table.getName());
                if (!names.add(normalized)) {
                    throw new IllegalStateException(
                            "Duplicate Excel table name: "
                                    + table.getName());
                }
            }
        }
    }
}
