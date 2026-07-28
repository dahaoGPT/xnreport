package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import java.util.Collections;
import java.util.Map;

public final class NarrativeResult {

    private final String text;
    private final NarrativeDefinition.SourceType sourceType;
    private final String datasetId;
    private final String analyzerId;
    private final Map<String, Object> summaryValues;
    private final boolean skipped;
    private final Object analysisResult;

    NarrativeResult(
            String text,
            NarrativeDefinition.SourceType sourceType,
            String datasetId,
            String analyzerId,
            Map<String, Object> summaryValues,
            boolean skipped,
            Object analysisResult) {
        this.text = text == null ? "" : text;
        this.sourceType = sourceType;
        this.datasetId = datasetId;
        this.analyzerId = analyzerId;
        this.summaryValues = TextValueSnapshot.map(summaryValues);
        this.skipped = skipped;
        this.analysisResult = analysisResult;
    }

    public String text() {
        return text;
    }

    public NarrativeDefinition.SourceType sourceType() {
        return sourceType;
    }

    public String datasetId() {
        return datasetId;
    }

    public String analyzerId() {
        return analyzerId;
    }

    public Map<String, Object> summaryValues() {
        return summaryValues;
    }

    public boolean skipped() {
        return skipped;
    }

    public Object analysisResult() {
        return analysisResult;
    }

}
