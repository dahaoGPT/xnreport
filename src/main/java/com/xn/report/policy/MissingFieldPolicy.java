package com.xn.report.policy;

/**
 * 缺失字段处理策略枚举。
 * <p>
 * 定义当模板绑定、规则求值或文本占位符中引用的字段在数据集中不存在时的处理动作：
 * <ul>
 *   <li>{@link #FAIL}：抛出 {@code DATA-002} 异常终止执行。</li>
 *   <li>{@link #USE_DEFAULT}：使用默认值（通常为空字符串或0）填充。</li>
 *   <li>{@link #WARN_AND_SKIP}：记录警告并跳过该字段或组件。</li>
 * </ul>
 * </p>
 */
public enum MissingFieldPolicy {

    /** 抛出异常终止执行（默认安全策略）。 */
    FAIL,

    /** 使用默认值替换缺失字段。 */
    USE_DEFAULT,

    /** 记录警告日志并跳过。 */
    WARN_AND_SKIP
}
