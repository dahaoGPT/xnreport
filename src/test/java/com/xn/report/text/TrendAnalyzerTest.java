package com.xn.report.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.NarrativeDefinition;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TrendAnalyzerTest {

    private final TrendAnalyzer analyzer = new TrendAnalyzer();

    @Test
    void calculatesBaselineDifferenceRateDirectionExtremesAndAbnormalPeriods() {
        TrendResult result = analyzer.analyze(
                Arrays.asList(
                        point("2026-01", "8"),
                        point("2026-02", "10"),
                        point("2026-03", "12")),
                new BigDecimal("9"),
                new BigDecimal("0.01"),
                new BigDecimal("11"),
                NarrativeDefinition.EmptyStrategy.FAIL);

        assertThat(result.currentValue()).isEqualByComparingTo("12");
        assertThat(result.comparisonValue()).isEqualByComparingTo("9");
        assertThat(result.difference()).isEqualByComparingTo("3");
        assertThat(result.changeRate()).isEqualByComparingTo("0.3333333333");
        assertThat(result.direction()).isEqualTo(TrendResult.Direction.UP);
        assertThat(result.pattern()).isEqualTo(TrendResult.Pattern.CONTINUOUS_UP);
        assertThat(result.maximum().period()).isEqualTo("2026-03");
        assertThat(result.minimum().period()).isEqualTo("2026-01");
        assertThat(result.abnormalPeriods()).containsExactly("2026-03");
    }

    @Test
    void detectsDownFlatAndFluctuatingWithBigDecimalTolerance() {
        assertThat(analyzer.analyze(
                Arrays.asList(point("01", "3"), point("02", "2"), point("03", "1")),
                new BigDecimal("2"),
                BigDecimal.ZERO,
                null,
                NarrativeDefinition.EmptyStrategy.FAIL).pattern())
                .isEqualTo(TrendResult.Pattern.CONTINUOUS_DOWN);

        TrendResult flat = analyzer.analyze(
                Arrays.asList(point("01", "1"), point("02", "1.005")),
                BigDecimal.ONE,
                new BigDecimal("0.01"),
                null,
                NarrativeDefinition.EmptyStrategy.FAIL);
        assertThat(flat.direction()).isEqualTo(TrendResult.Direction.FLAT);
        assertThat(flat.pattern()).isEqualTo(TrendResult.Pattern.FLAT);

        assertThat(analyzer.analyze(
                Arrays.asList(point("01", "1"), point("02", "3"), point("03", "2")),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                NarrativeDefinition.EmptyStrategy.FAIL).pattern())
                .isEqualTo(TrendResult.Pattern.FLUCTUATING);

        assertThat(analyzer.analyze(
                Arrays.asList(point("01", "1"), point("02", "1"), point("03", "2")),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                NarrativeDefinition.EmptyStrategy.FAIL).pattern())
                .isEqualTo(TrendResult.Pattern.FLUCTUATING);
    }

    @Test
    void handlesZeroAndNegativeComparisonsWithoutFloatingPointValues() {
        TrendResult zero = analyzer.analyze(
                Collections.singletonList(point("now", "-2")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                NarrativeDefinition.EmptyStrategy.FAIL);
        assertThat(zero.changeRate()).isNull();
        assertThat(zero.direction()).isEqualTo(TrendResult.Direction.DOWN);
    }

    @Test
    void appliesEmptyPolicyAndRejectsInvalidInputs() {
        assertThat(analyzer.analyze(
                Collections.<TrendAnalyzer.TrendPoint>emptyList(),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                null,
                NarrativeDefinition.EmptyStrategy.SKIP).skipped()).isTrue();
        assertThatThrownBy(() -> analyzer.analyze(
                Collections.<TrendAnalyzer.TrendPoint>emptyList(),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                null,
                NarrativeDefinition.EmptyStrategy.FAIL))
                .isInstanceOf(TextRenderException.class);
        assertThatThrownBy(() -> analyzer.analyze(
                Collections.singletonList(point("now", "1")),
                BigDecimal.TEN,
                new BigDecimal("-0.1"),
                null,
                NarrativeDefinition.EmptyStrategy.FAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TrendAnalyzer.TrendPoint point(String period, String value) {
        return new TrendAnalyzer.TrendPoint(period, new BigDecimal(value));
    }
}
