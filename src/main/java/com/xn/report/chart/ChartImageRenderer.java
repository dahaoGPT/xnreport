package com.xn.report.chart;

public interface ChartImageRenderer {

    boolean supports(ChartModel model);

    RenderedChart render(ChartModel model, ChartRenderOptions options);
}
