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
import java.util.Arrays;
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
