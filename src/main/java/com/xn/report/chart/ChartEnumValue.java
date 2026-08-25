package com.xn.report.chart;

import java.util.Locale;

/**
 * 图表相关枚举配置字符串解析工具类。
 * <p>
 * 统一将 kebab-case（如 <code>stacked-column</code>）、空格分隔或 camelCase 规范化为 SCREAMING_SNAKE_CASE 并映射枚举。
 * </p>
 */
public final class ChartEnumValue {

    private ChartEnumValue() {
    }

    /**
     * 兼容性解析枚举值。
     *
     * @param type 枚举类对象
     * @param value 原始配置字符串
     * @param <E> 枚举泛型
     * @return 匹配的枚举常量，若输入为 null 则返回 null
     */
    public static <E extends Enum<E>> E parse(
            Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
        return Enum.valueOf(type, normalized);
    }
}
