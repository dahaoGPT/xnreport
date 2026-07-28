package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class NarrativeAnalyzerRegistry {

    private final Map<NarrativeDefinition.AnalyzerType, ControlledNarrativeAnalyzer>
            analyzers = new EnumMap<NarrativeDefinition.AnalyzerType,
                    ControlledNarrativeAnalyzer>(
                    NarrativeDefinition.AnalyzerType.class);

    public static NarrativeAnalyzerRegistry defaults() {
        NarrativeAnalyzerRegistry registry = new NarrativeAnalyzerRegistry();
        registry.register(new TrendNarrativeAnalyzer(new TrendAnalyzer()));
        registry.register(
                new DistributionNarrativeAnalyzer(new DistributionAnalyzer()));
        return registry;
    }

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
