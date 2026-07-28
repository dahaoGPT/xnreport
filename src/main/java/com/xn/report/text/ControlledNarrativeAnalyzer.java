package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;

public interface ControlledNarrativeAnalyzer {

    NarrativeDefinition.AnalyzerType type();

    NarrativeAnalysis analyze(
            NarrativeDefinition definition, TextRenderContext context);
}
