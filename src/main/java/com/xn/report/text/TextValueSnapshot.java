package com.xn.report.text;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import java.util.Set;
import java.util.UUID;

/**
 * 文本渲染上下文数据值不可变快照与深度拷贝工具类。
 * <p>
 * 为文本变量、summary 统计字典与 runtime 参数提供防御性不可变包装与循环引用安全检测。
 * </p>
 */
final class TextValueSnapshot {

    private TextValueSnapshot() {
    }

    /**
     * 深度冻结 Map 变量字典。
     */
    static Map<String, Object> map(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) freeze(
                source, new IdentityHashMap<Object, Boolean>());
        return snapshot;
    }

    private static Object freeze(
            Object value, IdentityHashMap<Object, Boolean> active) {
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
        if (active.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(
                    "Text values must not contain cyclic references");
        }
        try {
            if (value instanceof Map<?, ?>) {
                Map<String, Object> copy = new LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException(
                                "Text value map keys must be strings");
                    }
                    copy.put((String) entry.getKey(), freeze(entry.getValue(), active));
                }
                return Collections.unmodifiableMap(copy);
            }
            if (value instanceof Set<?>) {
                Set<Object> copy = new LinkedHashSet<Object>();
                for (Object element : (Set<?>) value) {
                    copy.add(freeze(element, active));
                }
                return Collections.unmodifiableSet(copy);
            }
            if (value instanceof Collection<?>) {
                List<Object> copy = new ArrayList<Object>();
                for (Object element : (Collection<?>) value) {
                    copy.add(freeze(element, active));
                }
                return Collections.unmodifiableList(copy);
            }
            List<Object> array = TextArrayValues.copy(value);
            if (array != null) {
                List<Object> copy = new ArrayList<Object>();
                for (Object element : array) {
                    copy.add(freeze(element, active));
                }
                return Collections.unmodifiableList(copy);
            }
            throw new IllegalArgumentException(
                    "Unsupported mutable text value type: "
                            + value.getClass().getName());
        } finally {
            active.remove(value);
        }
    }

    private static boolean isKnownImmutable(Object value) {
        Class<?> type = value.getClass();
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == BigInteger.class
                || type == BigDecimal.class
                || value instanceof UUID
                || value instanceof Enum<?>
                || value instanceof Class<?>
                || value instanceof Instant
                || value instanceof LocalDate
                || value instanceof LocalTime
                || value instanceof LocalDateTime
                || value instanceof OffsetTime
                || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime
                || value instanceof Year
                || value instanceof YearMonth
                || value instanceof MonthDay
                || value instanceof ZoneId
                || value instanceof ZoneOffset
                || value instanceof Duration
                || value instanceof Period;
    }
}
