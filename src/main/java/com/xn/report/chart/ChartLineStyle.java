package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 图表折线线型枚举。
 */
public enum ChartLineStyle {

    /** 实线。 */
    SOLID,

    /** 虚线（短划线）。 */
    DASHED,

    /** 点线。 */
    DOTTED;

    @JsonCreator
    public static ChartLineStyle fromConfig(String value) {
        return ChartEnumValue.parse(ChartLineStyle.class, value);
    }
}
