package com.xn.report.text;

/**
 * 文本占位符变量格式化器函数接口。
 * <p>
 * 将输入变量对象按指定的参数（如格式化模式串）转换为目标文本。
 * </p>
 */
public interface ValueFormatter {

    /**
     * 格式化单个变量值。
     *
     * @param value 原始输入值
     * @param argument 格式化参数（如 <code>0.00</code>, <code>yyyy-MM-dd</code>, <code>,</code> 等，可为空）
     * @return 格式化后的字符串
     */
    String format(Object value, String argument);
}
