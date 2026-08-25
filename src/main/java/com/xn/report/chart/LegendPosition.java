package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 图表图例显示位置枚举。
 */
public enum LegendPosition {

    /** 位于顶部（TOP）。 */
    TOP,

    /** 位于底部（BOTTOM）。 */
    BOTTOM,

    /** 位于左侧（LEFT）。 */
    LEFT,

    /** 位于右侧（RIGHT）。 */
    RIGHT,

    /** 隐藏图例（NONE）。 */
    NONE;

    @JsonCreator
    public static LegendPosition fromConfig(String value) {
        return ChartEnumValue.parse(LegendPosition.class, value);
    }
}
