package com.xn.report.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.support.TestFixtures;
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
            assertThat(xml).contains("'中心-每月'!$A$2:$A$4");
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
}
