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

    @Test
    void rejectsAnchorIntegerOverflowBeforeCreatingDrawing()
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Data");
            ChartDefinition definition =
                    TestFixtures.comboChartDefinition();
            definition.setExcelSheet("Data");
            definition.setAnchorRow(Integer.MAX_VALUE);
            definition.setAnchorHeightRows(Integer.MAX_VALUE);
            ChartModel model = TestFixtures.comboChartModel();
            ChartFormulaRange range = new ChartFormulaRange(
                    "Data", 0, 1, model.getCategories().size(),
                    Collections.<String, Integer>emptyMap());

            assertThatThrownBy(() ->
                    new GeneratedNativeChartWriter().write(
                            workbook, definition, model, range))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bounds");
            assertThat(workbook.getSheet("Data")
                    .getDrawingPatriarch()).isNull();
        }
    }

    @Test
    void rejectsAnchorBeyondExcelColumnLimitBeforeCreatingDrawing()
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Data");
            ChartDefinition definition =
                    TestFixtures.comboChartDefinition();
            definition.setExcelSheet("Data");
            definition.setAnchorColumn(16380);
            definition.setAnchorWidthColumns(10);
            ChartModel model = TestFixtures.comboChartModel();
            ChartFormulaRange range = new ChartFormulaRange(
                    "Data", 0, 1, model.getCategories().size(),
                    Collections.<String, Integer>emptyMap());

            assertThatThrownBy(() ->
                    new GeneratedNativeChartWriter().write(
                            workbook, definition, model, range))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bounds");
            assertThat(workbook.getSheet("Data")
                    .getDrawingPatriarch()).isNull();
        }
    }

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
    void createsEditableBubbleSeriesOnPrimaryAndSecondaryAxes()
            throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("x");
            sheet.getRow(0).createCell(1).setCellValue("primaryY");
            sheet.getRow(0).createCell(2).setCellValue("primarySize");
            sheet.getRow(0).createCell(3).setCellValue("secondaryY");
            sheet.getRow(0).createCell(4).setCellValue("secondarySize");
            for (int index = 0; index < 2; index++) {
                sheet.createRow(index + 1).createCell(0)
                        .setCellValue(index + 1);
                sheet.getRow(index + 1).createCell(1)
                        .setCellValue(10 + index);
                sheet.getRow(index + 1).createCell(2)
                        .setCellValue(3 + index);
                sheet.getRow(index + 1).createCell(3)
                        .setCellValue(20 + index);
                sheet.getRow(index + 1).createCell(4)
                        .setCellValue(5 + index);
            }
            Map<String, Integer> columns =
                    new LinkedHashMap<String, Integer>();
            columns.put("x", 0);
            columns.put("primaryY", 1);
            columns.put("primarySize", 2);
            columns.put("secondaryY", 3);
            columns.put("secondarySize", 4);
            ChartFormulaRange range = new ChartFormulaRange(
                    "Data", 0, 1, 2, columns,
                    Arrays.asList(1, 3),
                    Arrays.asList(2, 4));

            ChartDefinition definition = new ChartDefinition();
            definition.setId("bubble");
            definition.setDataset("d");
            definition.setCategoryField("x");
            definition.setExcelSheet("Data");
            com.xn.report.config.definition.ChartSeriesDefinition primary =
                    bubbleDefinition(
                            "primaryY", "primarySize",
                            "Primary", ChartAxis.PRIMARY);
            com.xn.report.config.definition.ChartSeriesDefinition secondary =
                    bubbleDefinition(
                            "secondaryY", "secondarySize",
                            "Secondary", ChartAxis.SECONDARY);
            definition.setSeries(Arrays.asList(primary, secondary));
            ChartSeriesModel primaryModel = bubbleModel(
                    "primaryY", "Primary", ChartAxis.PRIMARY,
                    Arrays.asList(
                            new BigDecimal("10"),
                            new BigDecimal("11")),
                    Arrays.asList(
                            new BigDecimal("3"),
                            new BigDecimal("4")));
            ChartSeriesModel secondaryModel = bubbleModel(
                    "secondaryY", "Secondary", ChartAxis.SECONDARY,
                    Arrays.asList(
                            new BigDecimal("20"),
                            new BigDecimal("21")),
                    Arrays.asList(
                            new BigDecimal("5"),
                            new BigDecimal("6")));
            ChartModel model = new ChartModel(
                    "bubble", "Bubble", "d", null,
                    Arrays.asList("1", "2"),
                    Arrays.asList(primaryModel, secondaryModel),
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
            assertThat(reopened.getNumberOfSheets()).isEqualTo(1);
            assertThat(reopened.getSheetVisibility(0))
                    .isEqualTo(SheetVisibility.VISIBLE);
            org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea plot =
                    reopened.getSheet("Data").getDrawingPatriarch()
                            .getCharts().get(0).getCTChart().getPlotArea();
            assertThat(plot.getBubbleChartList()).hasSize(2);
            assertThat(plot.getValAxList()).hasSize(4);
            String xml = plot.xmlText();
            assertThat(xml).contains(
                    "'Data'!$A$2:$A$3",
                    "'Data'!$B$2:$B$3",
                    "'Data'!$C$2:$C$3",
                    "'Data'!$D$2:$D$3",
                    "'Data'!$E$2:$E$3",
                    "Data!$B$1",
                    "Data!$D$1");
            assertThat(xml).contains("bubbleSize", "ptCount");
            java.util.Set<Long> firstAxes =
                    new java.util.LinkedHashSet<Long>();
            plot.getBubbleChartList().get(0).getAxIdList()
                    .forEach(axis -> firstAxes.add(axis.getVal()));
            java.util.Set<Long> secondAxes =
                    new java.util.LinkedHashSet<Long>();
            plot.getBubbleChartList().get(1).getAxIdList()
                    .forEach(axis -> secondAxes.add(axis.getVal()));
            assertThat(firstAxes).hasSize(2);
            assertThat(secondAxes).hasSize(2);
            assertThat(firstAxes).doesNotContainAnyElementsOf(secondAxes);
        }
    }

    private static com.xn.report.config.definition.ChartSeriesDefinition
            bubbleDefinition(
                    String field, String sizeField,
                    String name, ChartAxis axis) {
        com.xn.report.config.definition.ChartSeriesDefinition series =
                new com.xn.report.config.definition.ChartSeriesDefinition();
        series.setField(field);
        series.setSizeField(sizeField);
        series.setName(name);
        series.setType(ChartType.BUBBLE);
        series.setAxis(axis);
        return series;
    }

    private static ChartSeriesModel bubbleModel(
            String field, String name, ChartAxis axis,
            java.util.List<BigDecimal> values,
            java.util.List<BigDecimal> sizes) {
        return new ChartSeriesModel(
                field, name, ChartType.BUBBLE, axis,
                null, "#4472C4", ChartLineStyle.SOLID,
                BigDecimal.valueOf(2), false,
                ChartDataLabelMode.NONE, null,
                ChartNullHandling.GAP, 0,
                values, sizes);
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
