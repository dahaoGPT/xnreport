package com.xn.report.chart;

/**
 * 不支持的图表类型异常。
 * <p>
 * 当指定的图表类型在当前生成模式（如图像渲染或动态原生生成）下无法支持时抛出。
 * </p>
 */
public final class UnsupportedChartTypeException extends RuntimeException {

    public UnsupportedChartTypeException(ChartType type) {
        super("No image renderer supports chart type " + type);
    }

    public UnsupportedChartTypeException(String message) {
        super(message);
    }
}
