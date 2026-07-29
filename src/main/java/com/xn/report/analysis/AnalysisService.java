package com.xn.report.analysis;

import com.xn.report.chart.ChartImageRenderer;
import com.xn.report.chart.ChartModel;
import com.xn.report.chart.ChartModelBuilder;
import com.xn.report.chart.ChartRenderOptions;
import com.xn.report.chart.JFreeChartImageRenderer;
import com.xn.report.chart.RenderedChart;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.policy.ReportWarning;
import com.xn.report.rule.RuleEngine;
import com.xn.report.rule.RuleEvaluationContext;
import com.xn.report.rule.RuleResult;
import com.xn.report.text.NarrativeEngine;
import com.xn.report.text.NarrativeResult;
import com.xn.report.text.TextRenderContext;
import com.xn.report.text.TextRenderer;
import com.xn.report.transform.TransformEngine;
import com.xn.report.transform.TransformFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AnalysisService {

    private final TransformFactory transformFactory;
    private final TransformEngine transformEngine;
    private final RuleEngine ruleEngine;
    private final NarrativeEngine narrativeEngine;
    private final ChartModelBuilder chartModelBuilder;
    private final ChartRendererFactory chartRendererFactory;

    public AnalysisService() {
        this(
                new TransformFactory(),
                new TransformEngine(),
                new RuleEngine(),
                new NarrativeEngine(TextRenderer.createDefault()),
                new ChartModelBuilder(),
                directory -> new JFreeChartImageRenderer(
                        directory, Collections.<String>emptyList()));
    }

    AnalysisService(
            TransformFactory transformFactory,
            TransformEngine transformEngine,
            RuleEngine ruleEngine,
            NarrativeEngine narrativeEngine,
            ChartModelBuilder chartModelBuilder,
            ChartRendererFactory chartRendererFactory) {
        this.transformFactory =
                Objects.requireNonNull(transformFactory, "transformFactory");
        this.transformEngine =
                Objects.requireNonNull(transformEngine, "transformEngine");
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine");
        this.narrativeEngine =
                Objects.requireNonNull(narrativeEngine, "narrativeEngine");
        this.chartModelBuilder =
                Objects.requireNonNull(chartModelBuilder, "chartModelBuilder");
        this.chartRendererFactory =
                Objects.requireNonNull(chartRendererFactory, "chartRendererFactory");
    }

    public AnalysisContext analyze(
            ReportDefinition definition,
            DatasetContext querySnapshot,
            Map<String, Object> runtimeParameters,
            Path chartDirectory) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(querySnapshot, "querySnapshot");
        Objects.requireNonNull(runtimeParameters, "runtimeParameters");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        DatasetContext transformed = transform(definition, querySnapshot);
        Map<String, RuleResult> rules =
                evaluateRules(definition, transformed, runtimeParameters);
        Map<String, NarrativeResult> narratives =
                renderNarratives(definition, transformed, runtimeParameters);
        ChartAnalysis charts = buildCharts(
                definition, transformed, chartDirectory);
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        for (Map.Entry<String, NarrativeResult> entry
                : narratives.entrySet()) {
            if (entry.getValue().skipped()) {
                warnings.add(new ReportWarning(
                        "SKIP", "narrative", entry.getKey(),
                        "Narrative was skipped by its empty-data strategy"));
            }
        }
        warnings.addAll(charts.warnings);
        return new AnalysisContext(
                querySnapshot,
                transformed,
                rules,
                narratives,
                charts.models,
                charts.rendered,
                warnings);
    }

    private DatasetContext transform(
            ReportDefinition definition, DatasetContext source) {
        Map<String, DatasetDefinition> definitions =
                new LinkedHashMap<String, DatasetDefinition>();
        for (DatasetDefinition item : definition.getDatasets()) {
            definitions.put(item.getId(), item);
        }
        DatasetContext.Builder result = DatasetContext.builder();
        for (String id : source.ids()) {
            DatasetResult dataset = source.get(id);
            DatasetDefinition configured = definitions.get(id);
            if (configured != null) {
                dataset = transformEngine.apply(
                        dataset,
                        transformFactory.createAll(configured.getTransforms()));
            }
            result.put(dataset);
        }
        return result.build();
    }

    private Map<String, RuleResult> evaluateRules(
            ReportDefinition definition,
            DatasetContext datasets,
            Map<String, Object> runtimeParameters) {
        Map<String, RuleResult> results =
                new LinkedHashMap<String, RuleResult>();
        RuleEvaluationContext context =
                new RuleEvaluationContext(datasets, runtimeParameters);
        for (RuleDefinition rule : definition.getRules()) {
            RuleResult result =
                    ruleEngine.evaluate(rule, datasets.get(rule.getDataset()), context);
            putUnique(results, rule.getId(), result, "rule");
        }
        return results;
    }

    private Map<String, NarrativeResult> renderNarratives(
            ReportDefinition definition,
            DatasetContext datasets,
            Map<String, Object> runtimeParameters) {
        Map<String, NarrativeResult> results =
                new LinkedHashMap<String, NarrativeResult>();
        TextRenderContext context = TextRenderContext.builder()
                .datasets(datasets)
                .runtime(runtimeParameters)
                .build();
        for (NarrativeDefinition narrative : definition.getNarratives()) {
            putUnique(
                    results,
                    narrative.getId(),
                    narrativeEngine.generate(narrative, context),
                    "narrative");
        }
        return results;
    }

    private ChartAnalysis buildCharts(
            ReportDefinition definition,
            DatasetContext datasets,
            Path chartDirectory) {
        Map<String, ChartModel> models =
                new LinkedHashMap<String, ChartModel>();
        Map<String, RenderedChart> rendered =
                new LinkedHashMap<String, RenderedChart>();
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        ChartImageRenderer renderer =
                chartRendererFactory.create(chartDirectory);
        for (ChartDefinition chart : definition.getCharts()) {
            List<ChartModel> groups = chartModelBuilder.buildAll(
                    chart, datasets.get(chart.getDataset()));
            if (groups.isEmpty()) {
                warnings.add(new ReportWarning(
                        "SKIP", "chart", chart.getId(),
                        "Chart was skipped by its empty-data strategy"));
                continue;
            }
            for (int index = 0; index < groups.size(); index++) {
                ChartModel model = groups.get(index);
                String logicalId = logicalChartId(
                        chart.getId(), model.getGroupKey(), index, groups.size());
                putUnique(models, logicalId, model, "chart model");
                RenderedChart image = renderer.render(
                        model,
                        new ChartRenderOptions(
                                chart.getWidthPixels().intValue(),
                                chart.getHeightPixels().intValue(),
                                chart.getDpi().intValue()));
                Path normalizedChartRoot =
                        chartDirectory.toAbsolutePath().normalize();
                if (!image.getPath().startsWith(normalizedChartRoot)) {
                    throw new IllegalStateException(
                            "Rendered chart escaped execution workspace: "
                                    + image.getPath());
                }
                putUnique(rendered, logicalId, image, "rendered chart");
            }
        }
        return new ChartAnalysis(models, rendered, warnings);
    }

    static String logicalChartId(
            String baseId, String groupKey, int index, int groupCount) {
        if (groupCount <= 1 || index == 0) {
            return baseId;
        }
        String stableGroup = groupKey == null ? "<null>" : groupKey;
        return baseId
                + "::"
                + String.format(java.util.Locale.ROOT, "%03d", index + 1)
                + "::"
                + Integer.toHexString(stableGroup.hashCode());
    }

    private static <T> void putUnique(
            Map<String, T> target, String id, T value, String kind) {
        if (target.put(id, value) != null) {
            throw new IllegalArgumentException(
                    "Duplicate " + kind + " logical id: " + id);
        }
    }

    @FunctionalInterface
    interface ChartRendererFactory {
        ChartImageRenderer create(Path directory);
    }

    private static final class ChartAnalysis {
        private final Map<String, ChartModel> models;
        private final Map<String, RenderedChart> rendered;
        private final List<ReportWarning> warnings;

        private ChartAnalysis(
                Map<String, ChartModel> models,
                Map<String, RenderedChart> rendered,
                List<ReportWarning> warnings) {
            this.models = models;
            this.rendered = rendered;
            this.warnings = warnings;
        }
    }
}
