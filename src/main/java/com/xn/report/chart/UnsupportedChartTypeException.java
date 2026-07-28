package com.xn.report.chart;

public final class UnsupportedChartTypeException extends RuntimeException {

    public UnsupportedChartTypeException(ChartType type) {
        super("No image renderer supports chart type " + type);
    }

    public UnsupportedChartTypeException(String message) {
        super(message);
    }
}
