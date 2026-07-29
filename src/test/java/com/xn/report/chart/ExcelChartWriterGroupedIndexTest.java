package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.excel.ExcelDatasetSheetWriter;
import com.xn.report.support.TestFixtures;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelChartWriterGroupedIndexTest {

    @Test
    void buildsOneTypedCategoryIndexForAllGroupedModels()
            throws Exception {
        DatasetDefinition dataset =
                TestFixtures.dataset("grouped");
        dataset.setSheetName("Data");
        DatasetResult result = DatasetResult.list(
                "grouped", Arrays.asList(
                        TestFixtures.row(
                                "team", "A",
                                "category", 101, "value", 1),
                        TestFixtures.row(
                                "team", "B",
                                "category", 202, "value", 2),
                        TestFixtures.row(
                                "team", "C",
                                "category", 303, "value", 3)));
        ChartDefinition chart = groupedChart();
        AtomicInteger builds = new AtomicInteger();
        ChartSourceCategoryIndex.Factory factory =
                (definition, source) -> {
                    builds.incrementAndGet();
                    return ChartSourceCategoryIndex.build(
                            definition, source);
                };

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            new ExcelDatasetSheetWriter().write(
                    workbook, dataset, result);
            new ExcelChartWriter(
                    new ChartModelBuilder(),
                    new ChartRangeResolver(),
                    new GeneratedNativeChartWriter(),
                    new TemplateNativeChartUpdater(),
                    new ExcelChartDataAreaWriter(),
                    factory).write(
                            workbook,
                            Collections.singletonList(chart),
                            Collections.singletonList(dataset),
                            DatasetContext.builder()
                                    .put(result).build(),
                            Collections.emptyMap());

            assertThat(builds).hasValue(1);
            XSSFSheet sheet = workbook.getSheet("Data");
            assertThat(sheet.getDrawingPatriarch().getCharts())
                    .hasSize(3);
            java.util.List<Double> categories =
                    new java.util.ArrayList<Double>();
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING
                            && cell.getStringCellValue()
                                    .contains("grouped:")) {
                        categories.add(sheet.getRow(
                                row.getRowNum() + 2)
                                .getCell(cell.getColumnIndex())
                                .getNumericCellValue());
                    }
                }
            }
            assertThat(categories)
                    .containsExactly(101D, 202D, 303D);
        }
    }

    private static ChartDefinition groupedChart() {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("grouped");
        definition.setDataset("grouped");
        definition.setExcelSheet("Data");
        definition.setCategoryField("category");
        definition.setGroupByField("team");
        definition.setCategorySort(ChartCategorySort.SOURCE);
        ChartSeriesDefinition series =
                new ChartSeriesDefinition();
        series.setField("value");
        series.setName("Value");
        series.setType(ChartType.LINE);
        definition.setSeries(Collections.singletonList(series));
        return definition;
    }
}
