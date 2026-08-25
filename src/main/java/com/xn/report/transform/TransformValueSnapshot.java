package com.xn.report.transform;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 转换器值快照深拷贝与不可变冻结工具类。
 * <p>
 * 为过滤（Filter）等转换器中所持有的目标匹配值提供深度不可变拷贝，具备循环引用检测能力。
 * </p>
 */
final class TransformValueSnapshot {

    private TransformValueSnapshot() {
    }

    /**
     * 深度冻结并复制输入对象。
     *
     * @param value 原始值
     * @return 深度拷贝后的安全副本
     */
    static Object freeze(Object value) {
        return copy(value, new IdentityHashMap<Object, Boolean>());
    }

    private static Object copy(
            Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null || isKnownImmutable(value)) {
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
        if (value.getClass().isArray()) {
            return copyArray(value, visiting);
        }
        if (value instanceof List<?>) {
            return copyList((List<?>) value, visiting);
        }
        if (value instanceof Set<?>) {
            return copySet((Set<?>) value, visiting);
        }
        if (value instanceof Map<?, ?>) {
            return copyMap((Map<?, ?>) value, visiting);
        }
        throw new IllegalArgumentException(
                "Unsupported mutable transform value type: "
                        + value.getClass().getName());
    }

    private static Object copyArray(
            Object source, IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            int length = Array.getLength(source);
            Class<?> componentType = source.getClass().getComponentType();
            Object copy = Array.newInstance(componentType, length);
            if (componentType.isPrimitive()) {
                System.arraycopy(source, 0, copy, 0, length);
                return copy;
            }
            for (int index = 0; index < length; index++) {
                Array.set(copy, index, copy(Array.get(source, index), visiting));
            }
            return copy;
        } finally {
            visiting.remove(source);
        }
    }

    private static List<?> copyList(
            List<?> source, IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            List<Object> copy = new ArrayList<Object>(source.size());
            for (Object value : source) {
                copy.add(copy(value, visiting));
            }
            return Collections.unmodifiableList(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static Set<?> copySet(
            Set<?> source, IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Set<Object> copy = new LinkedHashSet<Object>();
            for (Object value : source) {
                copy.add(copy(value, visiting));
            }
            return Collections.unmodifiableSet(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static Map<?, ?> copyMap(
            Map<?, ?> source, IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Map<Object, Object> copy = new LinkedHashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                copy.put(
                        copy(entry.getKey(), visiting),
                        copy(entry.getValue(), visiting));
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static void enter(
            Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(
                    "Cyclic transform value: " + value.getClass().getName());
        }
    }

    private static boolean isKnownImmutable(Object value) {
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
                || value instanceof UUID
                || value instanceof Enum<?>
                || value instanceof Class<?>
                || value.getClass().getName().startsWith("java.time.");
    }
}
