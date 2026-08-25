package com.xn.report.text;

/**
 * 文本模板中未解析/未匹配占位符的处理策略枚举。
 * <p>
 * <ul>
 *   <li>{@link #FAIL}：抛出 {@link TextRenderException} 异常失败。</li>
 *   <li>{@link #KEEP}：原样保留占位符文本（如 <code>${unknown}</code>）。</li>
 *   <li>{@link #EMPTY}：替换为空字符串。</li>
 * </ul>
 * </p>
 */
public enum UnresolvedPlaceholderPolicy {

    /** 抛出异常失败。 */
    FAIL,

    /** 原样保留占位符字符串。 */
    KEEP,

    /** 替换为空字符串。 */
    EMPTY
}
