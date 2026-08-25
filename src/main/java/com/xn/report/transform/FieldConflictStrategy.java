package com.xn.report.transform;

/**
 * 派生字段同名列覆盖冲突处理策略枚举。
 * <p>
 * <ul>
 *   <li>{@link #FAIL}：当派生字段名称与已存在列重名时抛出异常失败。</li>
 *   <li>{@link #REPLACE}：就地覆盖现有同名列的值与类型。</li>
 * </ul>
 * </p>
 */
public enum FieldConflictStrategy {

    /** 冲突时抛出异常失败。 */
    FAIL,

    /** 冲突时直接覆盖现有列。 */
    REPLACE
}
