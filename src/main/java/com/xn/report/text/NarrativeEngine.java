package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.TrendDefinition;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * 智能叙述句与文本生成综合驱动引擎。
 * <p>
 * 支持两种叙述句生成模式：
 * <ul>
 *   <li><b>固定模板生成（FIXED_TEMPLATE）</b>：直接基于上下文环境与通用模板（template）进行占位符插值渲染。</li>
 *   <li><b>受控规则生成（RULE_GENERATED）</b>：调度 {@link NarrativeAnalyzerRegistry} 运行结构化算法（趋势/分布），动态产出度量并渲染结论句（sentence）。</li>
 * </ul>
 * </p>
 */
public final class NarrativeEngine {

    /** 文本占位符渲染器。 */
    private final TextRenderer renderer;

    /** 受控分析器注册中心。 */
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

    /**
     * 生成单条叙述句结论文案与分析产物。
     *
     * @param definition 叙述句配置定义
     * @param context 渲染上下文环境
     * @return 综合结果 NarrativeResult
     */
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
     * 兼容性重载方法。
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
