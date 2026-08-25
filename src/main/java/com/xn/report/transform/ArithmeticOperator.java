package com.xn.report.transform;

/**
 * 派生字段计算算术操作符枚举。
 * <p>
 * <ul>
 *   <li>{@link #ADD}：加法运算。</li>
 *   <li>{@link #SUBTRACT}：减法运算。</li>
 *   <li>{@link #MULTIPLY}：乘法运算。</li>
 *   <li>{@link #DIVIDE}：除法运算。</li>
 * </ul>
 * </p>
 */
public enum ArithmeticOperator {

    /** 加法。 */
    ADD,

    /** 减法。 */
    SUBTRACT,

    /** 乘法。 */
    MULTIPLY,

    /** 除法。 */
    DIVIDE
}
