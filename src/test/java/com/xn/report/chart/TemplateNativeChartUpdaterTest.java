package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.excel.ExcelDatasetSheetWriter;
import com.xn.report.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TemplateNativeChartUpdaterTest {

    @Test
    void locatesMarkedTemplateChartAndUpdatesRangesWithoutChangingTypes()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = templateWorkbook()) {
            DatasetDefinition dataset = TestFixtures.dataset("centerEvents");
            dataset.setSheetName("中心-每月");
            DatasetResult expanded = DatasetResult.list(
                    "centerEvents", Arrays.asList(
                            TestFixtures.centerEvents().list().get(0),
                            TestFixtures.centerEvents().list().get(1),
                            TestFixtures.centerEvents().list().get(2),
                            TestFixtures.row("month", "2026年4月",
                                    "uncertain", 3, "certain", 1,
                                    "baseline", 9)));
            new ExcelDatasetSheetWriter().write(
                    workbook, dataset, expanded);

            ChartDefinition definition = TestFixtures.comboChartDefinition();
            definition.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
            definition.setExcelSheet("中心-每月");
            definition.setTemplateChartMarker(
                    "REPORT_CHART:centerEventChart");
            ChartFormulaRange range = new ChartRangeResolver()
                    .resolve(workbook, dataset, expanded, null, definition);
            new TemplateNativeChartUpdater().update(
                    workbook, definition, range);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            bytes = output.toByteArray();
        }

        try (XSSFWorkbook reopened = new XSSFWorkbook(
                new ByteArrayInputStream(bytes))) {
            XSSFChart chart = new ChartLocator().findUnique(
                    reopened, "中心-每月",
                    "REPORT_CHART:centerEventChart", null);
            String xml = chart.getCTChart().xmlText();
            assertThat(xml).contains("barChart", "lineChart");
            assertThat(xml).contains("'中心-每月'!$A$2:$A$5");
            assertThat(xml).contains("'中心-每月'!$B$2:$B$5");
            assertThat(xml).contains("'中心-每月'!$D$2:$D$5");
            assertThat(xml).contains("ptCount");
        }
    }

    @Test
    void rejectsMissingAndAmbiguousTemplateMarkers() throws Exception {
        try (XSSFWorkbook workbook = templateWorkbook()) {
            ChartLocator locator = new ChartLocator();
            assertThatThrownBy(() -> locator.findUnique(
                    workbook, "中心-每月", "REPORT_CHART:missing", null))
                    .hasMessageContaining("0");

            XSSFSheet sheet = workbook.getSheet("中心-每月");
            XSSFChart original = sheet.getDrawingPatriarch().getCharts().get(0);
            XSSFChart duplicate = sheet.getDrawingPatriarch().importChart(original);
            ChartLocator.setMarker(
                    duplicate, "REPORT_CHART:centerEventChart");
            assertThatThrownBy(() -> locator.findUnique(
                    workbook, "中心-每月",
                    "REPORT_CHART:centerEventChart", null))
                    .hasMessageContaining("multiple");
        }
    }

    @Test
    void preservesSameTypePhysicalAxesWhenLegendOrderReordersSeries()
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("x");
            sheet.getRow(0).createCell(1).setCellValue("Primary");
            sheet.getRow(0).createCell(2).setCellValue("Secondary");
            for (int index = 0; index < 2; index++) {
                sheet.createRow(index + 1).createCell(0)
                        .setCellValue(index + 1);
                sheet.getRow(index + 1).createCell(1)
                        .setCellValue(10 + index);
                sheet.getRow(index + 1).createCell(2)
                        .setCellValue(20 + index);
            }
            Map<String, Integer> columns =
                    new LinkedHashMap<String, Integer>();
            columns.put("x", 0);
            columns.put("primary", 1);
            columns.put("secondary", 2);
            ChartFormulaRange physicalRange =
                    new ChartFormulaRange(
                            "Data", 0, 1, 2, columns,
                            Arrays.asList(2, 1),
                            Arrays.asList(null, null));

            ChartSeriesDefinition secondary =
                    lineDefinition(
                            "secondary", "Secondary",
                            ChartAxis.SECONDARY, 0);
            ChartSeriesDefinition primary =
                    lineDefinition(
                            "primary", "Primary",
                            ChartAxis.PRIMARY, 1);
            ChartDefinition templateDefinition =
                    chartDefinition(
                            Arrays.asList(secondary, primary),
                            ChartDefinition.Mode.GENERATED_NATIVE);
            ChartModel templateModel = chartModel(Arrays.asList(
                    lineModel(
                            "secondary", "Secondary",
                            ChartAxis.SECONDARY, 0, 0,
                            new BigDecimal("20")),
                    lineModel(
                            "primary", "Primary",
                            ChartAxis.PRIMARY, 1, 1,
                            new BigDecimal("10"))));
            XSSFChart chart = new GeneratedNativeChartWriter().write(
                    workbook, templateDefinition,
                    templateModel, physicalRange);
            ChartLocator.setMarker(chart, "REPORT_CHART:sameType");

            ChartSeriesDefinition configuredPrimary =
                    lineDefinition(
                            "primary", "Primary",
                            ChartAxis.PRIMARY, 1);
            ChartSeriesDefinition configuredSecondary =
                    lineDefinition(
                            "secondary", "Secondary",
                            ChartAxis.SECONDARY, 0);
            ChartDefinition target = chartDefinition(
                    Arrays.asList(
                            configuredPrimary,
                            configuredSecondary),
                    ChartDefinition.Mode.TEMPLATE_NATIVE);
            target.setTemplateChartMarker("REPORT_CHART:sameType");
            ChartModel targetModel = chartModel(Arrays.asList(
                    lineModel(
                            "secondary", "Secondary",
                            ChartAxis.SECONDARY, 0, 1,
                            new BigDecimal("20")),
                    lineModel(
                            "primary", "Primary",
                            ChartAxis.PRIMARY, 1, 0,
                            new BigDecimal("10"))));
            ChartFormulaRange targetRange =
                    new ChartFormulaRange(
                            "Data", 0, 1, 2, columns,
                            Arrays.asList(2, 1),
                            Arrays.asList(null, null));

            new TemplateNativeChartUpdater().update(
                    workbook, target, targetModel, targetRange);

            org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea plot =
                    chart.getCTChart().getPlotArea();
            long rightAxis = plot.getValAxList().stream()
                    .filter(axis -> "r".equals(
                            axis.getAxPos().getVal().toString()))
                    .findFirst().get().getAxId().getVal();
            long leftAxis = plot.getValAxList().stream()
                    .filter(axis -> "l".equals(
                            axis.getAxPos().getVal().toString()))
                    .findFirst().get().getAxId().getVal();
            org.openxmlformats.schemas.drawingml.x2006.chart.CTLineChart
                    secondaryPlot = plot.getLineChartList().stream()
                            .filter(item -> item.getAxIdList().stream()
                                    .anyMatch(axis ->
                                            axis.getVal() == rightAxis))
                            .findFirst().get();
            org.openxmlformats.schemas.drawingml.x2006.chart.CTLineChart
                    primaryPlot = plot.getLineChartList().stream()
                            .filter(item -> item.getAxIdList().stream()
                                    .anyMatch(axis ->
                                            axis.getVal() == leftAxis))
                            .findFirst().get();
            assertThat(secondaryPlot.getSerList().get(0)
                    .getVal().getNumRef().getF())
                    .isEqualTo("'Data'!$C$2:$C$3");
            assertThat(primaryPlot.getSerList().get(0)
                    .getVal().getNumRef().getF())
                    .isEqualTo("'Data'!$B$2:$B$3");
        }
    }

    private static ChartSeriesDefinition lineDefinition(
            String field, String name, ChartAxis axis,
            int legendOrder) {
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField(field);
        series.setName(name);
        series.setType(ChartType.LINE);
        series.setAxis(axis);
        series.setLegendOrder(legendOrder);
        return series;
    }

    private static ChartDefinition chartDefinition(
            java.util.List<ChartSeriesDefinition> series,
            ChartDefinition.Mode mode) {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("sameType");
        definition.setDataset("d");
        definition.setCategoryField("x");
        definition.setExcelSheet("Data");
        definition.setMode(mode);
        definition.setSeries(series);
        return definition;
    }

    private static ChartSeriesModel lineModel(
            String field, String name, ChartAxis axis,
            int legendOrder, int sourceIndex,
            BigDecimal firstValue) {
        return new ChartSeriesModel(
                field, name, ChartType.LINE, axis,
                null, null, ChartLineStyle.SOLID,
                BigDecimal.valueOf(2), false,
                ChartDataLabelMode.NONE, null,
                ChartNullHandling.GAP, legendOrder, sourceIndex,
                Arrays.asList(
                        firstValue,
                        firstValue.add(BigDecimal.ONE)),
                Collections.<BigDecimal>emptyList());
    }

    private static ChartModel chartModel(
            java.util.List<ChartSeriesModel> series) {
        return new ChartModel(
                "sameType", "Same type", "d", null,
                Arrays.asList("1", "2"), series,
                LegendPosition.BOTTOM, null, null, null, null,
                ChartDataLabelMode.NONE,
                Collections.<String>emptyList(),
                800, 500, ChartEmptyDataPolicy.OUTPUT_MESSAGE,
                "empty");
    }

    private XSSFWorkbook templateWorkbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        DatasetDefinition dataset = TestFixtures.dataset("centerEvents");
        dataset.setSheetName("中心-每月");
        DatasetResult result = TestFixtures.centerEvents();
        new ExcelDatasetSheetWriter().write(workbook, dataset, result);
        ChartDefinition definition = TestFixtures.comboChartDefinition();
        definition.setMode(ChartDefinition.Mode.GENERATED_NATIVE);
        definition.setExcelSheet("中心-每月");
        ChartFormulaRange range = new ChartRangeResolver()
                .resolve(workbook, dataset, result, null, definition);
        XSSFChart chart = new GeneratedNativeChartWriter().write(
                workbook, definition,
                TestFixtures.comboChartModel(), range);
        ChartLocator.setMarker(
                chart, "REPORT_CHART:centerEventChart");
        return workbook;
    }
}
