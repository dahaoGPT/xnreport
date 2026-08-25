package com.xn.report.chart;

/**
 * 图表离线图像渲染参数选项。
 * <p>
 * 控制生成图片的像素宽度（最大 4000）、高度（最大 2400）以及 DPI 分辨率（36 - 600）。
 * </p>
 */
public final class ChartRenderOptions {

    /** 支持的最大图像像素宽度。 */
    public static final int MAX_WIDTH = 4000;

    /** 支持的最大图像像素高度。 */
    public static final int MAX_HEIGHT = 2400;

    private final int widthPixels;
    private final int heightPixels;
    private final int dpi;

    public ChartRenderOptions(int widthPixels, int heightPixels, int dpi) {
        if (widthPixels <= 0 || widthPixels > MAX_WIDTH
                || heightPixels <= 0 || heightPixels > MAX_HEIGHT) {
            throw new IllegalArgumentException(
                    "Chart dimensions exceed supported bounds");
        }
        if (dpi < 36 || dpi > 600) {
            throw new IllegalArgumentException(
                    "Chart DPI must be between 36 and 600");
        }
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.dpi = dpi;
    }

    public int getWidthPixels() {
        return widthPixels;
    }

    public int getHeightPixels() {
        return heightPixels;
    }

    public int getDpi() {
        return dpi;
    }
}
