package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.ChartDefinition;
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
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

class GeneratedNativeChartWriterTest {

    @ParameterizedTest
    @MethodSource("nativeTypes")
    void mapsEveryGeneratedTypeToItsNativeOoxmlElement(
            ChartType type, String element) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("x");
            sheet.getRow(0).createCell(1).setCellValue("y");
            sheet.createRow(1).createCell(0).setCellValue(1);
            sheet.getRow(1).createCell(1).setCellValue(2);
            sheet.createRow(2).createCell(0).setCellValue(2);
            sheet.getRow(2).createCell(1).setCellValue(3);
            Map<String, Integer> columns =
                    new LinkedHashMap<String, Integer>();
            columns.put("x", 0);
            columns.put("y", 1);
            ChartFormulaRange range =
                    new ChartFormulaRange("Data", 0, 1, 2, columns);

            com.xn.report.config.definition.ChartSeriesDefinition configured =
                    new com.xn.report.config.definition.ChartSeriesDefinition();
            configured.setField("y");
            configured.setName("Y");
            configured.setType(type);
            if (type.isStacked()) {
                configured.setStackGroup("s");
            }
            ChartDefinition definition = new ChartDefinition();
            definition.setId("c-" + type);
            definition.setDataset("d");
            definition.setCategoryField("x");
            definition.setExcelSheet("Data");
            definition.setSeries(Collections.singletonList(configured));
            ChartSeriesModel series = new ChartSeriesModel(
                    "y", "Y", type, ChartAxis.PRIMARY,
                    type.isStacked() ? "s" : null,
                    "#4472C4", ChartLineStyle.SOLID,
                    BigDecimal.valueOf(2), type == ChartType.SCATTER,
                    ChartDataLabelMode.NONE, null,
                    ChartNullHandling.GAP, 0,
                    Arrays.asList(
                            new BigDecimal("2"),
                            new BigDecimal("3")),
                    Collections.<BigDecimal>emptyList());
            ChartModel model = new ChartModel(
                    definition.getId(), type.name(), "d", null,
                    Arrays.asList("1", "2"),
                    Collections.singletonList(series),
                    LegendPosition.BOTTOM, null, null, null, null,
                    ChartDataLabelMode.NONE,
                    Collections.<String>emptyList(),
                    800, 500, ChartEmptyDataPolicy.OUTPUT_MESSAGE,
                    "empty");

            XSSFChart chart = new GeneratedNativeChartWriter()
                    .write(workbook, definition, model, range);

            assertThat(chart.getCTChart().xmlText()).contains(element);
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> nativeTypes() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.COLUMN, "barChart"),
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.STACKED_BAR, "barChart"),
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.LINE, "lineChart"),
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.AREA, "areaChart"),
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.PIE, "pieChart"),
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.DOUGHNUT, "doughnutChart"),
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.SCATTER, "scatterChart"),
                org.junit.jupiter.params.provider.Arguments.of(
                        ChartType.RADAR, "radarChart"));
    }

    @Test
    void createsEditableStackedColumnAndSecondaryLineUsingVisibleDatasetSheet()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            DatasetDefinition dataset = TestFixtures.dataset("centerEvents");
            dataset.setSheetName("中心-每月");
            DatasetResult result = TestFixtures.centerEvents();
            new ExcelDatasetSheetWriter().write(workbook, dataset, result);

            ChartDefinition definition = TestFixtures.comboChartDefinition();
            definition.setMode(ChartDefinition.Mode.GENERATED_NATIVE);
            definition.setExcelSheet("中心-每月");
            ChartFormulaRange range = new ChartRangeResolver()
                    .resolve(workbook, dataset, result, null, definition);
            XSSFChart chart = new GeneratedNativeChartWriter()
                    .write(workbook, definition,
                            TestFixtures.comboChartModel(), range);

            assertThat(chart).isNotNull();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            bytes = output.toByteArray();
        }

        try (XSSFWorkbook reopened = new XSSFWorkbook(
                new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = reopened.getSheet("中心-每月");
            assertThat(reopened.getSheetVisibility(
                    reopened.getSheetIndex(sheet)))
                    .isEqualTo(SheetVisibility.VISIBLE);
            assertThat(reopened.getSheet("图表数据")).isNull();
            assertThat(sheet.getDrawingPatriarch().getCharts()).hasSize(1);

            String xml = sheet.getDrawingPatriarch().getCharts().get(0)
                    .getCTChart().xmlText();
            assertThat(xml).contains("barChart", "lineChart");
            assertThat(xml).contains("stacked");
            assertThat(xml).contains("overlap");
            assertThat(xml).contains("'中心-每月'!$A$2:$A$4");
            assertThat(xml).contains("'中心-每月'!$B$2:$B$4");
            assertThat(xml).contains("'中心-每月'!$C$2:$C$4");
            assertThat(xml).contains("'中心-每月'!$D$2:$D$4");
            assertThat(xml).contains("'中心-每月'!$B$1");
            assertThat(xml).contains("'中心-每月'!$D$1");
            org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea plot =
                    sheet.getDrawingPatriarch().getCharts().get(0)
                            .getCTChart().getPlotArea();
            assertThat(plot.getBarChartList()).hasSize(1);
            assertThat(plot.getLineChartList()).hasSize(1);
            assertThat(plot.getCatAxList()).hasSize(2);
            assertThat(plot.getValAxList()).hasSize(2);
            java.util.Set<Long> axisIds =
                    new java.util.LinkedHashSet<Long>();
            plot.getCatAxList().forEach(
                    axis -> axisIds.add(axis.getAxId().getVal()));
            plot.getValAxList().forEach(
                    axis -> axisIds.add(axis.getAxId().getVal()));
            assertThat(axisIds).hasSize(4);
        }
    }

    @Test
    void writesLegalDirectReferencesAndZeroPointCachesForEmptyData()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("month");
            sheet.getRow(0).createCell(1).setCellValue("value");
            Map<String, Integer> columns =
                    new LinkedHashMap<String, Integer>();
            columns.put("month", 0);
            columns.put("value", 1);
            ChartFormulaRange range =
                    new ChartFormulaRange("Data", 0, 1, 0, columns);
            ChartDefinition definition = new ChartDefinition();
            definition.setId("empty");
            definition.setDataset("empty");
            definition.setExcelSheet("Data");
            definition.setCategoryField("month");
            com.xn.report.config.definition.ChartSeriesDefinition configured =
                    new com.xn.report.config.definition.ChartSeriesDefinition();
            configured.setField("value");
            configured.setName("Value");
            configured.setType(ChartType.LINE);
            definition.setSeries(Collections.singletonList(configured));
            ChartSeriesModel series = new ChartSeriesModel(
                    "value", "Value", ChartType.LINE,
                    ChartAxis.PRIMARY, null, null,
                    ChartLineStyle.SOLID, BigDecimal.valueOf(2),
                    false, ChartDataLabelMode.NONE, null,
                    ChartNullHandling.GAP, 0,
                    Collections.<BigDecimal>emptyList(),
                    Collections.<BigDecimal>emptyList());
            ChartModel model = new ChartModel(
                    "empty", "Empty", "empty", null,
                    Collections.<String>emptyList(),
                    Collections.singletonList(series),
                    LegendPosition.BOTTOM, null, null, null, null,
                    ChartDataLabelMode.NONE,
                    Collections.<String>emptyList(),
                    800, 500, ChartEmptyDataPolicy.OUTPUT_MESSAGE,
                    "empty");
            new GeneratedNativeChartWriter().write(
                    workbook, definition, model, range);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            bytes = output.toByteArray();
        }

        try (XSSFWorkbook reopened = new XSSFWorkbook(
                new ByteArrayInputStream(bytes))) {
            String xml = reopened.getSheet("Data")
                    .getDrawingPatriarch().getCharts().get(0)
                    .getCTChart().xmlText();
            assertThat(xml).contains("'Data'!$A$2:$A$2");
            assertThat(xml).contains("'Data'!$B$2:$B$2");
            assertThat(xml).contains("ptCount");
            assertThat(xml).contains("val=\"0\"");
        }
    }

    @Test
    void rejectsGeneratedStockInsteadOfSilentlyChangingItsType() {
        ChartDefinition definition = TestFixtures.comboChartDefinition();
        definition.getSeries().get(0).setType(ChartType.STOCK);
        definition.setMode(ChartDefinition.Mode.GENERATED_NATIVE);

        assertThatThrownBy(() -> new GeneratedNativeChartWriter()
                .write(new XSSFWorkbook(), definition,
                        new ChartModelBuilder().build(
                                definition, TestFixtures.centerEvents()),
                        ChartFormulaRange.empty("中心-每月")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("TEMPLATE_NATIVE");
    }
}
