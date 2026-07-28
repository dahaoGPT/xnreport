package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Compatibility facade using the terminology in the detailed design.
 */
public final class ExcelNativeChartWriter {

    private final GeneratedNativeChartWriter delegate =
            new GeneratedNativeChartWriter();

    public XSSFChart write(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartModel model,
            ChartFormulaRange range) {
        return delegate.write(workbook, definition, model, range);
    }
}
