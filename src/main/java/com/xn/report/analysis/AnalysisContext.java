package com.xn.report.analysis;

import com.xn.report.chart.ChartModel;
import com.xn.report.chart.RenderedChart;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.policy.ReportWarning;
import com.xn.report.rule.RuleResult;
import com.xn.report.text.NarrativeResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AnalysisContext {

    private final DatasetContext querySnapshot;
    private final DatasetContext datasetContext;
    private final Map<String, RuleResult> ruleResults;
    private final Map<String, NarrativeResult> narratives;
    private final Map<String, ChartModel> chartModels;
    private final Map<String, RenderedChart> renderedCharts;
    private final List<ReportWarning> warnings;

    public AnalysisContext(
            DatasetContext querySnapshot,
            DatasetContext datasetContext,
            Map<String, RuleResult> ruleResults,
            Map<String, NarrativeResult> narratives,
            Map<String, ChartModel> chartModels,
            Map<String, RenderedChart> renderedCharts,
            List<ReportWarning> warnings) {
        this.querySnapshot =
                Objects.requireNonNull(querySnapshot, "querySnapshot");
        this.datasetContext =
                Objects.requireNonNull(datasetContext, "datasetContext");
        this.ruleResults = immutableMap(ruleResults);
        this.narratives = immutableMap(narratives);
        this.chartModels = immutableMap(chartModels);
        this.renderedCharts = immutableMap(renderedCharts);
        this.warnings = Collections.unmodifiableList(
                new ArrayList<ReportWarning>(
                        warnings == null
                                ? Collections.<ReportWarning>emptyList()
                                : warnings));
    }

    public static AnalysisContext empty(DatasetContext datasets) {
        return new AnalysisContext(
                datasets, datasets,
                Collections.<String, RuleResult>emptyMap(),
                Collections.<String, NarrativeResult>emptyMap(),
                Collections.<String, ChartModel>emptyMap(),
                Collections.<String, RenderedChart>emptyMap(),
                Collections.<ReportWarning>emptyList());
    }

    public AnalysisContext withWarning(
            String action,
            String scopeType,
            String scopeId,
            String message) {
        List<ReportWarning> copy = new ArrayList<ReportWarning>(warnings);
        copy.add(new ReportWarning(action, scopeType, scopeId, message));
        return new AnalysisContext(
                querySnapshot, datasetContext, ruleResults, narratives,
                chartModels, renderedCharts, copy);
    }

    public DatasetContext getQuerySnapshot() {
        return querySnapshot;
    }

    public DatasetContext getDatasetContext() {
        return datasetContext;
    }

    public Map<String, RuleResult> getRuleResults() {
        return ruleResults;
    }

    public Map<String, NarrativeResult> getNarratives() {
        return narratives;
    }

    public Map<String, ChartModel> getChartModels() {
        return chartModels;
    }

    public Map<String, RenderedChart> getRenderedCharts() {
        return renderedCharts;
    }

    public List<ReportWarning> getWarnings() {
        return warnings;
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> values) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, T>(
                        values == null
                                ? Collections.<String, T>emptyMap()
                                : values));
    }
}
