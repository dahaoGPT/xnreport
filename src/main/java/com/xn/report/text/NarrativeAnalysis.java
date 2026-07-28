package com.xn.report.text;

import java.util.Map;

public final class NarrativeAnalysis {

    private final Map<String, Object> summary;
    private final boolean empty;
    private final boolean skipped;
    private final String message;
    private final Object analysisResult;

    public NarrativeAnalysis(
            Map<String, Object> summary,
            boolean empty,
            boolean skipped,
            String message,
            Object analysisResult) {
        this.summary = TextValueSnapshot.map(summary);
        this.empty = empty;
        this.skipped = skipped;
        this.message = message == null ? "" : message;
        this.analysisResult = analysisResult;
    }

    public Map<String, Object> summary() {
        return summary;
    }

    public boolean empty() {
        return empty;
    }

    public boolean skipped() {
        return skipped;
    }

    public String message() {
        return message;
    }

    public Object analysisResult() {
        return analysisResult;
    }
}
