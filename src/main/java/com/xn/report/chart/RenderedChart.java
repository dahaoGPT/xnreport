package com.xn.report.chart;

import java.nio.file.Path;

/**
 * 离线图表图像渲染产物领域模型。
 * <p>
 * 封装输出图像文件路径（path）、MIME 媒体类型（mediaType，如 image/png）、像素宽高等信息。
 * </p>
 */
public final class RenderedChart {

    private final Path path;
    private final String mediaType;
    private final int widthPixels;
    private final int heightPixels;
    private final int dpi;

    public RenderedChart(
            Path path,
            String mediaType,
            int widthPixels,
            int heightPixels,
            int dpi) {
        if (path == null) {
            throw new IllegalArgumentException("Rendered chart path is required");
        }
        this.path = path.toAbsolutePath().normalize();
        this.mediaType = mediaType;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.dpi = dpi;
    }

    public Path getPath() {
        return path;
    }

    public String getMediaType() {
        return mediaType;
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
