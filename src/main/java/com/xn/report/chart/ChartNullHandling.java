package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartNullHandling {
    GAP,
    ZERO,
    SKIP_CATEGORY;

    @JsonCreator
    public static ChartNullHandling fromConfig(String value) {
        return ChartEnumValue.parse(ChartNullHandling.class, value);
    }
}
