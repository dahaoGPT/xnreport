package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import java.util.Collections;
import java.util.Map;

public final class NarrativeEngine {

    private final TextRenderer renderer;

    public NarrativeEngine(TextRenderer renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException("Text renderer is required");
        }
        this.renderer = renderer;
    }

    public NarrativeResult generate(
            NarrativeDefinition definition,
            TextRenderContext context,
            Map<String, Object> analysisValues) {
        if (definition == null
                || definition.getSourceType() == null
                || context == null) {
            throw new IllegalArgumentException(
                    "Narrative definition, source type, and context are required");
        }
        validateShape(definition);
        Map<String, Object> values = analysisValues == null
                ? Collections.<String, Object>emptyMap() : analysisValues;
        if (definition.getSourceType()
                == NarrativeDefinition.SourceType.FIXED_TEMPLATE) {
            requireText(definition.getTemplate(), "Fixed narrative template");
            return new NarrativeResult(
                    renderer.render(definition.getTemplate(), context),
                    definition.getSourceType(),
                    definition.getDataset(),
                    null,
                    Collections.<String, Object>emptyMap(),
                    false);
        }
        requireText(definition.getAnalyzer(), "Rule narrative analyzer");
        requireText(definition.getDataset(), "Rule narrative dataset");
        requireText(definition.getSentence(), "Rule narrative sentence");
        if (values.isEmpty()) {
            return emptyResult(definition);
        }
        return new NarrativeResult(
                renderer.render(
                        definition.getSentence(), context.withSummary(values)),
                definition.getSourceType(),
                definition.getDataset(),
                definition.getAnalyzer(),
                values,
                false);
    }

    private static void validateShape(NarrativeDefinition definition) {
        if (definition.hasProperty("emptyStrategy")
                && definition.getEmptyStrategy() == null) {
            throw new IllegalArgumentException(
                    "Narrative emptyStrategy must not be null");
        }
        if (definition.hasProperty("parameters")
                && definition.getParameters() == null) {
            throw new IllegalArgumentException(
                    "Narrative parameters must not be null");
        }
        if (definition.getSourceType()
                == NarrativeDefinition.SourceType.FIXED_TEMPLATE) {
            rejectPresent(definition, "analyzer", "baseline", "format",
                    "sentence", "distribution");
        } else {
            rejectPresent(definition, "template");
        }
    }

    private static void rejectPresent(
            NarrativeDefinition definition, String... properties) {
        for (String property : properties) {
            if (definition.hasProperty(property)) {
                throw new IllegalArgumentException(
                        property + " is not allowed for "
                                + definition.getSourceType());
            }
        }
    }

    private static NarrativeResult emptyResult(NarrativeDefinition definition) {
        NarrativeDefinition.EmptyStrategy strategy =
                definition.getEmptyStrategy() == null
                        ? NarrativeDefinition.EmptyStrategy.FAIL
                        : definition.getEmptyStrategy();
        if (strategy == NarrativeDefinition.EmptyStrategy.FAIL) {
            throw new TextRenderException(
                    "Narrative analysis values are empty: " + definition.getId());
        }
        boolean skipped = strategy == NarrativeDefinition.EmptyStrategy.SKIP;
        String text = skipped ? "" : emptyMessage(definition);
        return new NarrativeResult(
                text,
                definition.getSourceType(),
                definition.getDataset(),
                definition.getAnalyzer(),
                Collections.<String, Object>emptyMap(),
                skipped);
    }

    private static String emptyMessage(NarrativeDefinition definition) {
        Object configured = definition.getParameters() == null
                ? null : definition.getParameters().get("emptyMessage");
        return configured == null ? "暂无数据" : String.valueOf(configured);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
