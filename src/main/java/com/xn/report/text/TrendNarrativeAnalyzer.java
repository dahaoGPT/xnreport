package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.TrendDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TrendNarrativeAnalyzer implements ControlledNarrativeAnalyzer {

    private final TrendAnalyzer analyzer;

    TrendNarrativeAnalyzer(TrendAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public NarrativeDefinition.AnalyzerType type() {
        return NarrativeDefinition.AnalyzerType.TREND;
    }

    @Override
    public NarrativeAnalysis analyze(
            NarrativeDefinition narrative, TextRenderContext context) {
        TrendDefinition definition = narrative.getTrend();
        DatasetResult dataset = requiredDataset(
                context, narrative.getDataset());
        if (dataset.type() != DatasetType.LIST) {
            throw new IllegalArgumentException(
                    "Trend analyzer requires LIST dataset: "
                            + dataset.id());
        }
        List<TrendAnalyzer.TrendPoint> points =
                new ArrayList<TrendAnalyzer.TrendPoint>();
        for (DatasetRow row : dataset.list()) {
            requireField(row, definition.getPeriodField());
            requireField(row, definition.getValueField());
            Object period = row.getOrNull(definition.getPeriodField());
            Object value = row.getOrNull(definition.getValueField());
            if (period == null || value == null) {
                continue;
            }
            points.add(new TrendAnalyzer.TrendPoint(
                    String.valueOf(period), numeric(value, "trend value")));
        }
        BigDecimal comparison = resolveComparison(
                narrative, definition, context, points);
        TrendResult result = analyzer.analyze(
                points,
                comparison,
                definition.getFlatTolerance(),
                definition.getAbnormalThreshold(),
                narrative.getEmptyStrategy());
        if (result.currentValue() == null) {
            return new NarrativeAnalysis(
                    java.util.Collections.<String, Object>emptyMap(),
                    true,
                    result.skipped(),
                    result.message(),
                    result);
        }
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("current", result.currentValue());
        summary.put("comparison", result.comparisonValue());
        summary.put("difference", result.difference());
        summary.put("changeRate", result.changeRate());
        summary.put("direction", result.direction().name());
        summary.put("pattern", result.pattern().name());
        summary.put("maximumPeriod", result.maximum().period());
        summary.put("maximumValue", result.maximum().value());
        summary.put("minimumPeriod", result.minimum().period());
        summary.put("minimumValue", result.minimum().value());
        summary.put("abnormalPeriods", result.abnormalPeriods());
        return new NarrativeAnalysis(summary, false, false, "", result);
    }

    private static BigDecimal resolveComparison(
            NarrativeDefinition narrative,
            TrendDefinition definition,
            TextRenderContext context,
            List<TrendAnalyzer.TrendPoint> points) {
        switch (definition.getComparisonSource()) {
            case LITERAL:
                return definition.getComparisonValue();
            case RUNTIME_PARAMETER:
                if (!context.runtime().containsKey(
                        definition.getComparisonParameter())) {
                    throw new IllegalArgumentException(
                            "Missing runtime comparison parameter: "
                                    + definition.getComparisonParameter());
                }
                return numeric(context.runtime().get(
                        definition.getComparisonParameter()),
                        "runtime comparison");
            case DATASET_FIELD:
                return datasetField(
                        context,
                        definition.getComparisonDataset(),
                        definition.getComparisonField());
            case ANNUAL_BASELINE:
                return datasetField(
                        context,
                        definition.getComparisonDataset() == null
                                ? narrative.getBaseline()
                                : definition.getComparisonDataset(),
                        definition.getComparisonField());
            case PREVIOUS_YEAR:
                if (points.isEmpty()) {
                    return BigDecimal.ZERO;
                }
                return previousYear(points);
            default:
                throw new IllegalArgumentException(
                        "Unsupported trend comparison source: "
                                + definition.getComparisonSource());
        }
    }

    private static BigDecimal previousYear(
            List<TrendAnalyzer.TrendPoint> points) {
        TrendAnalyzer.TrendPoint current = points.get(points.size() - 1);
        YearMonth target;
        try {
            target = YearMonth.parse(current.period()).minusYears(1);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "PREVIOUS_YEAR requires yyyy-MM periods", exception);
        }
        for (TrendAnalyzer.TrendPoint point : points) {
            if (target.toString().equals(point.period())) {
                return point.value();
            }
        }
        throw new IllegalArgumentException(
                "Missing previous-year comparison period: " + target);
    }

    private static BigDecimal datasetField(
            TextRenderContext context, String datasetId, String field) {
        DatasetResult dataset = requiredDataset(context, datasetId);
        if (dataset.type() == DatasetType.SCALAR) {
            return numeric(dataset.scalar(), "dataset comparison");
        }
        if (dataset.type() != DatasetType.SINGLE) {
            throw new IllegalArgumentException(
                    "Comparison dataset must be SCALAR or SINGLE: "
                            + datasetId);
        }
        DatasetRow row = dataset.single();
        if (row == null) {
            throw new IllegalArgumentException(
                    "Comparison dataset is empty: " + datasetId);
        }
        requireField(row, field);
        return numeric(row.getOrNull(field), "dataset comparison");
    }

    private static DatasetResult requiredDataset(
            TextRenderContext context, String id) {
        if (id == null || !context.datasets().contains(id)) {
            throw new IllegalArgumentException("Missing dataset: " + id);
        }
        return context.datasets().get(id);
    }

    private static void requireField(DatasetRow row, String field) {
        if (field == null || !row.containsField(field)) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
    }

    private static BigDecimal numeric(Object value, String label) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(label + " must be numeric");
        }
        return value instanceof BigDecimal
                ? (BigDecimal) value
                : new BigDecimal(String.valueOf(value));
    }
}
