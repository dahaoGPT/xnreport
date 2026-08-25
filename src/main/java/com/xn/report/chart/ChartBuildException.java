package com.xn.report.chart;

/**
 * 图表模型构建与数据校验异常。
 */
public final class ChartBuildException extends RuntimeException {

    public ChartBuildException(String message) {
        super(message);
    }

    public ChartBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
