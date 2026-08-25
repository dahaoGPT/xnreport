package com.xn.report.text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 时间序列趋势分析结果值对象模型。
 * <p>
 * 封装趋势方向（direction）、时序形态（pattern）、末期值（currentValue）、基准比较值（comparisonValue）、
 * 差值（difference）、变动率（changeRate）、极值点（maximum/minimum）及异常周期列表（abnormalPeriods）。
 * </p>
 */
public final class TrendResult {

    /**
     * 趋势方向枚举。
     */
    public enum Direction {
        /** 上升。 */
        UP,
        /** 下降。 */
        DOWN,
        /** 持平。 */
        FLAT
    }

    /**
     * 趋势多周期形态枚举。
     */
    public enum Pattern {
        /** 持续上升。 */
        CONTINUOUS_UP,
        /** 持续下降。 */
        CONTINUOUS_DOWN,
        /** 持续持平。 */
        FLAT,
        /** 波动起伏。 */
        FLUCTUATING,
        /** 周期样本不足（小于2个点）。 */
        INSUFFICIENT
    }

    /** 当期/末期实际数值。 */
    private final BigDecimal currentValue;

    /** 比较基准数值。 */
    private final BigDecimal comparisonValue;

    /** 绝对差额（currentValue - comparisonValue）。 */
    private final BigDecimal difference;

    /** 变化比率（difference / |comparisonValue|）。 */
    private final BigDecimal changeRate;

    /** 趋势方向。 */
    private final Direction direction;

    /** 趋势形态模式。 */
    private final Pattern pattern;

    /** 最大值点。 */
    private final TrendAnalyzer.TrendPoint maximum;

    /** 最小值点。 */
    private final TrendAnalyzer.TrendPoint minimum;

    /** 超出异常阈值的周期标签列表。 */
    private final List<String> abnormalPeriods;

    /** 是否被空数据策略跳过。 */
    private final boolean skipped;

    /** 提示或降级文案消息。 */
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
