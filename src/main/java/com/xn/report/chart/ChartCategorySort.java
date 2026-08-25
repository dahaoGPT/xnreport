package com.xn.report.chart;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 图表类目轴排序规则枚举。
 */
public enum ChartCategorySort {

    /** 保持数据集原始顺序。 */
    SOURCE,

    /** 类目值升序排序。 */
    ASC,

    /** 类目值降序排序。 */
    DESC,

    /** 按照配置显式指定的 categories 列表顺序。 */
    EXPLICIT;

    @JsonCreator
    public static ChartCategorySort fromConfig(String value) {
        return ChartEnumValue.parse(ChartCategorySort.class, value);
    }
}
