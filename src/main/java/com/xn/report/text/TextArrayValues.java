package com.xn.report.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文本渲染原生数组到 List 集合的复制解包工具类。
 * <p>
 * 支持将 Java 原生基本类型数组（int[], byte[], double[] 等）以及对象数组安全包装复制为泛型 List 列表。
 * </p>
 */
final class TextArrayValues {

    private TextArrayValues() {
    }

    /**
     * 将对象或原生数组复制为 List 列表。
     *
     * @param value 原始数组对象
     * @return List 集合，若非数组则返回 null
     */
    static List<Object> copy(Object value) {
        List<Object> values = new ArrayList<Object>();
        if (value instanceof Object[]) {
            Collections.addAll(values, (Object[]) value);
        } else if (value instanceof byte[]) {
            for (byte item : (byte[]) value) {
                values.add(item);
            }
        } else if (value instanceof short[]) {
            for (short item : (short[]) value) {
                values.add(item);
            }
        } else if (value instanceof int[]) {
            for (int item : (int[]) value) {
                values.add(item);
            }
        } else if (value instanceof long[]) {
            for (long item : (long[]) value) {
                values.add(item);
            }
        } else if (value instanceof float[]) {
            for (float item : (float[]) value) {
                values.add(item);
            }
        } else if (value instanceof double[]) {
            for (double item : (double[]) value) {
                values.add(item);
            }
        } else if (value instanceof char[]) {
            for (char item : (char[]) value) {
                values.add(item);
            }
        } else if (value instanceof boolean[]) {
            for (boolean item : (boolean[]) value) {
                values.add(item);
            }
        } else {
            return null;
        }
        return values;
    }
}
