package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 图表数据空值（null）处理策略枚举。
 */
public enum ChartNullHandling {

    /** 留空/断开折线（GAP）。 */
    GAP,

    /** 将空值视为数值 0（ZERO）。 */
    ZERO,

    /** 剔除包含空值的类目整行（SKIP_CATEGORY）。 */
    SKIP_CATEGORY;

    @JsonCreator
    public static ChartNullHandling fromConfig(String value) {
        return ChartEnumValue.parse(ChartNullHandling.class, value);
    }
}
