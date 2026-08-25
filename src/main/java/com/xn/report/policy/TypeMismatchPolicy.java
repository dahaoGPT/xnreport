package com.xn.report.policy;

/**
 * 字段类型不匹配处理策略枚举。
 * <p>
 * 定义当实际查询结果的字段类型与预期类型不一致时的处理方式：
 * <ul>
 *   <li>{@link #FAIL}：抛出 {@code DATA-003} 异常终止。</li>
 *   <li>{@link #SAFE_CONVERT}：尝试进行安全类型转换（如数字转字符串、字符串解析数值）。</li>
 *   <li>{@link #WARN_AND_SKIP}：记录警告并跳过该字段或组件。</li>
 * </ul>
 * </p>
 */
public enum TypeMismatchPolicy {

    /** 抛出类型不匹配异常终止执行。 */
    FAIL,

    /** 尝试自动安全转换类型。 */
    SAFE_CONVERT,

    /** 记录警告并跳过。 */
    WARN_AND_SKIP
}
