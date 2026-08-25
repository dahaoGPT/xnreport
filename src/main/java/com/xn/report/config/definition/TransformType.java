package com.xn.report.config.definition;

/**
 * 内存数据集派生转换类型枚举。
 * <p>
 * <ul>
 *   <li>{@link #FILTER}：行级数据条件过滤。</li>
 *   <li>{@link #SORT}：多字段升降序排列。</li>
 *   <li>{@link #DISTINCT}：指定字段集合唯一性去重。</li>
 *   <li>{@link #LIMIT}：截断保留前 N 行。</li>
 *   <li>{@link #DERIVED_FIELD}：四则运算计算生成新字段。</li>
 * </ul>
 * </p>
 */
public enum TransformType {

    /** 条件过滤。 */
    FILTER,

    /** 排序。 */
    SORT,

    /** 去重。 */
    DISTINCT,

    /** 行数截断。 */
    LIMIT,

    /** 派生字段计算。 */
    DERIVED_FIELD
}
