package com.xn.report.text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrendResult {

    public enum Direction {
        UP,
        DOWN,
        FLAT
    }

    public enum Pattern {
        CONTINUOUS_UP,
        CONTINUOUS_DOWN,
        FLAT,
        FLUCTUATING,
        INSUFFICIENT
    }

    private final BigDecimal currentValue;
    private final BigDecimal comparisonValue;
    private final BigDecimal difference;
    private final BigDecimal changeRate;
    private final Direction direction;
    private final Pattern pattern;
    private final TrendAnalyzer.TrendPoint maximum;
    private final TrendAnalyzer.TrendPoint minimum;
    private final List<String> abnormalPeriods;
    private final boolean skipped;
    private final String message;

    TrendResult(
            BigDecimal currentValue,
            BigDecimal comparisonValue,
            BigDecimal difference,
            BigDecimal changeRate,
            Direction direction,
            Pattern pattern,
            TrendAnalyzer.TrendPoint maximum,
            TrendAnalyzer.TrendPoint minimum,
            List<String> abnormalPeriods,
            boolean skipped,
            String message) {
        this.currentValue = currentValue;
        this.comparisonValue = comparisonValue;
        this.difference = difference;
        this.changeRate = changeRate;
        this.direction = direction;
        this.pattern = pattern;
        this.maximum = maximum;
        this.minimum = minimum;
        this.abnormalPeriods = Collections.unmodifiableList(
                new ArrayList<String>(abnormalPeriods));
        this.skipped = skipped;
        this.message = message;
    }

    static TrendResult empty(boolean skipped) {
        return new TrendResult(
                null, null, null, null, null, Pattern.INSUFFICIENT,
                null, null, Collections.<String>emptyList(), skipped,
                skipped ? "" : "暂无趋势数据");
    }

    public BigDecimal currentValue() {
        return currentValue;
    }

    public BigDecimal comparisonValue() {
        return comparisonValue;
    }

    public BigDecimal difference() {
        return difference;
    }

    public BigDecimal changeRate() {
        return changeRate;
    }

    public Direction direction() {
        return direction;
    }

    public Pattern pattern() {
        return pattern;
    }

    public TrendAnalyzer.TrendPoint maximum() {
        return maximum;
    }

    public TrendAnalyzer.TrendPoint minimum() {
        return minimum;
    }

    public List<String> abnormalPeriods() {
        return abnormalPeriods;
    }

    public boolean skipped() {
        return skipped;
    }

    public String message() {
        return message;
    }
}
