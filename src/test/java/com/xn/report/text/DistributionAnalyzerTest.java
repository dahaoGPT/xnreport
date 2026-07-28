package com.xn.report.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.DistributionDefinition.BinDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.dataset.DatasetRow;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistributionAnalyzerTest {

    private final DistributionAnalyzer analyzer = new DistributionAnalyzer();

    @Test
    void usesMutuallyExclusiveOpenClosedBoundariesAndSharedLabels() {
        DistributionDefinition definition = distribution();
        List<DatasetRow> rows = Arrays.asList(
                row("0"), row("24"), row("24.0001"),
                row("168"), row("168.0001"), row(null));

        DistributionResult result = analyzer.analyze(
                rows, definition, NarrativeDefinition.EmptyStrategy.FAIL);

        assertThat(result.total()).isEqualTo(5);
        assertThat(result.bins())
                .extracting(DistributionResult.BinResult::count)
                .containsExactly(2, 2, 1);
        assertThat(result.bins())
                .extracting(DistributionResult.BinResult::percent)
                .containsExactly(
                        new BigDecimal("0.4000000000"),
                        new BigDecimal("0.4000000000"),
                        new BigDecimal("0.2000000000"));
        assertThat(result.bins())
                .extracting(DistributionResult.BinResult::displayLabel)
                .containsExactly("1天之内 2 (40.00%)",
                        "7天之内 2 (40.00%)", "7天以上 1 (20.00%)");
        assertThatThrownBy(() -> result.bins().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void supportsCountAndPercentLabelModes() {
        DistributionDefinition count = distribution();
        count.setLabelMode(DistributionDefinition.LabelMode.COUNT);
        assertThat(analyzer.analyze(
                Collections.singletonList(row("1")), count,
                NarrativeDefinition.EmptyStrategy.FAIL)
                .bins().get(0).displayLabel()).isEqualTo("1天之内 1");

        DistributionDefinition percent = distribution();
        percent.setLabelMode(DistributionDefinition.LabelMode.PERCENT);
        assertThat(analyzer.analyze(
                Collections.singletonList(row("1")), percent,
                NarrativeDefinition.EmptyStrategy.FAIL)
                .bins().get(0).displayLabel()).isEqualTo("1天之内 100.00%");
    }

    @Test
    void validatesOverlapsGapsTypesAndEmptyPolicies() {
        DistributionDefinition overlap = distribution();
        overlap.getBins().get(1).setMinInclusive(true);
        assertThatThrownBy(() -> analyzer.analyze(
                Collections.singletonList(row("24")), overlap,
                NarrativeDefinition.EmptyStrategy.FAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");

        assertThatThrownBy(() -> analyzer.analyze(
                Collections.singletonList(DatasetRow.of("approvalHours", "not-number")),
                distribution(),
                NarrativeDefinition.EmptyStrategy.FAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");

        DistributionResult empty = analyzer.analyze(
                Collections.<DatasetRow>emptyList(),
                distribution(),
                NarrativeDefinition.EmptyStrategy.OUTPUT_MESSAGE);
        assertThat(empty.total()).isZero();
        assertThat(empty.empty()).isTrue();
        assertThat(empty.message()).isNotBlank();

        assertThat(analyzer.analyze(
                Collections.<DatasetRow>emptyList(),
                distribution(),
                NarrativeDefinition.EmptyStrategy.SKIP).skipped()).isTrue();

        DistributionDefinition explicitNull = distribution();
        explicitNull.getBins().get(0).setMinInclusive(null);
        assertThatThrownBy(() -> analyzer.analyze(
                Collections.singletonList(row("1")),
                explicitNull,
                NarrativeDefinition.EmptyStrategy.FAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minInclusive");
    }

    private static DatasetRow row(String value) {
        return DatasetRow.of("approvalHours",
                value == null ? null : new BigDecimal(value));
    }

    private static DistributionDefinition distribution() {
        DistributionDefinition definition = new DistributionDefinition();
        definition.setField("approvalHours");
        definition.setBins(Arrays.asList(
                bin("within1Day", "1天之内", null, false, "24", true),
                bin("within7Days", "7天之内", "24", false, "168", true),
                bin("over7Days", "7天以上", "168", false, null, false)));
        definition.setLabelMode(DistributionDefinition.LabelMode.COUNT_AND_PERCENT);
        return definition;
    }

    private static BinDefinition bin(
            String id,
            String label,
            String min,
            boolean minInclusive,
            String max,
            boolean maxInclusive) {
        BinDefinition bin = new BinDefinition();
        bin.setId(id);
        bin.setLabel(label);
        if (min != null) {
            bin.setMin(new BigDecimal(min));
        }
        bin.setMinInclusive(minInclusive);
        if (max != null) {
            bin.setMax(new BigDecimal(max));
        }
        bin.setMaxInclusive(maxInclusive);
        return bin;
    }
}
