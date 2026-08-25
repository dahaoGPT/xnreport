package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 模板图表数据绑定门面。
 * <p>
 * 对应详细设计说明书规范，内部委派至 {@link TemplateNativeChartUpdater}。
 * </p>
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
