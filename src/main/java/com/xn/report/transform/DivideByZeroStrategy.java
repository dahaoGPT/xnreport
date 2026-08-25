package com.xn.report.transform;

/**
 * 派生字段除以零（Divide By Zero）容错策略枚举。
 * <p>
 * <ul>
 *   <li>{@link #FAIL}：抛出 ArithmeticException 异常终止流程。</li>
 *   <li>{@link #NULL}：将结果单元格置为 NULL。</li>
 *   <li>{@link #DEFAULT_VALUE}：将结果单元格填充为预设的替代默认数值。</li>
 * </ul>
 * </p>
 */
public enum DivideByZeroStrategy {

    /** 抛出异常失败。 */
    FAIL,

    /** 置为 NULL。 */
    NULL,

    /** 使用预设默认值填充。 */
    DEFAULT_VALUE
}
