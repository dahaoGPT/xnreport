package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 结构化智能叙述句分析器注册中心。
 * <p>
 * 默认注册趋势分析（TREND）与分布分析（DISTRIBUTION）两大分析引擎。
 * </p>
 */
public final class NarrativeAnalyzerRegistry {

    private final Map<NarrativeDefinition.AnalyzerType, ControlledNarrativeAnalyzer>
            analyzers = new EnumMap<NarrativeDefinition.AnalyzerType,
                    ControlledNarrativeAnalyzer>(
                    NarrativeDefinition.AnalyzerType.class);

    /**
     * 构建预装默认分析器的注册中心。
     */
    public static NarrativeAnalyzerRegistry defaults() {
        NarrativeAnalyzerRegistry registry = new NarrativeAnalyzerRegistry();
        registry.register(new TrendNarrativeAnalyzer(new TrendAnalyzer()));
        registry.register(
                new DistributionNarrativeAnalyzer(new DistributionAnalyzer()));
        return registry;
    }

    /**
     * 注册受控分析器实现。
     *
     * @param analyzer 分析器实例
     * @return this
     */
    public NarrativeAnalyzerRegistry register(
            ControlledNarrativeAnalyzer analyzer) {
        if (analyzer == null || analyzer.type() == null) {
            throw new IllegalArgumentException(
                    "Controlled narrative analyzer and type are required");
        }
        if (analyzers.containsKey(analyzer.type())) {
            throw new IllegalArgumentException(
                    "Duplicate controlled analyzer type: " + analyzer.type());
        }
        analyzers.put(analyzer.type(), analyzer);
        return this;
    }

    /**
     * 根据叙述句配置调用对应的分析器执行分析。
     *
     * @param definition 叙述句配置
     * @param context 渲染上下文
     * @return NarrativeAnalysis 分析结果
     */
    public NarrativeAnalysis analyze(
            NarrativeDefinition definition, TextRenderContext context) {
        ControlledNarrativeAnalyzer analyzer =
                analyzers.get(definition.getAnalyzerType());
        if (analyzer == null) {
            throw new IllegalArgumentException(
                    "No controlled analyzer registered for "
                            + definition.getAnalyzerType());
        }
        return analyzer.analyze(definition, context);
    }

    public Map<NarrativeDefinition.AnalyzerType, ControlledNarrativeAnalyzer>
            analyzers() {
        return Collections.unmodifiableMap(
                new EnumMap<NarrativeDefinition.AnalyzerType,
                        ControlledNarrativeAnalyzer>(analyzers));
    }
}
