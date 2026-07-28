package com.xn.report.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.support.TestFixtures;
import com.xn.report.chart.ChartCategorySort;
import com.xn.report.chart.ChartNullHandling;
import com.xn.report.chart.ChartType;
import com.xn.report.chart.ChartLocator;
import java.util.Arrays;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.io.InputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelWorkbookWriterChartTest {

    @TempDir
    Path temporary;

    @Test
    void writesDatasetSheetsBeforeBindingNativeCharts() throws Exception {
        Path template = temporary.resolve("template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream output = Files.newOutputStream(template)) {
            workbook.createSheet("封面");
            workbook.write(output);
        }
        DatasetDefinition dataset = TestFixtures.dataset("centerEvents");
        dataset.setSheetName("中心-每月");
        ReportDefinition report = TestFixtures.report(dataset);
        ChartDefinition chart = TestFixtures.comboChartDefinition();
        chart.setMode(ChartDefinition.Mode.GENERATED_NATIVE);
        chart.setExcelSheet("中心-每月");
        chart.setCategorySort(
                com.xn.report.chart.ChartCategorySort.SOURCE);
        report.setCharts(Collections.singletonList(chart));
        DatasetContext context = DatasetContext.builder()
                .put(TestFixtures.centerEvents())
                .build();

        Path output = temporary.resolve("report.xlsx");
        new ExcelWorkbookWriter().write(
                template, output, report, context,
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            assertThat(workbook.getSheet("中心-每月")
                    .getDrawingPatriarch().getCharts()).hasSize(1);
            String xml = workbook.getSheet("中心-每月")
                    .getDrawingPatriarch().getCharts().get(0)
                    .getCTChart().xmlText();
            assertThat(xml).contains("'中心-每月'!$F$3:$F$5");
            assertThat(workbook.getSheet("中心-每月")
                    .getRow(0).getCell(5).getStringCellValue())
                    .startsWith("Chart data:");
        }
    }

    @Test
    void outputValidationRejectsChartFormulaOutsideItsDatasetSheet()
            throws Exception {
        Path template = temporary.resolve("validation-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream output = Files.newOutputStream(template)) {
            workbook.createSheet("封面");
            workbook.write(output);
        }
        DatasetDefinition dataset = TestFixtures.dataset("centerEvents");
        dataset.setSheetName("中心-每月");
        ReportDefinition report = TestFixtures.report(dataset);
        ChartDefinition chart = TestFixtures.comboChartDefinition();
        chart.setMode(ChartDefinition.Mode.GENERATED_NATIVE);
        chart.setExcelSheet("中心-每月");
        chart.setCategorySort(
                com.xn.report.chart.ChartCategorySort.SOURCE);
        report.setCharts(Collections.singletonList(chart));
        DatasetContext context = DatasetContext.builder()
                .put(TestFixtures.centerEvents()).build();
        Path output = temporary.resolve("corrupt.xlsx");
        new ExcelWorkbookWriter().write(
                template, output, report, context,
                Collections.<String, Object>emptyMap());
        try (InputStream input = Files.newInputStream(output);
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            workbook.getSheet("中心-每月").getDrawingPatriarch()
                    .getCharts().get(0).getCTChart().getPlotArea()
                    .getBarChartArray(0).getSerArray(0)
                    .getCat().getStrRef()
                    .setF("'Wrong'!$A$2:$A$4");
            try (OutputStream replacement =
                    Files.newOutputStream(output)) {
                workbook.write(replacement);
            }
        }

        assertThatThrownBy(() -> new ExcelOutputValidator().validate(
                output,
                Collections.singletonList(dataset),
                context,
                Collections.<String,
                        com.xn.report.config.definition.ExcelTableBinding>
                        emptyMap(),
                Collections.singletonList(chart)))
                .hasMessageContaining("category formula");
    }

    @Test
    void outputValidationRejectsStaleChartCachePointCount()
            throws Exception {
        Path template = temporary.resolve(
                "cache-validation-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream output = Files.newOutputStream(template)) {
            workbook.createSheet("Cover");
            workbook.write(output);
        }
        DatasetDefinition dataset =
                TestFixtures.dataset("centerEvents");
        dataset.setSheetName("中心-每月");
        ChartDefinition chart =
                TestFixtures.comboChartDefinition();
        chart.setExcelSheet("中心-每月");
        chart.setCategorySort(ChartCategorySort.SOURCE);
        ReportDefinition report = TestFixtures.report(dataset);
        report.setCharts(Collections.singletonList(chart));
        DatasetContext context = DatasetContext.builder()
                .put(TestFixtures.centerEvents()).build();
        Path output = temporary.resolve("stale-cache.xlsx");
        new ExcelWorkbookWriter().write(
                template, output, report, context,
                Collections.<String, Object>emptyMap());
        try (InputStream input = Files.newInputStream(output);
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            workbook.getSheet("中心-每月").getDrawingPatriarch()
                    .getCharts().get(0).getCTChart().getPlotArea()
                    .getBarChartArray(0).getSerArray(0)
                    .getCat().getStrRef().getStrCache()
                    .getPtCount().setVal(99);
            try (OutputStream replacement =
                    Files.newOutputStream(output)) {
                workbook.write(replacement);
            }
        }

        assertThatThrownBy(() ->
                new ExcelOutputValidator().validate(
                        output, Collections.singletonList(dataset),
                        context,
                        Collections.<String,
                                com.xn.report.config.definition
                                        .ExcelTableBinding>
                                emptyMap(),
                        Collections.singletonList(chart)))
                .hasMessageContaining("cache point count");
    }

    @Test
    void materializesGroupedSortedSkippedAndLegendOrderedDataOnSqlSheet()
            throws Exception {
        Path template = temporary.resolve("grouped-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream output = Files.newOutputStream(template)) {
            workbook.createSheet("Cover");
            workbook.write(output);
        }
        DatasetDefinition dataset = TestFixtures.dataset("grouped");
        dataset.setSheetName("SQL Data");
        DatasetResult result = DatasetResult.list("grouped", Arrays.asList(
                TestFixtures.row("team", "B", "month", "03",
                        "value", 30, "baseline", 3),
                TestFixtures.row("team", "A", "month", "02",
                        "value", null, "baseline", 2),
                TestFixtures.row("team", "B", "month", "01",
                        "value", 10, "baseline", 1),
                TestFixtures.row("team", "A", "month", "03",
                        "value", 31, "baseline", 3),
                TestFixtures.row("team", "B", "month", "02",
                        "value", 20, "baseline", 2),
                TestFixtures.row("team", "A", "month", "01",
                        "value", 11, "baseline", 1)));
        ChartSeriesDefinition value = new ChartSeriesDefinition();
        value.setField("value");
        value.setName("Value");
        value.setType(ChartType.LINE);
        value.setNullHandling(ChartNullHandling.SKIP_CATEGORY);
        value.setLegendOrder(1);
        ChartSeriesDefinition baseline = new ChartSeriesDefinition();
        baseline.setField("baseline");
        baseline.setName("Baseline");
        baseline.setType(ChartType.LINE);
        baseline.setLegendOrder(0);
        ChartDefinition chart = new ChartDefinition();
        chart.setId("groupedChart");
        chart.setTitle("Grouped");
        chart.setDataset("grouped");
        chart.setExcelSheet("SQL Data");
        chart.setCategoryField("month");
        chart.setGroupByField("team");
        chart.setCategorySort(ChartCategorySort.ASC);
        chart.setSeries(Arrays.asList(value, baseline));
        ReportDefinition report = TestFixtures.report(dataset);
        report.setCharts(Collections.singletonList(chart));
        Path output = temporary.resolve("grouped.xlsx");

        new ExcelWorkbookWriter().write(
                template, output, report,
                DatasetContext.builder().put(result).build(),
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            org.apache.poi.xssf.usermodel.XSSFSheet sheet =
                    workbook.getSheet("SQL Data");
            assertThat(workbook.isSheetHidden(
                    workbook.getSheetIndex(sheet))).isFalse();
            assertThat(sheet.getDrawingPatriarch().getCharts()).hasSize(2);
            org.apache.poi.xssf.usermodel.XSSFChart groupA =
                    new ChartLocator().findUnique(
                            workbook, "SQL Data",
                            "REPORT_CHART:groupedChart:A", null);
            org.apache.poi.xssf.usermodel.XSSFChart groupB =
                    new ChartLocator().findUnique(
                            workbook, "SQL Data",
                            "REPORT_CHART:groupedChart:B", null);
            assertDataArea(sheet, groupA,
                    Arrays.asList("01", "03"),
                    Arrays.asList("Baseline", "Value"));
            assertDataArea(sheet, groupB,
                    Arrays.asList("01", "02", "03"),
                    Arrays.asList("Baseline", "Value"));
        }
    }

    @Test
    void bindsEveryTemplateGroupToItsDeclaredLocatorAndDataArea()
            throws Exception {
        Path template = temporary.resolve(
                "grouped-native-template.xlsx");
        writeTwoChartTemplate(template);
        DatasetDefinition dataset =
                TestFixtures.dataset("templateGroups");
        dataset.setSheetName("SQL Data");
        DatasetResult result = DatasetResult.list(
                "templateGroups", Arrays.asList(
                        TestFixtures.row("team", "B",
                                "category", "02", "value", 20),
                        TestFixtures.row("team", "A",
                                "category", "02", "value", 2),
                        TestFixtures.row("team", "A",
                                "category", "01", "value", 1),
                        TestFixtures.row("team", "B",
                                "category", "01", "value", 10)));
        ChartSeriesDefinition series =
                new ChartSeriesDefinition();
        series.setField("value");
        series.setName("Value");
        series.setType(ChartType.LINE);
        ChartDefinition chart = new ChartDefinition();
        chart.setId("templateGroups");
        chart.setDataset("templateGroups");
        chart.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
        chart.setExcelSheet("SQL Data");
        chart.setCategoryField("category");
        chart.setGroupByField("team");
        chart.setCategorySort(ChartCategorySort.ASC);
        chart.setSeries(Collections.singletonList(series));
        com.xn.report.config.definition.TemplateChartLocatorDefinition a =
                new com.xn.report.config.definition
                        .TemplateChartLocatorDefinition();
        a.setGroupKey("A");
        a.setMarker("REPORT_CHART:group:A");
        com.xn.report.config.definition.TemplateChartLocatorDefinition b =
                new com.xn.report.config.definition
                        .TemplateChartLocatorDefinition();
        b.setGroupKey("B");
        b.setMarker("REPORT_CHART:group:B");
        chart.setTemplateChartLocators(Arrays.asList(a, b));
        ReportDefinition report = TestFixtures.report(dataset);
        report.setCharts(Collections.singletonList(chart));
        Path output = temporary.resolve(
                "grouped-native-output.xlsx");

        new ExcelWorkbookWriter().write(
                template, output, report,
                DatasetContext.builder().put(result).build(),
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet =
                    workbook.getSheet("SQL Data");
            assertThat(sheet.getDrawingPatriarch().getCharts())
                    .hasSize(2);
            assertSingleSeriesCategories(
                    new ChartLocator().findUnique(
                            workbook, "SQL Data",
                            "REPORT_CHART:group:A", null),
                    Arrays.asList("01", "02"));
            assertSingleSeriesCategories(
                    new ChartLocator().findUnique(
                            workbook, "SQL Data",
                            "REPORT_CHART:group:B", null),
                    Arrays.asList("01", "02"));
        }
    }

    private static void writeTwoChartTemplate(Path path)
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Cover");
            org.apache.poi.xssf.usermodel.XSSFSheet sheet =
                    workbook.createSheet("SQL Data");
            sheet.createRow(0).createCell(5)
                    .setCellValue("category");
            sheet.getRow(0).createCell(6).setCellValue("Value");
            sheet.createRow(1).createCell(5).setCellValue("01");
            sheet.getRow(1).createCell(6).setCellValue(1);
            sheet.createRow(2).createCell(5).setCellValue("02");
            sheet.getRow(2).createCell(6).setCellValue(2);
            ChartDefinition definition = new ChartDefinition();
            definition.setId("seed");
            definition.setDataset("seed");
            definition.setExcelSheet("SQL Data");
            definition.setAnchorRow(10);
            definition.setAnchorColumn(8);
            definition.setCategoryField("category");
            ChartSeriesDefinition series =
                    new ChartSeriesDefinition();
            series.setField("value");
            series.setName("Value");
            series.setType(ChartType.LINE);
            definition.setSeries(Collections.singletonList(series));
            com.xn.report.chart.ChartSeriesModel modelSeries =
                    new com.xn.report.chart.ChartSeriesModel(
                            "value", "Value", ChartType.LINE,
                            com.xn.report.chart.ChartAxis.PRIMARY,
                            null, null,
                            com.xn.report.chart.ChartLineStyle.SOLID,
                            java.math.BigDecimal.valueOf(2),
                            false,
                            com.xn.report.chart.ChartDataLabelMode.NONE,
                            null, ChartNullHandling.GAP, 0,
                            Arrays.asList(
                                    java.math.BigDecimal.ONE,
                                    java.math.BigDecimal.valueOf(2)),
                            Collections.<java.math.BigDecimal>emptyList());
            com.xn.report.chart.ChartModel model =
                    new com.xn.report.chart.ChartModel(
                            "seed", "Seed", "seed", null,
                            Arrays.asList("01", "02"),
                            Collections.singletonList(modelSeries),
                            com.xn.report.chart.LegendPosition.BOTTOM,
                            null, null, null, null,
                            com.xn.report.chart.ChartDataLabelMode.NONE,
                            Collections.<String>emptyList(),
                            800, 500,
                            com.xn.report.chart.ChartEmptyDataPolicy
                                    .OUTPUT_MESSAGE,
                            "empty");
            java.util.Map<String, Integer> columns =
                    new java.util.LinkedHashMap<String, Integer>();
            columns.put("category", 5);
            columns.put("value", 6);
            com.xn.report.chart.ChartFormulaRange range =
                    new com.xn.report.chart.ChartFormulaRange(
                            "SQL Data", 0, 1, 2, columns);
            com.xn.report.chart.GeneratedNativeChartWriter writer =
                    new com.xn.report.chart
                            .GeneratedNativeChartWriter();
            org.apache.poi.xssf.usermodel.XSSFChart first =
                    writer.write(workbook, definition, model, range);
            ChartLocator.setMarker(
                    first, "REPORT_CHART:group:A");
            org.apache.poi.xssf.usermodel.XSSFChart second =
                    writer.write(workbook, definition, model, range, 1);
            ChartLocator.setMarker(
                    second, "REPORT_CHART:group:B");
            try (OutputStream output =
                    Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }

    private static void assertSingleSeriesCategories(
            org.apache.poi.xssf.usermodel.XSSFChart chart,
            java.util.List<String> expected) {
        String namespaces =
                "declare namespace c='http://schemas.openxmlformats.org/"
                        + "drawingml/2006/chart'; ";
        java.util.List<String> values =
                new java.util.ArrayList<String>();
        for (org.apache.xmlbeans.XmlObject value
                : chart.getCTChart().selectPath(
                        namespaces
                                + ".//c:ser//c:cat"
                                + "//c:strCache/c:pt/c:v")) {
            try (org.apache.xmlbeans.XmlCursor cursor =
                    value.newCursor()) {
                values.add(cursor.getTextValue());
            }
        }
        assertThat(values).isEqualTo(expected);
    }

    private static void assertDataArea(
            org.apache.poi.xssf.usermodel.XSSFSheet sheet,
            org.apache.poi.xssf.usermodel.XSSFChart chart,
            java.util.List<String> categories,
            java.util.List<String> seriesHeaders) {
        String namespaces =
                "declare namespace c='http://schemas.openxmlformats.org/"
                        + "drawingml/2006/chart'; ";
        org.apache.xmlbeans.XmlObject[] series =
                chart.getCTChart().selectPath(namespaces + ".//c:ser");
        assertThat(series).hasSize(2);
        String categoryFormula = xmlText(
                series[0], namespaces + ".//c:cat//c:f");
        org.apache.poi.ss.util.AreaReference categoryArea =
                new org.apache.poi.ss.util.AreaReference(
                        categoryFormula,
                        org.apache.poi.ss.SpreadsheetVersion.EXCEL2007);
        int headerRow = categoryArea.getFirstCell().getRow() - 1;
        int categoryColumn = categoryArea.getFirstCell().getCol();
        assertThat(sheet.getRow(headerRow - 1)
                .getCell(categoryColumn).getStringCellValue())
                .startsWith("Chart data:");
        assertThat(sheet.getRow(headerRow)
                .getCell(categoryColumn + 1).getStringCellValue())
                .isEqualTo(seriesHeaders.get(0));
        assertThat(sheet.getRow(headerRow)
                .getCell(categoryColumn + 2).getStringCellValue())
                .isEqualTo(seriesHeaders.get(1));
        java.util.List<String> actual = new java.util.ArrayList<String>();
        for (int row = categoryArea.getFirstCell().getRow();
                row <= categoryArea.getLastCell().getRow(); row++) {
            actual.add(sheet.getRow(row).getCell(categoryColumn)
                    .getStringCellValue());
        }
        assertThat(actual).isEqualTo(categories);
        assertThat(categoryFormula).startsWith("'SQL Data'!");
    }

    private static String xmlText(
            org.apache.xmlbeans.XmlObject parent, String path) {
        org.apache.xmlbeans.XmlObject[] nodes =
                parent.selectPath(path);
        assertThat(nodes).hasSize(1);
        try (org.apache.xmlbeans.XmlCursor cursor =
                nodes[0].newCursor()) {
            return cursor.getTextValue();
        }
    }
}
