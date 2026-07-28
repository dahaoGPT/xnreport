package com.xn.report.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TextValueSnapshot {

    private TextValueSnapshot() {
    }

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
        if (value instanceof Date) {
            return new Date(((Date) value).getTime());
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
            return value;
        } finally {
            active.remove(value);
        }
    }

    private static boolean isKnownImmutable(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof java.time.temporal.TemporalAccessor
                || value instanceof java.time.Duration
                || value instanceof java.time.Period;
    }
}
