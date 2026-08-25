package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.TrendDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 趋势分析智能叙述句受控分析器实现。
 * <p>
 * 从数据集中提取时序序列（按 yyyy-MM 排序），解析比对基准（支持 LITERAL、RUNTIME_PARAMETER、DATASET_FIELD、ANNUAL_BASELINE、PREVIOUS_YEAR 去年同期），
 * 调用 {@link TrendAnalyzer} 执行计算并展开包含 {@code current}, {@code comparison}, {@code difference}, {@code changeRate}, {@code direction}, {@code pattern}, {@code maximumPeriod}, {@code minimumPeriod} 等全套 summary 变量字典。
 * </p>
 */
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
        List<MonthlyTrendPoint> monthlyPoints =
                new ArrayList<MonthlyTrendPoint>();
        Set<YearMonth> periods = new HashSet<YearMonth>();
        for (DatasetRow row : dataset.list()) {
            requireField(row, definition.getPeriodField());
            requireField(row, definition.getValueField());
            Object period = row.getOrNull(definition.getPeriodField());
            Object value = row.getOrNull(definition.getValueField());
            YearMonth month = monthlyPeriod(period);
            if (!periods.add(month)) {
                throw new IllegalArgumentException(
                        "Duplicate trend period: " + month);
            }
            if (value == null) {
                continue;
            }
            monthlyPoints.add(new MonthlyTrendPoint(
                    month, numeric(value, "trend value")));
        }
        Collections.sort(monthlyPoints, new Comparator<MonthlyTrendPoint>() {
            @Override
            public int compare(
                    MonthlyTrendPoint left, MonthlyTrendPoint right) {
                return left.period.compareTo(right.period);
            }
        });
        List<TrendAnalyzer.TrendPoint> points =
                new ArrayList<TrendAnalyzer.TrendPoint>(monthlyPoints.size());
        for (MonthlyTrendPoint point : monthlyPoints) {
            points.add(new TrendAnalyzer.TrendPoint(
                    point.period.toString(), point.value));
        }
        BigDecimal comparison = points.isEmpty()
                ? null
                : resolveComparison(
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

    private static YearMonth monthlyPeriod(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Trend period must not be null");
        }
        if (value instanceof YearMonth) {
            return (YearMonth) value;
        }
        if (value instanceof CharSequence) {
            try {
                return YearMonth.parse(value.toString());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Trend period must use yyyy-MM: " + value,
                        exception);
            }
        }
        throw new IllegalArgumentException(
                "Unsupported trend period type "
                        + value.getClass().getSimpleName()
                        + "; expected yyyy-MM text or YearMonth");
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
            List<String> actualFields = dataset.schema().fieldNames();
            if (actualFields.size() != 1) {
                throw new IllegalArgumentException(
                        "Scalar comparison dataset " + datasetId
                                + " must expose exactly one schema field: "
                                + actualFields);
            }
            if (field == null || !dataset.schema().containsField(field)) {
                throw new IllegalArgumentException(
                        "Scalar comparison field " + field
                                + " does not match actual field "
                                + actualFields.get(0));
            }
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

    private static final class MonthlyTrendPoint {
        private final YearMonth period;
        private final BigDecimal value;

        private MonthlyTrendPoint(YearMonth period, BigDecimal value) {
            this.period = period;
            this.value = value;
        }
    }
}
