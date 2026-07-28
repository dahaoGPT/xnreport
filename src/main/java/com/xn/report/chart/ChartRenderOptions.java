package com.xn.report.chart;

public final class ChartRenderOptions {

    public static final int MAX_WIDTH = 4000;
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
