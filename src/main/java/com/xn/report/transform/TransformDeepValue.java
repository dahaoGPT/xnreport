package com.xn.report.transform;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TransformDeepValue {

    private final Object value;

    private TransformDeepValue(Object value) {
        this.value = TransformValueSnapshot.freeze(value);
    }

    static TransformDeepValue of(Object value) {
        return new TransformDeepValue(value);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof TransformDeepValue
                && deepEquals(value, ((TransformDeepValue) object).value);
    }

    @Override
    public int hashCode() {
        return deepHash(value);
    }

    private static boolean deepEquals(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number && right instanceof Number) {
            return number((Number) left).compareTo(number((Number) right)) == 0;
        }
        if (left instanceof Date && right instanceof Date) {
            return TransformValueComparator.compare(left, right) == 0;
        }
        if (left.getClass().isArray() || right.getClass().isArray()) {
            return left.getClass().equals(right.getClass())
                    && arraysEqual(left, right);
        }
        if (left instanceof List<?> || right instanceof List<?>) {
            return left instanceof List<?> && right instanceof List<?>
                    && listsEqual((List<?>) left, (List<?>) right);
        }
        if (left instanceof Set<?> || right instanceof Set<?>) {
            return left instanceof Set<?> && right instanceof Set<?>
                    && setsEqual((Set<?>) left, (Set<?>) right);
        }
        if (left instanceof Map<?, ?> || right instanceof Map<?, ?>) {
            return left instanceof Map<?, ?> && right instanceof Map<?, ?>
                    && mapsEqual((Map<?, ?>) left, (Map<?, ?>) right);
        }
        return left.getClass().equals(right.getClass()) && left.equals(right);
    }

    private static boolean arraysEqual(Object left, Object right) {
        int length = Array.getLength(left);
        if (length != Array.getLength(right)) {
            return false;
        }
        for (int index = 0; index < length; index++) {
            if (!deepEquals(Array.get(left, index), Array.get(right, index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean listsEqual(List<?> left, List<?> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!deepEquals(left.get(index), right.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean setsEqual(Set<?> left, Set<?> right) {
        if (left.size() != right.size()) {
            return false;
        }
        List<Object> unmatched = new ArrayList<Object>(right);
        for (Object leftValue : left) {
            int match = findMatch(leftValue, unmatched);
            if (match < 0) {
                return false;
            }
            unmatched.remove(match);
        }
        return true;
    }

    private static boolean mapsEqual(Map<?, ?> left, Map<?, ?> right) {
        if (left.size() != right.size()) {
            return false;
        }
        List<Map.Entry<?, ?>> unmatched =
                new ArrayList<Map.Entry<?, ?>>(right.entrySet());
        for (Map.Entry<?, ?> leftEntry : left.entrySet()) {
            int match = findEntry(leftEntry, unmatched);
            if (match < 0) {
                return false;
            }
            unmatched.remove(match);
        }
        return true;
    }

    private static int findMatch(Object expected, List<Object> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            if (deepEquals(expected, candidates.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int findEntry(
            Map.Entry<?, ?> expected,
            List<Map.Entry<?, ?>> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            Map.Entry<?, ?> candidate = candidates.get(index);
            if (deepEquals(expected.getKey(), candidate.getKey())
                    && deepEquals(expected.getValue(), candidate.getValue())) {
                return index;
            }
        }
        return -1;
    }

    private static int deepHash(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return number((Number) value).stripTrailingZeros().hashCode();
        }
        if (value instanceof Date) {
            return TransformValueComparator.dateHash((Date) value);
        }
        if (value.getClass().isArray()) {
            int result = value.getClass().hashCode();
            for (int index = 0; index < Array.getLength(value); index++) {
                result = 31 * result + deepHash(Array.get(value, index));
            }
            return result;
        }
        if (value instanceof List<?>) {
            int result = 1;
            for (Object item : (List<?>) value) {
                result = 31 * result + deepHash(item);
            }
            return result;
        }
        if (value instanceof Set<?>) {
            int result = 0;
            for (Object item : (Set<?>) value) {
                result += deepHash(item);
            }
            return result;
        }
        if (value instanceof Map<?, ?>) {
            int result = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result += deepHash(entry.getKey()) ^ deepHash(entry.getValue());
            }
            return result;
        }
        return 31 * value.getClass().hashCode() + value.hashCode();
    }

    private static BigDecimal number(Number value) {
        return value instanceof BigDecimal
                ? (BigDecimal) value : new BigDecimal(value.toString());
    }
}
