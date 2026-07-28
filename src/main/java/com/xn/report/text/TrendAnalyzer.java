package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrendAnalyzer {

    public TrendResult analyze(
            List<TrendPoint> points,
            BigDecimal comparisonValue,
            BigDecimal flatTolerance,
            BigDecimal abnormalThreshold,
            NarrativeDefinition.EmptyStrategy emptyStrategy) {
        if (flatTolerance == null || flatTolerance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Flat tolerance must be a non-negative BigDecimal");
        }
        NarrativeDefinition.EmptyStrategy strategy = emptyStrategy == null
                ? NarrativeDefinition.EmptyStrategy.FAIL : emptyStrategy;
        if (points == null || points.isEmpty()) {
            if (strategy == NarrativeDefinition.EmptyStrategy.FAIL) {
                throw new TextRenderException("Trend data is empty");
            }
            return TrendResult.empty(
                    strategy == NarrativeDefinition.EmptyStrategy.SKIP);
        }
        if (comparisonValue == null) {
            throw new IllegalArgumentException("Trend comparison value is required");
        }
        List<TrendPoint> snapshot = validateAndCopy(points);
        TrendPoint current = snapshot.get(snapshot.size() - 1);
        BigDecimal difference = current.value().subtract(comparisonValue);
        BigDecimal rate = comparisonValue.signum() == 0
                ? null
                : difference.divide(
                        comparisonValue.abs(), 10, RoundingMode.HALF_UP);
        TrendResult.Direction direction =
                direction(difference, flatTolerance);
        TrendPoint maximum = snapshot.get(0);
        TrendPoint minimum = snapshot.get(0);
        List<String> abnormal = new ArrayList<String>();
        for (TrendPoint point : snapshot) {
            if (point.value().compareTo(maximum.value()) > 0) {
                maximum = point;
            }
            if (point.value().compareTo(minimum.value()) < 0) {
                minimum = point;
            }
            if (abnormalThreshold != null
                    && point.value().compareTo(abnormalThreshold) > 0) {
                abnormal.add(point.period());
            }
        }
        return new TrendResult(
                current.value(),
                comparisonValue,
                difference,
                rate,
                direction,
                pattern(snapshot, flatTolerance),
                maximum,
                minimum,
                abnormal,
                false,
                null);
    }

    private static List<TrendPoint> validateAndCopy(List<TrendPoint> points) {
        List<TrendPoint> copy = new ArrayList<TrendPoint>();
        for (TrendPoint point : points) {
            if (point == null) {
                throw new IllegalArgumentException(
                        "Trend points must not contain null");
            }
            copy.add(point);
        }
        return Collections.unmodifiableList(copy);
    }

    private static TrendResult.Direction direction(
            BigDecimal difference, BigDecimal tolerance) {
        if (difference.abs().compareTo(tolerance) <= 0) {
            return TrendResult.Direction.FLAT;
        }
        return difference.signum() > 0
                ? TrendResult.Direction.UP : TrendResult.Direction.DOWN;
    }

    private static TrendResult.Pattern pattern(
            List<TrendPoint> points, BigDecimal tolerance) {
        if (points.size() < 2) {
            return TrendResult.Pattern.INSUFFICIENT;
        }
        boolean sawUp = false;
        boolean sawDown = false;
        boolean sawFlat = false;
        for (int index = 1; index < points.size(); index++) {
            TrendResult.Direction direction = direction(
                    points.get(index).value()
                            .subtract(points.get(index - 1).value()),
                    tolerance);
            sawUp |= direction == TrendResult.Direction.UP;
            sawDown |= direction == TrendResult.Direction.DOWN;
            sawFlat |= direction == TrendResult.Direction.FLAT;
        }
        if (!sawUp && !sawDown) {
            return TrendResult.Pattern.FLAT;
        }
        if (sawUp && !sawDown && !sawFlat) {
            return TrendResult.Pattern.CONTINUOUS_UP;
        }
        if (sawDown && !sawUp && !sawFlat) {
            return TrendResult.Pattern.CONTINUOUS_DOWN;
        }
        return TrendResult.Pattern.FLUCTUATING;
    }

    public static final class TrendPoint {
        private final String period;
        private final BigDecimal value;

        public TrendPoint(String period, BigDecimal value) {
            if (period == null || period.trim().isEmpty() || value == null) {
                throw new IllegalArgumentException(
                        "Trend period and value are required");
            }
            this.period = period;
            this.value = value;
        }

        public String period() {
            return period;
        }

        public BigDecimal value() {
            return value;
        }
    }
}
