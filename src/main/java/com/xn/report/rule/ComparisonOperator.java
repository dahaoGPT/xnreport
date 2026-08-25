package com.xn.report.rule;

/**
 * 规则引擎比较运算符枚举。
 * <p>
 * 支持等值、不等、大小关系、集合归属、区间与字符串文本匹配判断。
 * </p>
 */
public enum ComparisonOperator {

    /** 等于。 */
    EQ,

    /** 不等于。 */
    NE,

    /** 大于。 */
    GT,

    /** 大于等于。 */
    GE,

    /** 小于。 */
    LT,

    /** 小于等于。 */
    LE,

    /** 存在于集合中。 */
    IN,

    /** 不存在于集合中。 */
    NOT_IN,

    /** 处于区间 [min, max] 之间。 */
    BETWEEN,

    /** 包含子字符串。 */
    CONTAINS,

    /** 以前缀子串开头。 */
    STARTS_WITH,

    /** 以后缀子串结尾。 */
    ENDS_WITH,

    /** 值为 NULL。 */
    IS_NULL,

    /** 值不为 NULL。 */
    IS_NOT_NULL
}
