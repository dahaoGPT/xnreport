package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 报表引擎支持的所有图表物理类型枚举。
 */
public enum ChartType {

    /** 柱状图（普通簇状柱形图）。 */
    COLUMN,

    /** 堆叠柱状图。 */
    STACKED_COLUMN,

    /** 百分比堆叠柱状图。 */
    PERCENT_STACKED_COLUMN,

    /** 折线图。 */
    LINE,

    /** 条形图（普通簇状水平条形图）。 */
    BAR,

    /** 堆叠条形图。 */
    STACKED_BAR,

    /** 饼图。 */
    PIE,

    /** 环形图。 */
    DOUGHNUT,

    /** 面积图。 */
    AREA,

    /** 堆叠面积图。 */
    STACKED_AREA,

    /** 散点图（XY 散点图）。 */
    SCATTER,

    /** 气泡图（XYZ 三维气泡图）。 */
    BUBBLE,

    /** 雷达图（蜘蛛网图）。 */
    RADAR,

    /** 股价图（High-Low-Close 或 Open-High-Low-Close）。 */
    STOCK;

    @JsonCreator
    public static ChartType fromConfig(String value) {
        return ChartEnumValue.parse(ChartType.class, value);
    }

    /**
     * 判断当前类型是否为饼状/环形单系列占比图表。
     */
    public boolean isPieLike() {
        return this == PIE || this == DOUGHNUT;
    }

    /**
     * 判断当前类型是否为堆叠形态图表（需要 stackGroup 支持）。
     */
    public boolean isStacked() {
        return this == STACKED_COLUMN
                || this == PERCENT_STACKED_COLUMN
                || this == STACKED_BAR
                || this == STACKED_AREA;
    }
}
