package com.xn.report.transform;

/**
 * 排序时 NULL 值的排序位置策略枚举。
 * <p>
 * <ul>
 *   <li>{@link #FIRST}：NULL 值排在最前。</li>
 *   <li>{@link #LAST}：NULL 值排在最后。</li>
 * </ul>
 * </p>
 */
public enum NullOrder {

    /** NULL 排在最前。 */
    FIRST,

    /** NULL 排在最后。 */
    LAST
}
