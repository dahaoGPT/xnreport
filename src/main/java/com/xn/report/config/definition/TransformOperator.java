package com.xn.report.config.definition;

/**
 * 转换操作符枚举。
 * <p>
 * 支持过滤比较（等于、不等于、大于、小于、空判断）与数值四则运算（加减乘除）。
 * </p>
 */
public enum TransformOperator {

    /** 等于（完整拼写）。 */
    EQUAL,
    /** 等于（简写）。 */
    EQ,
    /** 不等于（完整拼写）。 */
    NOT_EQUAL,
    /** 不等于（简写）。 */
    NE,
    /** 大于（完整拼写）。 */
    GREATER_THAN,
    /** 大于（简写）。 */
    GT,
    /** 大于等于（完整拼写）。 */
    GREATER_THAN_OR_EQUAL,
    /** 大于等于（简写）。 */
    GTE,
    /** 小于（完整拼写）。 */
    LESS_THAN,
    /** 小于（简写）。 */
    LT,
    /** 小于等于（完整拼写）。 */
    LESS_THAN_OR_EQUAL,
    /** 小于等于（简写）。 */
    LTE,
    /** 值为 NULL。 */
    IS_NULL,
    /** 值不为 NULL。 */
    IS_NOT_NULL,
    /** 算术加法。 */
    ADD,
    /** 算术减法。 */
    SUBTRACT,
    /** 算术乘法。 */
    MULTIPLY,
    /** 算术除法。 */
    DIVIDE
}
