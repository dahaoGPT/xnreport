package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LegendPosition {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    NONE;

    @JsonCreator
    public static LegendPosition fromConfig(String value) {
        return ChartEnumValue.parse(LegendPosition.class, value);
    }
}
