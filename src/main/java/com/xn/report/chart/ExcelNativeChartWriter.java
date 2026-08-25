package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 动态生成原生 Excel 图表兼容适配门面。
 * <p>
 * 对接详细设计说明书规范，内部委派至 {@link GeneratedNativeChartWriter}。
 * </p>
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
