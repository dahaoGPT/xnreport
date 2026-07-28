package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartEmptyDataPolicy {
    FAIL,
    OUTPUT_MESSAGE,
    SKIP;

    @JsonCreator
    public static ChartEmptyDataPolicy fromConfig(String value) {
        return ChartEnumValue.parse(ChartEmptyDataPolicy.class, value);
    }
}
