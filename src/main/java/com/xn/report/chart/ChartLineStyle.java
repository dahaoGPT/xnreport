package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartLineStyle {
    SOLID,
    DASHED,
    DOTTED;

    @JsonCreator
    public static ChartLineStyle fromConfig(String value) {
        return ChartEnumValue.parse(ChartLineStyle.class, value);
    }
}
