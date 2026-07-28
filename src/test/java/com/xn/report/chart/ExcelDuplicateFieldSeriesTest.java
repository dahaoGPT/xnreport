package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.junit.jupiter.api.Test;

class ExcelDuplicateFieldSeriesTest {

    private static final String NS =
            "declare namespace c='http://schemas.openxmlformats.org/"
                    + "drawingml/2006/chart'; ";

    @Test
    void generatedColumnAndLineKeepSeparateGapAndZeroColumnsAfterReopen()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("detail");
            DatasetDefinition dataset = dataset();
            DatasetResult result = duplicateFieldResult();
            ChartDefinition definition = duplicateFieldDefinition(
                    ChartType.COLUMN, ChartType.LINE);
            definition.getSeries().get(1).setAxis(
                    ChartAxis.SECONDARY);
            ChartModel model =
                    new ChartModelBuilder().build(definition, result);
            ChartFormulaRange range =
                    new ExcelChartDataAreaWriter().write(
                            workbook, dataset, result,
                            definition, model);
            new GeneratedNativeChartWriter().write(
                    workbook, definition, model, range);
            bytes = write(workbook);
        }

        try (XSSFWorkbook workbook = reopen(bytes)) {
            XSSFChart chart = new ChartLocator().findUnique(
                    workbook, "Data",
                    "REPORT_CHART:duplicate", null);
            XmlObject[] series =
                    chart.getCTChart().selectPath(NS + ".//c:ser");
            assertThat(series).hasSize(2);
            String firstFormula = formula(series[0]);
            String secondFormula = formula(series[1]);
            assertThat(firstFormula).isNotEqualTo(secondFormula);
            assertSeriesColumn(
                    workbook.getSheet("Data"),
                    firstFormula, "Gap", CellType.BLANK, null);
            assertSeriesColumn(
                    workbook.getSheet("Data"),
                    secondFormula, "Zero", CellType.NUMERIC, 0d);
        }
    }

    @Test
    void templateSameTypeSeriesUseStableIdentityAndLegendOrderAfterReopen()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = seedTwoLineTemplate()) {
            DatasetDefinition dataset = dataset();
            DatasetResult result = duplicateFieldResult();
            ChartDefinition definition = duplicateFieldDefinition(
                    ChartType.LINE, ChartType.LINE);
            definition.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
            definition.setTemplateChartMarker(
                    "REPORT_CHART:duplicate-template");
            definition.getSeries().get(0).setLegendOrder(1);
            definition.getSeries().get(1).setLegendOrder(0);
            new ExcelChartWriter().write(
                    workbook,
                    Collections.singletonList(definition),
                    Collections.singletonList(dataset),
                    DatasetContext.builder().put(result).build(),
                    Collections.emptyMap());
            bytes = write(workbook);
        }

        try (XSSFWorkbook workbook = reopen(bytes)) {
            XSSFChart chart = new ChartLocator().findUnique(
                    workbook, "Data",
                    "REPORT_CHART:duplicate-template", null);
            XmlObject zero = seriesForOrder(chart, 0);
            XmlObject gap = seriesForOrder(chart, 1);
            assertThat(header(workbook, titleFormula(zero)))
                    .isEqualTo("Zero");
            assertThat(header(workbook, titleFormula(gap)))
                    .isEqualTo("Gap");
            assertThat(formula(zero)).isNotEqualTo(formula(gap));
            assertThat(cacheValues(zero))
                    .containsExactly("0.0", "5.0");
            assertThat(cacheValues(gap))
                    .containsExactly("5.0");
        }
    }

    private static XSSFWorkbook seedTwoLineTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Data");
        sheet.createRow(0).createCell(0).setCellValue("category");
        sheet.getRow(0).createCell(1).setCellValue("First");
        sheet.getRow(0).createCell(2).setCellValue("Second");
        sheet.createRow(1).createCell(0).setCellValue("A");
        sheet.getRow(1).createCell(1).setCellValue(1);
        sheet.getRow(1).createCell(2).setCellValue(10);
        sheet.createRow(2).createCell(0).setCellValue("B");
        sheet.getRow(2).createCell(1).setCellValue(2);
        sheet.getRow(2).createCell(2).setCellValue(20);

        ChartDefinition seed = new ChartDefinition();
        seed.setId("seed");
        seed.setDataset("seed");
        seed.setExcelSheet("Data");
        seed.setCategoryField("category");
        seed.setSeries(Arrays.asList(
                series("first", "First", ChartType.LINE,
                        ChartNullHandling.GAP),
                series("second", "Second", ChartType.LINE,
                        ChartNullHandling.GAP)));
        DatasetResult seedResult = DatasetResult.list(
                "seed", Arrays.asList(
                        TestFixtures.row(
                                "category", "A",
                                "first", 1, "second", 10),
                        TestFixtures.row(
                                "category", "B",
                                "first", 2, "second", 20)));
        ChartModel seedModel =
                new ChartModelBuilder().build(seed, seedResult);
        Map<String, Integer> columns =
                new LinkedHashMap<String, Integer>();
        columns.put("category", 0);
        columns.put("first", 1);
        columns.put("second", 2);
        XSSFChart chart = new GeneratedNativeChartWriter().write(
                workbook, seed, seedModel,
                new ChartFormulaRange(
                        "Data", 0, 1, 2, columns));
        ChartLocator.setMarker(
                chart, "REPORT_CHART:duplicate-template");
        return workbook;
    }

    private static DatasetDefinition dataset() {
        DatasetDefinition dataset =
                TestFixtures.dataset("duplicate");
        dataset.setSheetName("Data");
        return dataset;
    }

    private static DatasetResult duplicateFieldResult() {
        return DatasetResult.list(
                "duplicate", Arrays.asList(
                        TestFixtures.row(
                                "category", "A", "value", null),
                        TestFixtures.row(
                                "category", "B", "value", 5)));
    }

    private static ChartDefinition duplicateFieldDefinition(
            ChartType firstType, ChartType secondType) {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("duplicate");
        definition.setDataset("duplicate");
        definition.setExcelSheet("Data");
        definition.setCategoryField("category");
        definition.setCategorySort(ChartCategorySort.SOURCE);
        definition.setSeries(Arrays.asList(
                series("value", "Gap", firstType,
                        ChartNullHandling.GAP),
                series("value", "Zero", secondType,
                        ChartNullHandling.ZERO)));
        return definition;
    }

    private static ChartSeriesDefinition series(
            String field,
            String name,
            ChartType type,
            ChartNullHandling nullHandling) {
        ChartSeriesDefinition series =
                new ChartSeriesDefinition();
        series.setField(field);
        series.setName(name);
        series.setType(type);
        series.setNullHandling(nullHandling);
        return series;
    }

    private static void assertSeriesColumn(
            XSSFSheet sheet,
            String formula,
            String expectedHeader,
            CellType expectedType,
            Double expectedValue) {
        AreaReference area = new AreaReference(
                formula, SpreadsheetVersion.EXCEL2007);
        int column = area.getFirstCell().getCol();
        int firstRow = area.getFirstCell().getRow();
        assertThat(sheet.getRow(firstRow - 1)
                .getCell(column).getStringCellValue())
                .isEqualTo(expectedHeader);
        Cell first = sheet.getRow(firstRow).getCell(
                column, org.apache.poi.ss.usermodel
                        .Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        assertThat(first.getCellType()).isEqualTo(expectedType);
        if (expectedValue != null) {
            assertThat(first.getNumericCellValue())
                    .isEqualTo(expectedValue.doubleValue());
        }
    }

    private static XmlObject seriesForOrder(
            XSSFChart chart, int expectedOrder) {
        for (XmlObject series
                : chart.getCTChart().selectPath(NS + ".//c:ser")) {
            XmlObject[] orders =
                    series.selectPath(NS + "./c:order");
            try (XmlCursor cursor = orders[0].newCursor()) {
                if (Integer.toString(expectedOrder).equals(
                        cursor.getAttributeText(
                                new QName("val")))) {
                    return series;
                }
            }
        }
        return null;
    }

    private static String formula(XmlObject series) {
        return text(series, ".//c:val//c:f");
    }

    private static String titleFormula(XmlObject series) {
        return text(series, ".//c:tx//c:f");
    }

    private static String text(
            XmlObject parent, String path) {
        XmlObject[] values =
                parent.selectPath(NS + path);
        assertThat(values).hasSize(1);
        try (XmlCursor cursor = values[0].newCursor()) {
            return cursor.getTextValue();
        }
    }

    private static String header(
            XSSFWorkbook workbook, String formula) {
        org.apache.poi.ss.util.CellReference reference =
                new org.apache.poi.ss.util.CellReference(formula);
        return workbook.getSheet(reference.getSheetName())
                .getRow(reference.getRow())
                .getCell(reference.getCol())
                .getStringCellValue();
    }

    private static List<String> cacheValues(
            XmlObject series) {
        java.util.ArrayList<String> values =
                new java.util.ArrayList<String>();
        for (XmlObject value : series.selectPath(
                NS + ".//c:val//c:numCache/c:pt/c:v")) {
            try (XmlCursor cursor = value.newCursor()) {
                values.add(cursor.getTextValue());
            }
        }
        return values;
    }

    private static byte[] write(XSSFWorkbook workbook)
            throws Exception {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    private static XSSFWorkbook reopen(byte[] bytes)
            throws Exception {
        return new XSSFWorkbook(
                new ByteArrayInputStream(bytes));
    }
}
