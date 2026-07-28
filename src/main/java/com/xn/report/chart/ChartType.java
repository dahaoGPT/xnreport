package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartType {
    COLUMN,
    STACKED_COLUMN,
    PERCENT_STACKED_COLUMN,
    LINE,
    BAR,
    STACKED_BAR,
    PIE,
    DOUGHNUT,
    AREA,
    STACKED_AREA,
    SCATTER,
    BUBBLE,
    RADAR,
    STOCK;

    @JsonCreator
    public static ChartType fromConfig(String value) {
        return ChartEnumValue.parse(ChartType.class, value);
    }

    public boolean isPieLike() {
        return this == PIE || this == DOUGHNUT;
    }

    public boolean isStacked() {
        return this == STACKED_COLUMN
                || this == PERCENT_STACKED_COLUMN
                || this == STACKED_BAR
                || this == STACKED_AREA;
    }
}
