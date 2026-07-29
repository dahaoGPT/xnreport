package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExcelChartDataAreaWriterTest {

    @Test
    void rejectsCompleteDataAreaColumnOverflowBeforeMutation()
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(10).createCell(16380)
                    .setCellValue("existing");
            DatasetDefinition dataset = TestFixtures.dataset("sort");
            dataset.setSheetName("Data");
            DatasetResult result = DatasetResult.list(
                    "sort", Collections.singletonList(
                            TestFixtures.row(
                                    "category", "A",
                                    "value", 1,
                                    "value2", 2)));
            ChartDefinition definition =
                    definition(ChartCategorySort.SOURCE,
                            Collections.<String>emptyList());
            ChartSeriesDefinition second =
                    new ChartSeriesDefinition();
            second.setField("value2");
            second.setName("Value 2");
            second.setType(ChartType.LINE);
            definition.setSeries(Arrays.asList(
                    definition.getSeries().get(0), second));
            ChartModel model = new ChartModelBuilder().build(
                    definition, result);
            int styles = workbook.getNumCellStyles();

            assertThatThrownBy(() ->
                    new ExcelChartDataAreaWriter().write(
                            workbook, dataset, result,
                            definition, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("columns");
            assertThat((Object) sheet.getRow(0)).isNull();
            assertThat((Object) sheet.getRow(1)).isNull();
            assertThat(workbook.getNumCellStyles()).isEqualTo(styles);
        }
    }

    @Test
    void rejectsDataAreaRowAndColumnIntegerOverflow() {
        assertThatCode(() ->
                ExcelChartBounds.validateDataArea(
                        16382L, 2L, 1048575L, 1L))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                ExcelChartBounds.validateDataArea(
                        0L, 2L, 2L, Integer.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows");
        assertThatThrownBy(() ->
                ExcelChartBounds.validateDataArea(
                        Long.MAX_VALUE, 2L, 2L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns");
    }

    @Test
    void writesTheSpecifiedChineseChartDataMarker() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("detail");
            DatasetDefinition dataset = TestFixtures.dataset("sort");
            dataset.setSheetName("Data");
            DatasetResult result = DatasetResult.list(
                    "sort", Collections.singletonList(
                            TestFixtures.row(
                                    "category", "A", "value", 1)));
            ChartDefinition definition =
                    definition(ChartCategorySort.SOURCE,
                            Collections.<String>emptyList());
            ChartModel model = new ChartModelBuilder().build(
                    definition, result);

            ChartFormulaRange range =
                    new ExcelChartDataAreaWriter().write(
                            workbook, dataset, result,
                            definition, model);

            assertThat(sheet.getRow(range.getHeaderRow() - 1)
                    .getCell(range.column("category"))
                    .getStringCellValue())
                    .isEqualTo("图表数据：sortChart");
        }
    }

    @Test
    void appendsTheGroupKeyToTheSpecifiedMarkerWithAColon()
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            DatasetDefinition dataset = TestFixtures.dataset("sort");
            dataset.setSheetName("Data");
            DatasetResult result = DatasetResult.list(
                    "sort", Collections.singletonList(
                            TestFixtures.row(
                                    "category", "A", "value", 1)));
            ChartDefinition definition =
                    definition(ChartCategorySort.SOURCE,
                            Collections.<String>emptyList());
            ChartModel base = new ChartModelBuilder().build(
                    definition, result);
            ChartModel grouped = new ChartModel(
                    base.getChartId(), base.getTitle(),
                    base.getDatasetId(), "华北",
                    base.getCategories(), base.getSeries(),
                    base.getLegendPosition(),
                    base.getPrimaryAxisMin(),
                    base.getPrimaryAxisMax(),
                    base.getSecondaryAxisMin(),
                    base.getSecondaryAxisMax(),
                    base.getDataLabelMode(),
                    base.getDataLabels(),
                    base.getWidthPixels(),
                    base.getHeightPixels(),
                    base.getEmptyDataPolicy(),
                    base.getEmptyMessage());

            ChartFormulaRange range =
                    new ExcelChartDataAreaWriter().write(
                            workbook, dataset, result,
                            definition, grouped);

            assertThat(sheet.getRow(range.getHeaderRow() - 1)
                    .getCell(range.column("category"))
                    .getStringCellValue())
                    .isEqualTo("图表数据：sortChart:华北");
        }
    }

    @ParameterizedTest
    @MethodSource("sortCases")
    void materializesEveryCategorySortMode(
            ChartCategorySort sort,
            List<String> explicit,
            List<String> expected) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("detail");
            DatasetDefinition dataset = TestFixtures.dataset("sort");
            dataset.setSheetName("Data");
            DatasetResult result = DatasetResult.list(
                    "sort", Arrays.asList(
                            TestFixtures.row(
                                    "category", "B", "value", 2),
                            TestFixtures.row(
                                    "category", "A", "value", 1),
                            TestFixtures.row(
                                    "category", "C", "value", 3)));
            ChartDefinition definition = definition(sort, explicit);
            ChartModel model = new ChartModelBuilder().build(
                    definition, result);

            ChartFormulaRange range =
                    new ExcelChartDataAreaWriter().write(
                            workbook, dataset, result,
                            definition, model);

            java.util.ArrayList<String> actual =
                    new java.util.ArrayList<String>();
            for (int index = 0;
                    index < range.getPointCount(); index++) {
                actual.add(sheet.getRow(
                        range.getFirstDataRow() + index)
                        .getCell(range.column("category"))
                        .getStringCellValue());
            }
            assertThat(actual).isEqualTo(expected);
            assertThat(range.formula("category"))
                    .startsWith("'Data'!");
        }
    }

    static Stream<Arguments> sortCases() {
        return Stream.of(
                Arguments.of(
                        ChartCategorySort.SOURCE,
                        Collections.emptyList(),
                        Arrays.asList("B", "A", "C")),
                Arguments.of(
                        ChartCategorySort.ASC,
                        Collections.emptyList(),
                        Arrays.asList("A", "B", "C")),
                Arguments.of(
                        ChartCategorySort.DESC,
                        Collections.emptyList(),
                        Arrays.asList("C", "B", "A")),
                Arguments.of(
                        ChartCategorySort.EXPLICIT,
                        Arrays.asList("C", "A", "B"),
                        Arrays.asList("C", "A", "B")));
    }

    private static ChartDefinition definition(
            ChartCategorySort sort, List<String> categories) {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("sortChart");
        definition.setDataset("sort");
        definition.setExcelSheet("Data");
        definition.setCategoryField("category");
        definition.setCategorySort(sort);
        definition.setCategories(categories);
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField("value");
        series.setName("Value");
        series.setType(ChartType.LINE);
        definition.setSeries(Collections.singletonList(series));
        return definition;
    }
}
