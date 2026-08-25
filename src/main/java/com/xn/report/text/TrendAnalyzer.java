package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 时间序列趋势与异动分析计算器。
 * <p>
 * 对有序的时间序列点位（TrendPoint）进行多维趋势计算：
 * <ul>
 *   <li><b>差值与变动率</b>：计算末期值与基准比较值（comparisonValue）的绝对差额（difference）与变化率（changeRate）。</li>
 *   <li><b>趋势方向（{@link TrendResult.Direction}）</b>：依据容差阈值（flatTolerance）判定上升（UP）、下降（DOWN）或持平（FLAT）。</li>
 *   <li><b>时序形态（{@link TrendResult.Pattern}）</b>：识别连续上升（CONTINUOUS_UP）、连续下降（CONTINUOUS_DOWN）、波动（FLUCTUATING）或样本不足（INSUFFICIENT）。</li>
 *   <li><b>极值与异常识别</b>：找出最大值点、最小值点，以及超出 abnormalThreshold 异常阈值的周期列表。</li>
 * </ul>
 * </p>
 */
public final class TrendAnalyzer {

    /**
     * 执行时间序列趋势分析。
     *
     * @param points 时序数据点序列
     * @param comparisonValue 比较基准值
     * @param flatTolerance 持平容差
     * @param abnormalThreshold 异常告警阈值（可选）
     * @param emptyStrategy 空数据降级策略
     * @return 趋势分析结果 TrendResult
     */
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

    /**
     * 单个时间周期数值数据点。
     */
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
