package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Compatibility facade using the terminology in the detailed design.
 */
public final class TemplateChartBinder {

    private final TemplateNativeChartUpdater delegate =
            new TemplateNativeChartUpdater();

    public XSSFChart bind(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartFormulaRange range) {
        return delegate.update(workbook, definition, range);
    }
}
