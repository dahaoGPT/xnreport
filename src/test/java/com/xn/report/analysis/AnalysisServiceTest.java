package com.xn.report.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.chart.ChartType;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformType;
import com.xn.report.config.definition.ConditionDefinition;
import com.xn.report.config.definition.ValueReferenceDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.support.TestFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

    @Test
    void sharesOneSnapshotAndRunsTransformsRulesNarrativesAndGroupedCharts()
            throws Exception {
        Path temporary = Files.createTempDirectory(
                java.nio.file.Paths.get("target"), "analysis-service-");
        DatasetDefinition dataset = TestFixtures.dataset("pipeline");
        TransformDefinition distinct = new TransformDefinition();
        distinct.setType(TransformType.DISTINCT);
        distinct.setFields(Collections.singletonList("name"));
        dataset.setTransforms(Collections.singletonList(distinct));
        ReportDefinition definition = TestFixtures.report(dataset);
        definition.setRules(
                Collections.singletonList(TestFixtures.pipelineRule()));

        NarrativeDefinition narrative = new NarrativeDefinition();
        narrative.setId("periodText");
        narrative.setSourceType(
                NarrativeDefinition.SourceType.FIXED_TEMPLATE);
        narrative.setTemplate("统计周期：${runtime.period}");
        definition.setNarratives(Collections.singletonList(narrative));

        ChartDefinition chart = new ChartDefinition();
        chart.setId("hoursByGroup");
        chart.setTitle("分组耗时");
        chart.setDataset("pipeline");
        chart.setCategoryField("name");
        chart.setGroupByField("group");
        chart.setWidthPixels(Integer.valueOf(480));
        chart.setHeightPixels(Integer.valueOf(300));
        chart.setDpi(Integer.valueOf(96));
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField("hours");
        series.setName("耗时");
        series.setType(ChartType.COLUMN);
        chart.setSeries(Collections.singletonList(series));
        definition.setCharts(Collections.singletonList(chart));

        DatasetContext snapshot = DatasetContext.builder()
                .put(TestFixtures.pipelineRows())
                .build();
        Path charts = temporary.resolve("charts");

        AnalysisContext result = new AnalysisService().analyze(
                definition,
                snapshot,
                Collections.<String, Object>singletonMap("period", "2026H1"),
                charts);

        assertThat(result.getQuerySnapshot()).isSameAs(snapshot);
        assertThat(snapshot.get("pipeline").list()).hasSize(4);
        assertThat(result.getDatasetContext().get("pipeline").list())
                .extracting(row -> row.get("name"))
                .containsExactly("A", "B", "C");
        assertThat(result.getRuleResults().get("pipeline").getMatchedRows())
                .extracting(row -> row.get("name"))
                .containsExactly("B", "A");
        assertThat(result.getNarratives().get("periodText").text())
                .isEqualTo("统计周期：2026H1");
        assertThat(result.getChartModels()).hasSize(2)
                .containsKey("hoursByGroup");
        assertThat(result.getChartModels().keySet())
                .anyMatch(key -> key.startsWith("hoursByGroup::002::"));
        assertThat(result.getRenderedCharts()).hasSize(2);
        assertThat(result.getRenderedCharts().values())
                .allSatisfy(image -> {
                    assertThat(image.getPath()).startsWith(
                            charts.toAbsolutePath().normalize());
                    assertThat(image.getPath()).exists();
                    assertThat(image.getDpi()).isEqualTo(96);
                });
        try (java.util.stream.Stream<Path> files = Files.list(charts)) {
            assertThat(files.filter(Files::isRegularFile)).hasSize(2);
        }
    }

    @Test
    void nestedAndOrRuleResultsFlowIntoFixedNarrative() throws Exception {
        ReportDefinition definition = TestFixtures.report(
                TestFixtures.dataset("pipeline"));
        RuleDefinition rule = TestFixtures.pipelineRule();
        ConditionDefinition eitherName = new ConditionDefinition();
        eitherName.setOperator(ConditionDefinition.Operator.OR);
        eitherName.setChildren(java.util.Arrays.asList(
                equalsName("A"), equalsName("B")));
        ConditionDefinition nested = new ConditionDefinition();
        nested.setOperator(ConditionDefinition.Operator.AND);
        nested.setChildren(java.util.Arrays.asList(
                rule.getCondition(), eitherName));
        rule.setCondition(nested);
        definition.setRules(Collections.singletonList(rule));
        NarrativeDefinition narrative = new NarrativeDefinition();
        narrative.setId("ruleText");
        narrative.setSourceType(NarrativeDefinition.SourceType.FIXED_TEMPLATE);
        narrative.setTemplate("matched=${rule.pipeline.matchedCount};max="
                + "${rule.pipeline.summary.maxHours|number:0.0}");
        definition.setNarratives(Collections.singletonList(narrative));
        definition.setCharts(Collections.emptyList());

        AnalysisContext result = new AnalysisService().analyze(
                definition,
                DatasetContext.builder().put(TestFixtures.pipelineRows()).build(),
                Collections.emptyMap(),
                Files.createTempDirectory(java.nio.file.Paths.get("target"),
                        "analysis-rule-text-"));

        assertThat(result.getNarratives().get("ruleText").text())
                .isEqualTo("matched=2;max=12.0");
    }

    private static ConditionDefinition equalsName(String value) {
        ValueReferenceDefinition left = new ValueReferenceDefinition();
        left.setSource(ValueReferenceDefinition.Source.CURRENT_FIELD);
        left.setField("name");
        ValueReferenceDefinition right = new ValueReferenceDefinition();
        right.setSource(ValueReferenceDefinition.Source.LITERAL);
        right.setValue(value);
        ConditionDefinition result = new ConditionDefinition();
        result.setOperator(ConditionDefinition.Operator.EQ);
        result.setLeft(left);
        result.setRight(right);
        return result;
    }
}
