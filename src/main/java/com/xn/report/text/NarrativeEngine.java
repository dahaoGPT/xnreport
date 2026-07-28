package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.TrendDefinition;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

public final class NarrativeEngine {

    private final TextRenderer renderer;
    private final NarrativeAnalyzerRegistry analyzers;

    public NarrativeEngine(TextRenderer renderer) {
        this(renderer, NarrativeAnalyzerRegistry.defaults());
    }

    public NarrativeEngine(
            TextRenderer renderer, NarrativeAnalyzerRegistry analyzers) {
        if (renderer == null || analyzers == null) {
            throw new IllegalArgumentException(
                    "Text renderer and controlled analyzer registry are required");
        }
        this.renderer = renderer;
        this.analyzers = analyzers;
    }

    public NarrativeResult generate(
            NarrativeDefinition definition, TextRenderContext context) {
        requireBase(definition, context);
        validateShape(definition);
        if (definition.getSourceType()
                == NarrativeDefinition.SourceType.FIXED_TEMPLATE) {
            return new NarrativeResult(
                    renderer.render(definition.getTemplate(), context),
                    definition.getSourceType(),
                    definition.getDataset(),
                    null,
                    Collections.<String, Object>emptyMap(),
                    false,
                    null);
        }

        NarrativeAnalysis analysis = analyzers.analyze(definition, context);
        if (analysis.empty()) {
            String text = analysis.skipped()
                    ? "" : configuredEmptyMessage(definition, analysis.message());
            return new NarrativeResult(
                    text,
                    definition.getSourceType(),
                    definition.getDataset(),
                    definition.getAnalyzer(),
                    analysis.summary(),
                    analysis.skipped(),
                    analysis.analysisResult());
        }
        return new NarrativeResult(
                renderer.render(
                        definition.getSentence(),
                        context.withSummary(analysis.summary())),
                definition.getSourceType(),
                definition.getDataset(),
                definition.getAnalyzer(),
                analysis.summary(),
                false,
                analysis.analysisResult());
    }

    /**
     * Compatibility overload. External summaries are never accepted for
     * RULE_GENERATED narratives.
     */
    public NarrativeResult generate(
            NarrativeDefinition definition,
            TextRenderContext context,
            Map<String, Object> externalAnalysisValues) {
        if (definition != null
                && definition.getSourceType()
                == NarrativeDefinition.SourceType.RULE_GENERATED) {
            throw new IllegalArgumentException(
                    "RULE_GENERATED values must come from a controlled analyzer");
        }
        return generate(definition, context);
    }

    private static void requireBase(
            NarrativeDefinition definition, TextRenderContext context) {
        if (definition == null
                || definition.getSourceType() == null
                || context == null) {
            throw new IllegalArgumentException(
                    "Narrative definition, source type, and context are required");
        }
        requireText(definition.getId(), "Narrative id");
    }

    private static void validateShape(NarrativeDefinition definition) {
        rejectExplicitNull(
                definition, "emptyStrategy", definition.getEmptyStrategy());
        rejectExplicitNull(
                definition, "parameters", definition.getParameters());
        if (definition.getSourceType()
                == NarrativeDefinition.SourceType.FIXED_TEMPLATE) {
            requireText(definition.getTemplate(), "Fixed narrative template");
            rejectPresent(definition, "analyzer", "analyzerType", "baseline",
                    "format", "sentence", "distribution", "trend");
            return;
        }

        requireText(definition.getAnalyzer(), "Rule narrative analyzer");
        requireText(definition.getDataset(), "Rule narrative dataset");
        requireText(definition.getSentence(), "Rule narrative sentence");
        if (definition.getAnalyzerType() == null) {
            throw new IllegalArgumentException(
                    "Rule narrative analyzerType is required");
        }
        rejectPresent(definition, "template");
        rejectOptionalTextNull(definition, "baseline", definition.getBaseline());
        rejectOptionalTextNull(definition, "format", definition.getFormat());
        if (definition.getAnalyzerType()
                == NarrativeDefinition.AnalyzerType.TREND) {
            if (!definition.hasProperty("trend")
                    || definition.getTrend() == null) {
                throw new IllegalArgumentException(
                        "TREND narrative requires non-null trend");
            }
            rejectPresent(definition, "distribution");
            validateTrend(definition.getTrend());
        } else {
            if (!definition.hasProperty("distribution")
                    || definition.getDistribution() == null) {
                throw new IllegalArgumentException(
                        "DISTRIBUTION narrative requires non-null distribution");
            }
            rejectPresent(definition, "trend", "baseline");
        }
    }

    private static void validateTrend(TrendDefinition trend) {
        requireText(trend.getPeriodField(), "Trend periodField");
        requireText(trend.getValueField(), "Trend valueField");
        if (trend.getComparisonSource() == null) {
            throw new IllegalArgumentException(
                    "Trend comparisonSource is required");
        }
        rejectTrendNull(trend, "flatTolerance", trend.getFlatTolerance());
        rejectTrendNull(
                trend, "abnormalThreshold", trend.getAbnormalThreshold());
        if (trend.getFlatTolerance() == null
                || trend.getFlatTolerance().signum() < 0) {
            throw new IllegalArgumentException(
                    "Trend flatTolerance must be non-negative");
        }
        switch (trend.getComparisonSource()) {
            case LITERAL:
                requireTrendValue(trend, "comparisonValue",
                        trend.getComparisonValue());
                rejectTrendCross(trend, "comparisonDataset",
                        "comparisonField", "comparisonParameter");
                break;
            case RUNTIME_PARAMETER:
                requireText(trend.getComparisonParameter(),
                        "Trend comparisonParameter");
                rejectTrendCross(trend, "comparisonDataset",
                        "comparisonField", "comparisonValue");
                break;
            case DATASET_FIELD:
            case ANNUAL_BASELINE:
                requireText(trend.getComparisonField(),
                        "Trend comparisonField");
                if (trend.getComparisonSource()
                        == TrendDefinition.ComparisonSource.DATASET_FIELD) {
                    requireText(trend.getComparisonDataset(),
                            "Trend comparisonDataset");
                } else if (trend.hasProperty("comparisonDataset")
                        && trend.getComparisonDataset() == null) {
                    throw new IllegalArgumentException(
                            "Trend comparisonDataset must not be null");
                }
                rejectTrendCross(trend, "comparisonParameter", "comparisonValue");
                break;
            case PREVIOUS_YEAR:
                rejectTrendCross(trend, "comparisonDataset",
                        "comparisonField", "comparisonParameter",
                        "comparisonValue");
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported trend comparisonSource");
        }
    }

    private static void rejectTrendCross(
            TrendDefinition trend, String... properties) {
        for (String property : properties) {
            if (trend.hasProperty(property)) {
                throw new IllegalArgumentException(
                        property + " is not allowed for "
                                + trend.getComparisonSource());
            }
        }
    }

    private static void requireTrendValue(
            TrendDefinition trend, String property, BigDecimal value) {
        if (!trend.hasProperty(property) || value == null) {
            throw new IllegalArgumentException(
                    "Trend " + property + " is required");
        }
    }

    private static void rejectTrendNull(
            TrendDefinition trend, String property, Object value) {
        if (trend.hasProperty(property) && value == null) {
            throw new IllegalArgumentException(
                    "Trend " + property + " must not be null");
        }
    }

    private static void rejectOptionalTextNull(
            NarrativeDefinition definition, String property, String value) {
        if (definition.hasProperty(property) && value == null) {
            throw new IllegalArgumentException(
                    property + " must not be null");
        }
    }

    private static void rejectExplicitNull(
            NarrativeDefinition definition, String property, Object value) {
        if (definition.hasProperty(property) && value == null) {
            throw new IllegalArgumentException(
                    "Narrative " + property + " must not be null");
        }
    }

    private static void rejectPresent(
            NarrativeDefinition definition, String... properties) {
        for (String property : properties) {
            if (definition.hasProperty(property)) {
                throw new IllegalArgumentException(
                        property + " is not allowed for "
                                + (definition.getAnalyzerType() == null
                                ? definition.getSourceType()
                                : definition.getAnalyzerType()));
            }
        }
    }

    private static String configuredEmptyMessage(
            NarrativeDefinition definition, String fallback) {
        Object configured = definition.getParameters() == null
                ? null : definition.getParameters().get("emptyMessage");
        if (configured != null) {
            return String.valueOf(configured);
        }
        return fallback == null || fallback.isEmpty() ? "暂无数据" : fallback;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
