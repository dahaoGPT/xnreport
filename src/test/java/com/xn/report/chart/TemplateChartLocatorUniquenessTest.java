package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.TemplateChartLocatorDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TemplateChartLocatorUniquenessTest {

    @Test
    void markerAndIndexAliasesAreRejectedBeforeAnyDataAreaIsWritten()
            throws Exception {
        try (XSSFWorkbook workbook = seedTemplate()) {
            DatasetDefinition dataset =
                    TestFixtures.dataset("grouped");
            dataset.setSheetName("Data");
            DatasetResult result = DatasetResult.list(
                    "grouped", Arrays.asList(
                            TestFixtures.row(
                                    "team", "A",
                                    "category", "01", "value", 1),
                            TestFixtures.row(
                                    "team", "B",
                                    "category", "01", "value", 2)));
            ChartDefinition definition = groupedDefinition();

            assertThatThrownBy(() -> new ExcelChartWriter().write(
                    workbook,
                    Collections.singletonList(definition),
                    Collections.singletonList(dataset),
                    DatasetContext.builder().put(result).build(),
                    Collections.emptyMap()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(
                            "same template chart");
            assertThat(hasChartDataMarker(
                    workbook.getSheet("Data"))).isFalse();
        }
    }

    @Test
    void directTemplateUpdateAlsoPreflightsAllGroupedLocators()
            throws Exception {
        try (XSSFWorkbook workbook = seedTemplate()) {
            ChartDefinition definition = groupedDefinition();
            ChartModel model = new ChartModelBuilder().buildAll(
                    definition,
                    DatasetResult.list(
                            "grouped", Arrays.asList(
                                    TestFixtures.row(
                                            "team", "A",
                                            "category", "01",
                                            "value", 1),
                                    TestFixtures.row(
                                            "team", "B",
                                            "category", "01",
                                            "value", 2))))
                    .get(0);
            Map<String, Integer> columns =
                    new LinkedHashMap<String, Integer>();
            columns.put("category", 0);
            columns.put("value", 1);

            assertThatThrownBy(() ->
                    new TemplateNativeChartUpdater().update(
                            workbook, definition, model,
                            new ChartFormulaRange(
                                    "Data", 0, 1, 1, columns)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same template chart");
        }
    }

    private static ChartDefinition groupedDefinition() {
        ChartDefinition definition = oneSeriesDefinition();
        definition.setId("grouped");
        definition.setDataset("grouped");
        definition.setMode(
                ChartDefinition.Mode.TEMPLATE_NATIVE);
        definition.setGroupByField("team");
        TemplateChartLocatorDefinition marker =
                new TemplateChartLocatorDefinition();
        marker.setGroupKey("A");
        marker.setMarker("REPORT_CHART:seed");
        TemplateChartLocatorDefinition index =
                new TemplateChartLocatorDefinition();
        index.setGroupKey("B");
        index.setIndex(0);
        definition.setTemplateChartLocators(
                Arrays.asList(marker, index));
        return definition;
    }

    private static XSSFWorkbook seedTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Data");
        sheet.createRow(0).createCell(0)
                .setCellValue("category");
        sheet.getRow(0).createCell(1)
                .setCellValue("Value");
        sheet.createRow(1).createCell(0)
                .setCellValue("01");
        sheet.getRow(1).createCell(1).setCellValue(1);
        ChartDefinition definition =
                oneSeriesDefinition();
        ChartModel model = new ChartModelBuilder().build(
                definition,
                DatasetResult.list(
                        "seed",
                        Collections.singletonList(
                                TestFixtures.row(
                                        "category", "01",
                                        "value", 1))));
        Map<String, Integer> columns =
                new LinkedHashMap<String, Integer>();
        columns.put("category", 0);
        columns.put("value", 1);
        XSSFChart chart =
                new GeneratedNativeChartWriter().write(
                        workbook, definition, model,
                        new ChartFormulaRange(
                                "Data", 0, 1, 1, columns));
        ChartLocator.setMarker(chart, "REPORT_CHART:seed");
        return workbook;
    }

    private static ChartDefinition oneSeriesDefinition() {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("seed");
        definition.setDataset("seed");
        definition.setExcelSheet("Data");
        definition.setCategoryField("category");
        ChartSeriesDefinition series =
                new ChartSeriesDefinition();
        series.setField("value");
        series.setName("Value");
        series.setType(ChartType.LINE);
        definition.setSeries(
                Collections.singletonList(series));
        return definition;
    }

    private static boolean hasChartDataMarker(
            XSSFSheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING
                        && cell.getStringCellValue()
                                .startsWith("图表数据：")) {
                    return true;
                }
            }
        }
        return false;
    }
}
