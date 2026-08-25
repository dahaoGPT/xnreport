package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 图表数据源为空时的处理策略枚举。
 */
public enum ChartEmptyDataPolicy {

    /** 抛出异常中断执行。 */
    FAIL,

    /** 渲染输出空数据提示文案占位图。 */
    OUTPUT_MESSAGE,

    /** 跳过该图表的生成。 */
    SKIP;

    @JsonCreator
    public static ChartEmptyDataPolicy fromConfig(String value) {
        return ChartEnumValue.parse(ChartEmptyDataPolicy.class, value);
    }
}
