package com.xn.report.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.DistributionDefinition.BinDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.TrendDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NarrativeEngineTest {

    private final NarrativeEngine engine =
            new NarrativeEngine(TextRenderer.createDefault());

    @Test
    void generatesFixedTemplateWithTraceableSource() {
        NarrativeDefinition definition = new NarrativeDefinition();
        definition.setId("fixed");
        definition.setSourceType(NarrativeDefinition.SourceType.FIXED_TEMPLATE);
        definition.setDataset("baseline");
        definition.setTemplate(
                "周期${runtime.period}，标准${dataset.baseline.standardHours|number:0.00}小时");

        NarrativeResult result =
                engine.generate(definition, TestFixtures.textContext());

        assertThat(result.text()).isEqualTo("周期2026H1，标准10.00小时");
        assertThat(result.sourceType())
                .isEqualTo(NarrativeDefinition.SourceType.FIXED_TEMPLATE);
        assertThat(result.datasetId()).isEqualTo("baseline");
        assertThat(result.analyzerId()).isNull();
        assertThat(result.summaryValues()).isEmpty();
    }

    @Test
    void generatesRuleNarrativeOnlyFromControlledTrendAnalyzerAndDataset() {
        NarrativeDefinition definition = trendNarrative();

        NarrativeResult result =
                engine.generate(definition, trendContext());

        assertThat(result.text()).isEqualTo("本月12.00小时，方向UP");
        assertThat(result.sourceType())
                .isEqualTo(NarrativeDefinition.SourceType.RULE_GENERATED);
        assertThat(result.datasetId()).isEqualTo("monthly");
        assertThat(result.analyzerId()).isEqualTo("approvalTrend");
        assertThat(result.summaryValues()).containsEntry(
                "current", new BigDecimal("12"));
        assertThatThrownBy(() -> result.summaryValues().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void callerCannotForgeRuleGeneratedAnalysisValues() {
        Map<String, Object> forged = new LinkedHashMap<String, Object>();
        forged.put("current", new BigDecimal("9999"));
        forged.put("direction", "UP");

        assertThatThrownBy(() -> engine.generate(
                trendNarrative(), trendContext(), forged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("controlled");
    }

    @Test
    void generatesDistributionNarrativeFromDatasetBackedResult() {
        NarrativeResult result = engine.generate(
                distributionNarrative(), distributionContext());

        assertThat(result.text()).isEqualTo("共3条，1天内2条，占比66.67%");
        assertThat(result.summaryValues())
                .containsEntry("total", 3)
                .containsEntry("within1Day.count", 2)
                .containsEntry("within1Day.percent",
                        new BigDecimal("0.6666666667"));
        assertThat(result.analysisResult())
                .isInstanceOf(DistributionResult.class);
        DistributionResult shared =
                (DistributionResult) result.analysisResult();
        assertThat(shared.total()).isEqualTo(result.summaryValues().get("total"));
    }

    @Test
    void appliesConfiguredEmptyDataStrategyWithoutInventingAnalysis() {
        NarrativeDefinition skip = trendNarrative();
        skip.setId("empty");
        skip.setEmptyStrategy(NarrativeDefinition.EmptyStrategy.SKIP);
        TextRenderContext empty = emptyTrendContext();

        NarrativeResult result = engine.generate(skip, empty);

        assertThat(result.skipped()).isTrue();
        assertThat(result.text()).isEmpty();

        skip.setEmptyStrategy(NarrativeDefinition.EmptyStrategy.FAIL);
        assertThatThrownBy(() -> engine.generate(skip, empty))
                .isInstanceOf(TextRenderException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void runtimeFactoryRejectsCrossVariantAndExplicitNullConfiguration() {
        NarrativeDefinition fixed = new NarrativeDefinition();
        fixed.setId("fixed");
        fixed.setSourceType(NarrativeDefinition.SourceType.FIXED_TEMPLATE);
        fixed.setTemplate("text");
        fixed.setAnalyzer("forbidden");

        assertThatThrownBy(() -> engine.generate(
                fixed, TestFixtures.textContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");

        NarrativeDefinition generated = trendNarrative();
        generated.setEmptyStrategy(null);
        assertThatThrownBy(() -> engine.generate(generated, trendContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emptyStrategy");

        NarrativeDefinition crossBaseline = distributionNarrative();
        crossBaseline.setBaseline("forbidden");
        assertThatThrownBy(() -> engine.generate(
                crossBaseline, distributionContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseline")
                .hasMessageContaining("not allowed");

        NarrativeDefinition nullFormat = distributionNarrative();
        nullFormat.setFormat(null);
        assertThatThrownBy(() -> engine.generate(
                nullFormat, distributionContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("format");

        NarrativeDefinition nullDistribution = distributionNarrative();
        nullDistribution.setDistribution(null);
        assertThatThrownBy(() -> engine.generate(
                nullDistribution, distributionContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distribution");

        NarrativeDefinition nullId = distributionNarrative();
        nullId.setId(null);
        assertThatThrownBy(() -> engine.generate(
                nullId, distributionContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    private static NarrativeDefinition trendNarrative() {
        NarrativeDefinition definition = new NarrativeDefinition();
        definition.setId("trendText");
        definition.setSourceType(NarrativeDefinition.SourceType.RULE_GENERATED);
        definition.setDataset("monthly");
        definition.setAnalyzer("approvalTrend");
        definition.setAnalyzerType(NarrativeDefinition.AnalyzerType.TREND);
        definition.setSentence(
                "本月${summary.current|number:0.00}小时，方向${summary.direction}");
        TrendDefinition trend = new TrendDefinition();
        trend.setPeriodField("month");
        trend.setValueField("hours");
        trend.setComparisonSource(
                TrendDefinition.ComparisonSource.DATASET_FIELD);
        trend.setComparisonDataset("baseline");
        trend.setComparisonField("standardHours");
        trend.setFlatTolerance(BigDecimal.ZERO);
        trend.setAbnormalThreshold(new BigDecimal("11"));
        definition.setTrend(trend);
        return definition;
    }

    private static TextRenderContext trendContext() {
        DatasetContext datasets = DatasetContext.builder()
                .put(DatasetResult.list("monthly", Arrays.asList(
                        DatasetRow.of("month", "2026-01",
                                "hours", new BigDecimal("8")),
                        DatasetRow.of("month", "2026-02",
                                "hours", new BigDecimal("10")),
                        DatasetRow.of("month", "2026-03",
                                "hours", new BigDecimal("12")))))
                .put(DatasetResult.single("baseline",
                        Collections.singletonList(DatasetRow.of(
                                "standardHours", new BigDecimal("9")))))
                .build();
        return TextRenderContext.builder().datasets(datasets).build();
    }

    private static TextRenderContext emptyTrendContext() {
        return TextRenderContext.builder()
                .datasets(DatasetContext.builder()
                        .put(DatasetResult.list(
                                "monthly", Collections.<DatasetRow>emptyList()))
                        .put(DatasetResult.single("baseline",
                                Collections.singletonList(DatasetRow.of(
                                        "standardHours",
                                        new BigDecimal("9")))))
                        .build())
                .build();
    }

    private static NarrativeDefinition distributionNarrative() {
        NarrativeDefinition definition = new NarrativeDefinition();
        definition.setId("distributionText");
        definition.setSourceType(NarrativeDefinition.SourceType.RULE_GENERATED);
        definition.setDataset("monthly");
        definition.setAnalyzer("approvalDistribution");
        definition.setAnalyzerType(
                NarrativeDefinition.AnalyzerType.DISTRIBUTION);
        definition.setSentence("共${summary.total}条，1天内"
                + "${summary.within1Day.count}条，占比"
                + "${summary.within1Day.percent|percent:0.00}");
        DistributionDefinition distribution = new DistributionDefinition();
        distribution.setField("approvalHours");
        BinDefinition within = bin(
                "within1Day", "1天内", null, false, "24", true);
        BinDefinition over = bin(
                "over1Day", "超过1天", "24", false, null, false);
        distribution.setBins(Arrays.asList(within, over));
        distribution.setLabelMode(
                DistributionDefinition.LabelMode.COUNT_AND_PERCENT);
        definition.setDistribution(distribution);
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

    private static TextRenderContext distributionContext() {
        return TextRenderContext.builder()
                .datasets(DatasetContext.builder()
                        .put(DatasetResult.list("monthly", Arrays.asList(
                                DatasetRow.of("approvalHours",
                                        new BigDecimal("1")),
                                DatasetRow.of("approvalHours",
                                        new BigDecimal("24")),
                                DatasetRow.of("approvalHours",
                                        new BigDecimal("25")))))
                        .build())
                .build();
    }
}
