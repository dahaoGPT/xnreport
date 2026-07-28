package com.xn.report.rule;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RuleValues {

    private RuleValues() {
    }

    static Map<String, Object> freezeMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        IdentityHashMap<Object, Boolean> visiting = new IdentityHashMap<Object, Boolean>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), freeze(entry.getValue(), visiting));
        }
        return Collections.unmodifiableMap(copy);
    }

    static Object deepKey(Object value) {
        return freeze(value, new IdentityHashMap<Object, Boolean>());
    }

    private static Object freeze(
            Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null || isImmutable(value)) {
            return value;
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
            return value;
        } finally {
            visiting.remove(value);
        }
    }

    private static boolean isImmutable(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof java.time.temporal.Temporal
                || value instanceof java.util.UUID;
    }
}
