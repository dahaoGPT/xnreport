package com.xn.report.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
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

        NarrativeResult result = engine.generate(
                definition,
                TestFixtures.textContext(),
                Collections.<String, Object>emptyMap());

        assertThat(result.text()).isEqualTo("周期2026H1，标准10.00小时");
        assertThat(result.sourceType())
                .isEqualTo(NarrativeDefinition.SourceType.FIXED_TEMPLATE);
        assertThat(result.datasetId()).isEqualTo("baseline");
        assertThat(result.analyzerId()).isNull();
        assertThat(result.summaryValues()).isEmpty();
    }

    @Test
    void generatesRuleNarrativeFromControlledAnalysisValues() {
        NarrativeDefinition definition = new NarrativeDefinition();
        definition.setId("trendText");
        definition.setSourceType(NarrativeDefinition.SourceType.RULE_GENERATED);
        definition.setDataset("monthly");
        definition.setAnalyzer("approvalTrend");
        definition.setSentence(
                "本月${summary.current|number:0.00}小时，方向${summary.direction}");
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("current", new BigDecimal("12.5"));
        values.put("direction", "UP");

        NarrativeResult result =
                engine.generate(definition, TestFixtures.textContext(), values);

        assertThat(result.text()).isEqualTo("本月12.50小时，方向UP");
        assertThat(result.sourceType())
                .isEqualTo(NarrativeDefinition.SourceType.RULE_GENERATED);
        assertThat(result.datasetId()).isEqualTo("monthly");
        assertThat(result.analyzerId()).isEqualTo("approvalTrend");
        assertThat(result.summaryValues()).containsEntry(
                "current", new BigDecimal("12.5"));
        assertThatThrownBy(() -> result.summaryValues().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void appliesConfiguredEmptyDataStrategyWithoutInventingAnalysis() {
        NarrativeDefinition skip = new NarrativeDefinition();
        skip.setId("empty");
        skip.setSourceType(NarrativeDefinition.SourceType.RULE_GENERATED);
        skip.setAnalyzer("trend");
        skip.setDataset("monthly");
        skip.setSentence("${summary.current}");
        skip.setEmptyStrategy(NarrativeDefinition.EmptyStrategy.SKIP);

        NarrativeResult result = engine.generate(
                skip, TestFixtures.textContext(), Collections.<String, Object>emptyMap());

        assertThat(result.skipped()).isTrue();
        assertThat(result.text()).isEmpty();

        skip.setEmptyStrategy(NarrativeDefinition.EmptyStrategy.FAIL);
        assertThatThrownBy(() -> engine.generate(
                skip,
                TestFixtures.textContext(),
                Collections.<String, Object>emptyMap()))
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
                fixed,
                TestFixtures.textContext(),
                Collections.<String, Object>emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");

        NarrativeDefinition generated = new NarrativeDefinition();
        generated.setId("generated");
        generated.setSourceType(NarrativeDefinition.SourceType.RULE_GENERATED);
        generated.setAnalyzer("trend");
        generated.setDataset("monthly");
        generated.setSentence("${summary.current}");
        generated.setEmptyStrategy(null);

        assertThatThrownBy(() -> engine.generate(
                generated,
                TestFixtures.textContext(),
                Collections.singletonMap("current", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emptyStrategy");
    }
}
