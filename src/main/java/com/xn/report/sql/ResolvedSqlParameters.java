package com.xn.report.sql;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public final class ResolvedSqlParameters {

    private final Map<String, Object> values;

    public ResolvedSqlParameters(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        this.values = copyMap(
                values, new IdentityHashMap<Object, Boolean>());
    }

    public Map<String, Object> asMap() {
        return copyMap(
                values, new IdentityHashMap<Object, Boolean>());
    }

    public MapSqlParameterSource toMapSqlParameterSource() {
        return new MapSqlParameterSource(asMap());
    }

    private static Map<String, Object> copyMap(
            Map<String, Object> source,
            IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                copy.put(entry.getKey(), copyValue(entry.getValue(), visiting));
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static Object copyValue(
            Object value,
            IdentityHashMap<Object, Boolean> visiting) {
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
        if (value instanceof Collection<?>) {
            return copyCollection((Collection<?>) value, visiting);
        }
        if (value instanceof Map<?, ?>) {
            return copyNestedMap((Map<?, ?>) value, visiting);
        }
        throw new IllegalArgumentException(
                "Unsupported mutable SQL parameter value type: "
                        + value.getClass().getName());
    }

    private static Object copyArray(
            Object source,
            IdentityHashMap<Object, Boolean> visiting) {
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
                Array.set(
                        copy,
                        index,
                        copyValue(Array.get(source, index), visiting));
            }
            return copy;
        } finally {
            visiting.remove(source);
        }
    }

    private static List<?> copyList(
            List<?> source,
            IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            List<Object> copy = new ArrayList<Object>(source.size());
            for (Object value : source) {
                copy.add(copyValue(value, visiting));
            }
            return Collections.unmodifiableList(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static Set<?> copySet(
            Set<?> source,
            IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Set<Object> copy = new LinkedHashSet<Object>();
            for (Object value : source) {
                copy.add(copyValue(value, visiting));
            }
            return Collections.unmodifiableSet(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static List<?> copyCollection(
            Collection<?> source,
            IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            List<Object> copy = new ArrayList<Object>(source.size());
            for (Object value : source) {
                copy.add(copyValue(value, visiting));
            }
            return Collections.unmodifiableList(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static Map<?, ?> copyNestedMap(
            Map<?, ?> source,
            IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Map<Object, Object> copy = new LinkedHashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                Object key = copyValue(entry.getKey(), visiting);
                Object value = copyValue(entry.getValue(), visiting);
                copy.put(key, value);
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static void enter(
            Object value,
            IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(
                    "Cyclic mutable SQL parameter value: "
                            + value.getClass().getName());
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
