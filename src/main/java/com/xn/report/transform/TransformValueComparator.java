package com.xn.report.transform;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;

public final class TransformValueComparator {

    private TransformValueComparator() {
    }

    public static boolean equal(Object left, Object right) {
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
            return compareDates((Date) left, (Date) right) == 0;
        }
        if (isDeepValue(left) || isDeepValue(right)) {
            return TransformDeepValue.of(left).equals(TransformDeepValue.of(right));
        }
        return left.getClass().equals(right.getClass())
                && Objects.equals(left, right);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static int compare(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException(
                    "Transform values must not be null when ordering");
        }
        if (left instanceof Number && right instanceof Number) {
            return number((Number) left).compareTo(number((Number) right));
        }
        if (left instanceof Date && right instanceof Date) {
            return compareDates((Date) left, (Date) right);
        }
        if (left.getClass().equals(right.getClass())
                && left instanceof Comparable) {
            return ((Comparable) left).compareTo(right);
        }
        throw new IllegalArgumentException(
                "Transform values are not safely comparable: "
                        + left.getClass().getName() + " and "
                        + right.getClass().getName());
    }

    static int dateHash(Date value) {
        DatePoint point = DatePoint.of(value);
        int result = Long.valueOf(point.epochSecond).hashCode();
        return 31 * result + point.nano;
    }

    private static int compareDates(Date left, Date right) {
        return DatePoint.of(left).compareTo(DatePoint.of(right));
    }

    private static boolean isDeepValue(Object value) {
        return value.getClass().isArray()
                || value instanceof java.util.List<?>
                || value instanceof java.util.Set<?>
                || value instanceof java.util.Map<?, ?>;
    }

    private static BigDecimal number(Number value) {
        return value instanceof BigDecimal
                ? (BigDecimal) value : new BigDecimal(value.toString());
    }

    private static final class DatePoint implements Comparable<DatePoint> {

        private final long epochSecond;
        private final int nano;

        private DatePoint(long epochSecond, int nano) {
            this.epochSecond = epochSecond;
            this.nano = nano;
        }

        private static DatePoint of(Date value) {
            if (value instanceof Timestamp) {
                Timestamp timestamp = (Timestamp) value;
                return new DatePoint(
                        timestamp.toInstant().getEpochSecond(),
                        timestamp.getNanos());
            }
            long millis = value.getTime();
            return new DatePoint(
                    Math.floorDiv(millis, 1000L),
                    (int) Math.floorMod(millis, 1000L) * 1_000_000);
        }

        @Override
        public int compareTo(DatePoint other) {
            int seconds = Long.compare(epochSecond, other.epochSecond);
            return seconds != 0 ? seconds : Integer.compare(nano, other.nano);
        }
    }
}
