package com.xn.report.chart;

/**
 * 图表离线静态图像渲染器接口。
 * <p>
 * 将 {@link ChartModel} 渲染为图片文件（如 PNG），主要用于 Word 文档插图等场景。
 * </p>
 */
public interface ChartImageRenderer {

    /**
     * 判断当前渲染引擎是否支持该图表模型的类型及配置组合。
     */
    boolean supports(ChartModel model);

    /**
     * 执行图表图像渲染。
     *
     * @param model 图表数据模型
     * @param options 分辨率与宽高选项
     * @return 渲染生成的图片产物 RenderedChart
     */
    RenderedChart render(ChartModel model, ChartRenderOptions options);
}
