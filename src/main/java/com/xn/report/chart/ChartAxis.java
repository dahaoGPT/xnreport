package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 图表数值轴/纵轴归属枚举（主轴与次坐标轴）。
 */
public enum ChartAxis {

    /** 主坐标轴（左轴/主数值轴）。 */
    PRIMARY,

    /** 次坐标轴（右轴/次数值轴）。 */
    SECONDARY;

    @JsonCreator
    public static ChartAxis fromConfig(String value) {
        return ChartEnumValue.parse(ChartAxis.class, value);
    }
}
