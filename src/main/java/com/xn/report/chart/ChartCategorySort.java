package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartCategorySort {
    SOURCE,
    ASC,
    DESC,
    EXPLICIT;

    @JsonCreator
    public static ChartCategorySort fromConfig(String value) {
        return ChartEnumValue.parse(ChartCategorySort.class, value);
    }
}
