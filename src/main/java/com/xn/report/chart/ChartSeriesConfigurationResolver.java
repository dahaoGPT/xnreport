package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;

/**
 * 系列模型到原始配置定义的溯源反解器。
 * <p>
 * 将渲染阶段的 {@link ChartSeriesModel} 基于 sourceIndex 或序号准确反向匹配回原始 {@link ChartSeriesDefinition}。
 * </p>
 */
public final class ChartSeriesConfigurationResolver {

    private ChartSeriesConfigurationResolver() {
    }

    /**
     * 根据系列模型溯源匹配其原始配置。
     *
     * @param definition 图表配置定义
     * @param model 系列数据模型
     * @param ordinal 物理系列序号
     * @return 匹配的 ChartSeriesDefinition
     */
    public static ChartSeriesDefinition resolve(
            ChartDefinition definition,
            ChartSeriesModel model,
            int ordinal) {
        if (definition == null || model == null) {
            throw new IllegalArgumentException(
                    "Chart definition and series model must not be null");
        }
        int index = model.getSourceIndex() >= 0
                ? model.getSourceIndex() : ordinal;
        if (index < 0 || index >= definition.getSeries().size()) {
            throw new IllegalArgumentException(
                    "Chart series source index is invalid: " + index);
        }
        ChartSeriesDefinition configured =
                definition.getSeries().get(index);
        if (configured.getField() == null
                || !configured.getField().equalsIgnoreCase(
                        model.getField())
                || configured.getType() != model.getType()) {
            throw new IllegalArgumentException(
                    "Chart series source identity mismatch at index "
                            + index + ": " + model.getField()
                            + "/" + model.getType());
        }
        return configured;
    }
}
