package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.junit.jupiter.api.Test;

class TemplateNativeChartCacheTest {

    private static final String NS =
            "declare namespace c='http://schemas.openxmlformats.org/"
                    + "drawingml/2006/chart'; ";

    @Test
    void expandsTemplateCachesFromThreeToFourWithExactValues()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = stringTemplate()) {
            XSSFSheet sheet = workbook.getSheet("Data");
            sheet.createRow(4).createCell(0).setCellValue("D");
            sheet.getRow(4).createCell(1).setCellValue(40);
            new TemplateNativeChartUpdater().update(
                    workbook, oneSeriesDefinition(), range(4));
            bytes = write(workbook);
        }

        try (XSSFWorkbook workbook = reopen(bytes)) {
            XSSFChart chart = chart(workbook, "REPORT_CHART:simple");
            assertThat(pointCount(chart,
                    ".//c:cat//c:strCache/c:ptCount"))
                    .isEqualTo(4);
            assertThat(values(chart,
                    ".//c:cat//c:strCache/c:pt/c:v"))
                    .containsExactly("A", "B", "C", "D");
            assertThat(pointCount(chart,
                    ".//c:val//c:numCache/c:ptCount"))
                    .isEqualTo(4);
            assertThat(values(chart,
                    ".//c:val//c:numCache/c:pt/c:v"))
                    .containsExactly("10.0", "20.0", "30.0", "40.0");
        }
    }

    @Test
    void emptyRangeClearsOldPointsAndWritesZeroCounts()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = stringTemplate()) {
            new TemplateNativeChartUpdater().update(
                    workbook, oneSeriesDefinition(), range(0));
            bytes = write(workbook);
        }

        try (XSSFWorkbook workbook = reopen(bytes)) {
            XSSFChart chart = chart(workbook, "REPORT_CHART:simple");
            assertThat(pointCount(chart,
                    ".//c:cat//c:strCache/c:ptCount")).isZero();
            assertThat(pointCount(chart,
                    ".//c:val//c:numCache/c:ptCount")).isZero();
            assertThat(values(chart,
                    ".//c:cat//c:strCache/c:pt/c:v")).isEmpty();
            assertThat(values(chart,
                    ".//c:val//c:numCache/c:pt/c:v")).isEmpty();
        }
    }

    @Test
    void rebuildsNumericDateCategoriesAsNumCache()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = numericDateTemplate()) {
            XSSFSheet sheet = workbook.getSheet("Data");
            sheet.createRow(4).createCell(0)
                    .setCellValue(LocalDate.of(2026, 1, 4));
            sheet.getRow(4).createCell(1).setCellValue(40);
            new TemplateNativeChartUpdater().update(
                    workbook, oneSeriesDefinition(), range(4));
            bytes = write(workbook);
        }

        try (XSSFWorkbook workbook = reopen(bytes)) {
            XSSFChart chart = chart(workbook, "REPORT_CHART:simple");
            assertThat(pointCount(chart,
                    ".//c:cat//c:numCache/c:ptCount"))
                    .isEqualTo(4);
            assertThat(values(chart,
                    ".//c:cat//c:numCache/c:pt/c:v"))
                    .hasSize(4)
                    .allSatisfy(value ->
                            assertThat(Double.parseDouble(value))
                                    .isGreaterThan(40000d));
        }
    }

    @Test
    void bindsAndOrdersTemplateSeriesByChartModelLegendOrder()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = twoSeriesTemplate()) {
            ChartDefinition definition = twoSeriesDefinition();
            definition.getSeries().get(0).setLegendOrder(1);
            definition.getSeries().get(1).setLegendOrder(0);
            DatasetResult result = DatasetResult.list(
                    "series", Arrays.asList(
                            TestFixtures.row("category", "A",
                                    "first", 1, "second", 10),
                            TestFixtures.row("category", "B",
                                    "first", 2, "second", 20)));
            ChartModel model =
                    new ChartModelBuilder().build(definition, result);
            new TemplateNativeChartUpdater().update(
                    workbook, definition, model, twoSeriesRange());
            bytes = write(workbook);
        }

        try (XSSFWorkbook workbook = reopen(bytes)) {
            XSSFChart chart = chart(workbook, "REPORT_CHART:series");
            XmlObject[] series =
                    chart.getCTChart().selectPath(NS + ".//c:ser");
            assertThat(series).hasSize(2);
            assertThat(formulaForOrder(series, 0))
                    .contains("$C$2:$C$3");
            assertThat(formulaForOrder(series, 1))
                    .contains("$B$2:$B$3");
        }
    }

    private static XSSFWorkbook stringTemplate() {
        XSSFWorkbook workbook = baseWorkbook(false, false);
        new GeneratedNativeChartWriter().write(
                workbook, oneSeriesDefinition(), oneSeriesModel(),
                range(3));
        return workbook;
    }

    private static XSSFWorkbook numericDateTemplate() {
        XSSFWorkbook workbook = baseWorkbook(true, false);
        XSSFSheet sheet = workbook.getSheet("Data");
        XSSFClientAnchor anchor = new XSSFClientAnchor();
        anchor.setCol1(3);
        anchor.setRow1(0);
        anchor.setCol2(12);
        anchor.setRow2(18);
        XSSFChart chart = sheet.createDrawingPatriarch()
                .createChart(anchor);
        XDDFCategoryAxis categoryAxis =
                chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis valueAxis =
                chart.createValueAxis(AxisPosition.LEFT);
        categoryAxis.crossAxis(valueAxis);
        valueAxis.crossAxis(categoryAxis);
        XDDFChartData data = chart.createData(
                ChartTypes.LINE, categoryAxis, valueAxis);
        data.addSeries(
                XDDFDataSourcesFactory.fromNumericCellRange(
                        sheet, new CellRangeAddress(1, 3, 0, 0)),
                XDDFDataSourcesFactory.fromNumericCellRange(
                        sheet, new CellRangeAddress(1, 3, 1, 1)));
        chart.plot(data);
        ChartLocator.setMarker(chart, "REPORT_CHART:simple");
        return workbook;
    }

    private static XSSFWorkbook twoSeriesTemplate() {
        XSSFWorkbook workbook = baseWorkbook(false, true);
        ChartDefinition definition = twoSeriesDefinition();
        ChartModel model = new ChartModelBuilder().build(
                definition, DatasetResult.list(
                        "series", Arrays.asList(
                                TestFixtures.row("category", "A",
                                        "first", 1, "second", 10),
                                TestFixtures.row("category", "B",
                                        "first", 2, "second", 20))));
        new GeneratedNativeChartWriter().write(
                workbook, definition, model, twoSeriesRange());
        return workbook;
    }

    private static XSSFWorkbook baseWorkbook(
            boolean dates, boolean twoSeries) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Data");
        sheet.createRow(0).createCell(0).setCellValue("category");
        sheet.getRow(0).createCell(1)
                .setCellValue(twoSeries ? "First" : "Value");
        if (twoSeries) {
            sheet.getRow(0).createCell(2).setCellValue("Second");
        }
        int points = twoSeries ? 2 : 3;
        for (int index = 0; index < points; index++) {
            sheet.createRow(index + 1);
            if (dates) {
                sheet.getRow(index + 1).createCell(0)
                        .setCellValue(LocalDate.of(
                                2026, 1, index + 1));
            } else {
                sheet.getRow(index + 1).createCell(0)
                        .setCellValue(String.valueOf(
                                (char) ('A' + index)));
            }
            sheet.getRow(index + 1).createCell(1)
                    .setCellValue(twoSeries
                            ? index + 1 : (index + 1) * 10);
            if (twoSeries) {
                sheet.getRow(index + 1).createCell(2)
                        .setCellValue((index + 1) * 10);
            }
        }
        return workbook;
    }

    private static ChartDefinition oneSeriesDefinition() {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("simple");
        definition.setDataset("simple");
        definition.setExcelSheet("Data");
        definition.setTemplateChartMarker("REPORT_CHART:simple");
        definition.setCategoryField("category");
        ChartSeriesDefinition series = series(
                "value", "Value");
        definition.setSeries(Collections.singletonList(series));
        return definition;
    }

    private static ChartDefinition twoSeriesDefinition() {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("series");
        definition.setDataset("series");
        definition.setExcelSheet("Data");
        definition.setCategoryField("category");
        definition.setSeries(Arrays.asList(
                series("first", "First"),
                series("second", "Second")));
        return definition;
    }

    private static ChartSeriesDefinition series(
            String field, String name) {
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField(field);
        series.setName(name);
        series.setType(ChartType.LINE);
        return series;
    }

    private static ChartModel oneSeriesModel() {
        ChartSeriesModel series = new ChartSeriesModel(
                "value", "Value", ChartType.LINE,
                ChartAxis.PRIMARY, null, null,
                ChartLineStyle.SOLID, BigDecimal.valueOf(2),
                false, ChartDataLabelMode.NONE, null,
                ChartNullHandling.GAP, 0,
                Arrays.asList(
                        BigDecimal.TEN,
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(30)),
                Collections.<BigDecimal>emptyList());
        return new ChartModel(
                "simple", "Simple", "simple", null,
                Arrays.asList("A", "B", "C"),
                Collections.singletonList(series),
                LegendPosition.BOTTOM, null, null, null, null,
                ChartDataLabelMode.NONE,
                Collections.<String>emptyList(),
                800, 500, ChartEmptyDataPolicy.OUTPUT_MESSAGE, "empty");
    }

    private static ChartFormulaRange range(int points) {
        Map<String, Integer> columns =
                new LinkedHashMap<String, Integer>();
        columns.put("category", 0);
        columns.put("value", 1);
        return new ChartFormulaRange(
                "Data", 0, 1, points, columns);
    }

    private static ChartFormulaRange twoSeriesRange() {
        Map<String, Integer> columns =
                new LinkedHashMap<String, Integer>();
        columns.put("category", 0);
        columns.put("first", 1);
        columns.put("second", 2);
        return new ChartFormulaRange(
                "Data", 0, 1, 2, columns);
    }

    private static byte[] write(XSSFWorkbook workbook)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    private static XSSFWorkbook reopen(byte[] bytes)
            throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static XSSFChart chart(
            XSSFWorkbook workbook, String marker) {
        return new ChartLocator().findUnique(
                workbook, "Data", marker, null);
    }

    private static int pointCount(
            XSSFChart chart, String path) {
        XmlObject[] values =
                chart.getCTChart().selectPath(NS + path);
        assertThat(values).hasSize(1);
        try (XmlCursor cursor = values[0].newCursor()) {
            return Integer.parseInt(cursor.getAttributeText(
                    new QName("val")));
        }
    }

    private static List<String> values(
            XSSFChart chart, String path) {
        List<String> result = new ArrayList<String>();
        for (XmlObject value
                : chart.getCTChart().selectPath(NS + path)) {
            try (XmlCursor cursor = value.newCursor()) {
                result.add(cursor.getTextValue());
            }
        }
        return result;
    }

    private static String formulaForOrder(
            XmlObject[] series, int order) {
        for (XmlObject item : series) {
            XmlObject[] orderNodes =
                    item.selectPath(NS + "./c:order");
            try (XmlCursor cursor =
                    orderNodes[0].newCursor()) {
                if (Integer.toString(order).equals(
                        cursor.getAttributeText(
                                new QName("val")))) {
                    XmlObject[] formula = item.selectPath(
                            NS + ".//c:val//c:f");
                    try (XmlCursor formulaCursor =
                            formula[0].newCursor()) {
                        return formulaCursor.getTextValue();
                    }
                }
            }
        }
        return null;
    }
}
