package com.xn.report.rule;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.Set;

/**
 * 规则引擎值对象深拷贝与不可变冻结工具类。
 * <p>
 * 为规则计算结果、度量汇总与分组键提供不可变防护和循环引用安全检测。
 * </p>
 */
final class RuleValues {

    private RuleValues() {
    }

    /**
     * 深度冻结 Map 键值对字典。
     */
    static Map<String, Object> freezeMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        IdentityHashMap<Object, Boolean> visiting = new IdentityHashMap<Object, Boolean>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), freeze(entry.getValue(), visiting));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 复制 Map 字典。
     */
    static Map<String, Object> copyMap(Map<String, Object> source) {
        return freezeMap(source);
    }

    /**
     * 深度冻结单值。
     */
    static Object freezeValue(Object value) {
        return freeze(value, new IdentityHashMap<Object, Boolean>());
    }

    /**
     * 复制单值。
     */
    static Object copyValue(Object value) {
        return freezeValue(value);
    }

    /**
     * 构建用于分组的深度键值副本。
     */
    static Object deepKey(Object value) {
        return freezeValue(value);
    }

    private static Object freeze(
            Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null || isImmutable(value)) {
            return value;
        }
        if (value instanceof Timestamp) {
            Timestamp source = (Timestamp) value;
            Timestamp copy = new Timestamp(source.getTime());
            copy.setNanos(source.getNanos());
            return copy;
        }
        if (value instanceof java.sql.Date) {
            return new java.sql.Date(((java.sql.Date) value).getTime());
        }
        if (value instanceof Time) {
            return new Time(((Time) value).getTime());
        }
        if (value instanceof Date) {
            return new Date(((Date) value).getTime());
        }
        if (value instanceof Calendar) {
            return ((Calendar) value).clone();
        }
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw RuleErrors.invalid("Cyclic rule result value is not supported");
        }
        try {
            if (value instanceof Map<?, ?>) {
                LinkedHashMap<Object, Object> copy = new LinkedHashMap<Object, Object>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    copy.put(freeze(entry.getKey(), visiting),
                            freeze(entry.getValue(), visiting));
                }
                return Collections.unmodifiableMap(copy);
            }
            if (value instanceof Set<?>) {
                LinkedHashSet<Object> copy = new LinkedHashSet<Object>();
                for (Object element : (Set<?>) value) {
                    copy.add(freeze(element, visiting));
                }
                return Collections.unmodifiableSet(copy);
            }
            if (value instanceof Collection<?>) {
                ArrayList<Object> copy = new ArrayList<Object>();
                for (Object element : (Collection<?>) value) {
                    copy.add(freeze(element, visiting));
                }
                return Collections.unmodifiableList(copy);
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                ArrayList<Object> copy = new ArrayList<Object>(length);
                for (int index = 0; index < length; index++) {
                    copy.add(freeze(Array.get(value, index), visiting));
                }
                return Collections.unmodifiableList(copy);
            }
            throw RuleErrors.invalid(
                    "Unsupported mutable rule result value type: "
                            + value.getClass().getName());
        } finally {
            visiting.remove(value);
        }
    }

    private static boolean isImmutable(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof Character
                || value instanceof BigInteger
                || value instanceof BigDecimal
                || value instanceof Enum<?>
                || value instanceof Class<?>
                || value instanceof java.util.UUID
                || value.getClass().getName().startsWith("java.time.");
    }
}
