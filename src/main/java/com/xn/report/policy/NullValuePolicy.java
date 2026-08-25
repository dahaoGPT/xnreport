package com.xn.report.policy;

/**
 * 空值（NULL）处理策略枚举。
 * <p>
 * 定义当字段值为 null 时的求值策略：
 * <ul>
 *   <li>{@link #RULE_NOT_MATCHED}：在规则引擎中视作条件不匹配（返回 false）。</li>
 *   <li>{@link #USE_DEFAULT}：使用配置的默认值替换 null。</li>
 *   <li>{@link #ALLOW}：允许保留 null 值（如输出空单元格或空文本）。</li>
 *   <li>{@link #FAIL}：抛出异常终止执行。</li>
 * </ul>
 * </p>
 */
public enum NullValuePolicy {

    /** 规则求值判定为不匹配。 */
    RULE_NOT_MATCHED,

    /** 替换为预设默认值。 */
    USE_DEFAULT,

    /** 允许 null 值穿透输出。 */
    ALLOW,

    /** 抛出异常终止。 */
    FAIL
}
