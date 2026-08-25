package com.xn.report.chart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个图表数据系列的物理渲染领域模型。
 * <p>
 * 封装系列名称、取值字段、图表类型（柱状/折线/饼图/散点/气泡等）、坐标轴归属、堆叠组、颜色、线型线宽、数据标签模式、数据格式、空值策略及数值序列列表（values、sizes）。
 * </p>
 */
public final class ChartSeriesModel {

    private final String field;
    private final String name;
    private final ChartType type;
    private final ChartAxis axis;
    private final String stackGroup;
    private final String color;
    private final ChartLineStyle lineStyle;
    private final BigDecimal lineWidth;
    private final boolean marker;
    private final ChartDataLabelMode dataLabelMode;
    private final String format;
    private final ChartNullHandling nullHandling;
    private final int legendOrder;
    private final int sourceIndex;
    private final List<BigDecimal> values;
    private final List<BigDecimal> sizes;

    public ChartSeriesModel(
            String field,
            String name,
            ChartType type,
            ChartAxis axis,
            String stackGroup,
            String color,
            ChartLineStyle lineStyle,
            BigDecimal lineWidth,
            boolean marker,
            ChartDataLabelMode dataLabelMode,
            String format,
            ChartNullHandling nullHandling,
            int legendOrder,
            List<BigDecimal> values,
            List<BigDecimal> sizes) {
        this(field, name, type, axis, stackGroup, color,
                lineStyle, lineWidth, marker, dataLabelMode,
                format, nullHandling, legendOrder, -1,
                values, sizes);
    }

    public ChartSeriesModel(
            String field,
            String name,
            ChartType type,
            ChartAxis axis,
            String stackGroup,
            String color,
            ChartLineStyle lineStyle,
            BigDecimal lineWidth,
            boolean marker,
            ChartDataLabelMode dataLabelMode,
            String format,
            ChartNullHandling nullHandling,
            int legendOrder,
            int sourceIndex,
            List<BigDecimal> values,
            List<BigDecimal> sizes) {
        this.field = field;
        this.name = name;
        this.type = type;
        this.axis = axis;
        this.stackGroup = stackGroup;
        this.color = color;
        this.lineStyle = lineStyle;
        this.lineWidth = lineWidth;
        this.marker = marker;
        this.dataLabelMode = dataLabelMode;
        this.format = format;
        this.nullHandling = nullHandling;
        this.legendOrder = legendOrder;
        this.sourceIndex = sourceIndex;
        this.values = immutable(values);
        this.sizes = immutable(sizes);
    }

    private static List<BigDecimal> immutable(List<BigDecimal> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<BigDecimal>(values));
    }

    public String getField() {
        return field;
    }

    public String getName() {
        return name;
    }

    public ChartType getType() {
        return type;
    }

    public ChartAxis getAxis() {
        return axis;
    }

    public String getStackGroup() {
        return stackGroup;
    }

    public String getColor() {
        return color;
    }

    public ChartLineStyle getLineStyle() {
        return lineStyle;
    }

    public BigDecimal getLineWidth() {
        return lineWidth;
    }

    public boolean isMarker() {
        return marker;
    }

    public ChartDataLabelMode getDataLabelMode() {
        return dataLabelMode;
    }

    public String getFormat() {
        return format;
    }

    public ChartNullHandling getNullHandling() {
        return nullHandling;
    }

    public int getLegendOrder() {
        return legendOrder;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public List<BigDecimal> getValues() {
        return values;
    }

    public List<BigDecimal> getSizes() {
        return sizes;
    }
}
