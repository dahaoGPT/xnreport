package com.xn.report.excel;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.chart.ChartFormulaRange;
import com.xn.report.chart.ChartLocator;
import com.xn.report.chart.ChartRangeResolver;
import com.xn.report.chart.ChartModel;
import com.xn.report.chart.ChartModelBuilder;
import com.xn.report.chart.ChartSeriesModel;
import com.xn.report.chart.ChartSeriesConfigurationResolver;
import com.xn.report.chart.ExcelChartDataAreaWriter;
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
import org.apache.poi.ss.formula.SheetNameFormatter;
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
        ChartLocator locator = new ChartLocator();
        ChartModelBuilder models = new ChartModelBuilder();
        ExcelChartDataAreaWriter dataAreas =
                new ExcelChartDataAreaWriter();
        for (ChartDefinition definition : charts) {
            DatasetDefinition dataset =
                    datasets.get(definition.getDataset());
            if (dataset == null) {
                throw new IllegalStateException(
                        "Missing chart dataset during output validation: "
                                + definition.getDataset());
            }
            List<ChartModel> chartModels = models.buildAll(
                    definition, context.get(dataset.getId()));
            for (ChartModel model : chartModels) {
                ChartFormulaRange range = dataAreas.findRange(
                        workbook, dataset, definition, model);
                String targetSheet =
                        definition.getExcelSheet() == null
                                ? range.getSheetName()
                                : definition.getExcelSheet();
                LocatorValue value = locator(
                        definition, model);
                XSSFChart chart = locator.findUnique(
                        workbook, targetSheet,
                        value.marker, value.index);
                validateChartFormulas(
                        chart, definition, model, range);
            }
        }
    }

    private static void validateChartFormulas(
            XSSFChart chart,
            ChartDefinition definition,
            ChartModel model,
            ChartFormulaRange range) {
        String namespaces =
                "declare namespace c='http://schemas.openxmlformats.org/"
                + "drawingml/2006/chart'; ";
        XmlObject[] series = chart.getCTChart().selectPath(
                namespaces + ".//c:ser");
        if (series.length != model.getSeries().size()) {
            throw new IllegalStateException(
                    "Chart series count is invalid: "
                            + definition.getId());
        }
        Set<Integer> matched = new LinkedHashSet<Integer>();
        for (int index = 0; index < series.length; index++) {
            ChartSeriesModel configured =
                    model.getSeries().get(index);
            String expectedTitle = range.seriesTitleFormula(
                    index, configured.getField());
            XmlObject actualSeries = uniqueSeriesByTitle(
                    series, namespaces, expectedTitle, matched,
                    definition.getId(), configured.getName());
            String category = firstText(
                    actualSeries, namespaces,
                    ".//c:cat//c:f | .//c:xVal//c:f");
            String expectedCategory =
                    range.formula(definition.getCategoryField());
            if (!formulasEquivalent(expectedCategory, category)) {
                throw new IllegalStateException(
                        "Chart category formula is invalid for "
                                + definition.getId() + ": " + category);
            }
            String values = firstText(
                    actualSeries, namespaces,
                    ".//c:val//c:f | .//c:yVal//c:f");
            String expectedValues =
                    range.seriesFormula(
                            index, configured.getField());
            if (!formulasEquivalent(expectedValues, values)) {
                throw new IllegalStateException(
                        "Chart series formula is invalid for "
                                + definition.getId() + "/"
                                + configured.getName() + ": " + values);
            }
            String title = firstText(
                    actualSeries, namespaces, ".//c:tx//c:f");
            if (!formulasEquivalent(expectedTitle, title)) {
                throw new IllegalStateException(
                        "Chart series title formula is invalid for "
                                + definition.getId() + "/"
                                + configured.getName());
            }
            String sizeField =
                    ChartSeriesConfigurationResolver.resolve(
                            definition, configured, index)
                            .getSizeField();
            if (sizeField != null) {
                String size = firstText(
                        actualSeries, namespaces,
                        ".//c:bubbleSize//c:f");
                if (!formulasEquivalent(
                        range.sizeFormula(index, sizeField), size)) {
                    throw new IllegalStateException(
                            "Chart bubble size formula is invalid for "
                                    + definition.getId() + "/"
                                    + configured.getName());
                }
            }
            validateCachePointCount(
                    actualSeries, namespaces,
                    ".//c:cat//c:strCache/c:ptCount"
                            + " | .//c:cat//c:numCache/c:ptCount"
                            + " | .//c:xVal//c:strCache/c:ptCount"
                            + " | .//c:xVal//c:numCache/c:ptCount",
                    range.getPointCount(), true,
                    definition.getId() + " category");
            validateCachePointCount(
                    actualSeries, namespaces,
                    ".//c:val//c:numCache/c:ptCount"
                            + " | .//c:yVal//c:numCache/c:ptCount",
                    range.getPointCount(), false,
                    definition.getId() + "/"
                            + configured.getName());
            if (sizeField != null) {
                validateCachePointCount(
                        actualSeries, namespaces,
                        ".//c:bubbleSize//c:numCache/c:ptCount",
                        range.getPointCount(), false,
                        definition.getId() + " bubble size");
            }
        }
    }

    private static XmlObject uniqueSeriesByTitle(
            XmlObject[] series,
            String namespaces,
            String expectedTitle,
            Set<Integer> matched,
            String chartId,
            String seriesName) {
        Integer match = null;
        for (int index = 0; index < series.length; index++) {
            if (!matched.contains(Integer.valueOf(index))
                    && formulasEquivalent(expectedTitle, firstText(
                            series[index], namespaces,
                            ".//c:tx//c:f"))) {
                if (match != null) {
                    throw new IllegalStateException(
                            "Chart series title formula is ambiguous for "
                                    + chartId + "/" + seriesName);
                }
                match = Integer.valueOf(index);
            }
        }
        if (match == null) {
            java.util.List<String> actualTitles =
                    new java.util.ArrayList<String>();
            for (XmlObject candidate : series) {
                actualTitles.add(firstText(
                        candidate, namespaces, ".//c:tx//c:f"));
            }
            throw new IllegalStateException(
                    "Chart series title formula is invalid for "
                            + chartId + "/" + seriesName
                            + ": expected " + expectedTitle
                            + " but found " + actualTitles);
        }
        matched.add(match);
        return series[match.intValue()];
    }

    static boolean formulasEquivalent(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        return canonicalFormula(expected).equals(canonicalFormula(actual));
    }

    private static String canonicalFormula(String formula) {
        if (formula.length() >= 3 && formula.charAt(0) == '\'') {
            StringBuilder sheet = new StringBuilder();
            for (int index = 1; index < formula.length(); index++) {
                char character = formula.charAt(index);
                if (character != '\'') {
                    sheet.append(character);
                    continue;
                }
                if (index + 1 < formula.length()
                        && formula.charAt(index + 1) == '\'') {
                    sheet.append('\'');
                    index++;
                    continue;
                }
                if (index + 1 < formula.length()
                        && formula.charAt(index + 1) == '!') {
                    return isSafeUnquotedSheetName(sheet)
                            ? sheet.toString() + formula.substring(index + 1)
                            : formula;
                }
                return formula;
            }
            return formula;
        }
        return formula;
    }

    private static boolean isSafeUnquotedSheetName(
            CharSequence sheetName) {
        String value = sheetName.toString();
        return !value.isEmpty()
                && SheetNameFormatter.format(value).equals(value);
    }

    private static void validateCachePointCount(
            XmlObject series,
            String namespaces,
            String countPath,
            int expected,
            boolean requireEveryPoint,
            String description) {
        XmlObject[] counts =
                series.selectPath(namespaces + countPath);
        if (counts.length != 1) {
            throw new IllegalStateException(
                    "Chart cache is missing or ambiguous for "
                            + description);
        }
        long actual;
        try (XmlCursor cursor = counts[0].newCursor()) {
            String value = cursor.getAttributeText(
                    new javax.xml.namespace.QName("val"));
            actual = value == null
                    ? -1L : Long.parseLong(value);
        }
        if (actual != expected) {
            throw new IllegalStateException(
                    "Chart cache point count is invalid for "
                            + description + ": " + actual
                            + " expected " + expected);
        }
        XmlObject[] points = series.selectPath(
                namespaces
                        + countPath.replace(
                                "/c:ptCount", "/c:pt"));
        if (expected == 0 && points.length != 0) {
            throw new IllegalStateException(
                    "Chart empty cache retains stale points for "
                            + description);
        }
        if (requireEveryPoint && points.length != expected) {
            throw new IllegalStateException(
                    "Chart category cache points are invalid for "
                            + description);
        }
    }

    private static LocatorValue locator(
            ChartDefinition definition, ChartModel model) {
        if (definition.getMode()
                == ChartDefinition.Mode.TEMPLATE_NATIVE
                && definition.getGroupByField() != null) {
            for (com.xn.report.config.definition
                    .TemplateChartLocatorDefinition item
                    : definition.getTemplateChartLocators()) {
                if (model.getGroupKey().equals(item.getGroupKey())) {
                    return new LocatorValue(
                            item.getMarker(), item.getIndex());
                }
            }
            throw new IllegalStateException(
                    "Missing template chart locator for group "
                            + model.getGroupKey());
        }
        if (definition.getMode()
                == ChartDefinition.Mode.TEMPLATE_NATIVE) {
            return new LocatorValue(
                    definition.getTemplateChartMarker(),
                    definition.getTemplateChartIndex());
        }
        return new LocatorValue(
                "REPORT_CHART:" + definition.getId()
                        + (model.getGroupKey() == null
                        ? "" : ":" + model.getGroupKey()),
                null);
    }

    private static final class LocatorValue {
        private final String marker;
        private final Integer index;

        private LocatorValue(String marker, Integer index) {
            this.marker = marker;
            this.index = index;
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
