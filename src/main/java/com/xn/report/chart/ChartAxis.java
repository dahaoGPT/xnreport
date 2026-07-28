package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartAxis {
    PRIMARY,
    SECONDARY;

    @JsonCreator
    public static ChartAxis fromConfig(String value) {
        return ChartEnumValue.parse(ChartAxis.class, value);
    }
}
