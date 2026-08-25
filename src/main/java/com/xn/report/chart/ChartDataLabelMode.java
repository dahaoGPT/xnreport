package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 图表数据标签显示模式枚举。
 */
public enum ChartDataLabelMode {

    /** 不显示数据标签。 */
    NONE,

    /** 显示实际数值。 */
    VALUE,

    /** 饼图/环形图显示频数。 */
    COUNT,

    /** 饼图/环形图显示百分比。 */
    PERCENT,

    /** 饼图/环形图同时显示频数与百分比。 */
    COUNT_AND_PERCENT;

    @JsonCreator
    public static ChartDataLabelMode fromConfig(String value) {
        return ChartEnumValue.parse(ChartDataLabelMode.class, value);
    }
}
