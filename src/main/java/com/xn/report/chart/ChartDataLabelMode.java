package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartDataLabelMode {
    NONE,
    VALUE,
    COUNT,
    PERCENT,
    COUNT_AND_PERCENT;

    @JsonCreator
    public static ChartDataLabelMode fromConfig(String value) {
        return ChartEnumValue.parse(ChartDataLabelMode.class, value);
    }
}
