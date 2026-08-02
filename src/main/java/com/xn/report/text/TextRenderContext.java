package com.xn.report.text;

import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import com.xn.report.rule.RuleResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TextRenderContext {

    private final DatasetRow currentRow;
    private final Map<String, Object> summary;
    private final Map<String, Object> runtime;
    private final DatasetContext datasets;
    private final Map<String, RuleResult> rules;

    private TextRenderContext(Builder builder) {
        this.currentRow = builder.currentRow == null
                ? DatasetRow.empty() : builder.currentRow;
        this.summary = TextValueSnapshot.map(builder.summary);
        this.runtime = TextValueSnapshot.map(builder.runtime);
        this.datasets = builder.datasets == null
                ? DatasetContext.builder().build() : builder.datasets;
        this.rules = Collections.unmodifiableMap(
                new LinkedHashMap<String, RuleResult>(builder.rules == null
                        ? Collections.<String, RuleResult>emptyMap()
                        : builder.rules));
    }

    public static Builder builder() {
        return new Builder();
    }

    public TextRenderContext withSummary(Map<String, Object> values) {
        return builder()
                .currentRow(currentRow)
                .summary(values)
                .runtime(runtime)
                .datasets(datasets)
                .rules(rules)
                .build();
    }

    Resolution resolve(String name) {
        if (name.startsWith("summary.")) {
            return fromMap(summary, name.substring("summary.".length()));
        }
        if (name.startsWith("runtime.")) {
            return fromMap(runtime, name.substring("runtime.".length()));
        }
        if (name.startsWith("dataset.")) {
            return fromDataset(name);
        }
        if (name.startsWith("rule.")) {
            return fromRule(name);
        }
        if (name.indexOf('.') >= 0) {
            return Resolution.missing();
        }
        int matchingScopes = currentRow.containsField(name) ? 1 : 0;
        matchingScopes += summary.containsKey(name) ? 1 : 0;
        matchingScopes += runtime.containsKey(name) ? 1 : 0;
        for (DatasetResult result : datasets.asMap().values()) {
            if (result.type() == DatasetType.SINGLE
                    && result.schema().containsField(name)) {
                matchingScopes++;
            } else if (result.type() == DatasetType.SCALAR
                    && (result.schema().containsField(name)
                    || "value".equals(name))) {
                matchingScopes++;
            }
        }
        if (matchingScopes > 1) {
            throw new TextRenderException(
                    "Ambiguous unqualified placeholder: " + name);
        }
        if (currentRow.containsField(name)) {
            return Resolution.found(currentRow.getOrNull(name));
        }
        if (matchingScopes == 1) {
            throw new TextRenderException(
                    "Cross-scope placeholder must be qualified: " + name);
        }
        return Resolution.missing();
    }

    DatasetContext datasets() {
        return datasets;
    }

    Map<String, Object> runtime() {
        return runtime;
    }

    private Resolution fromDataset(String name) {
        String reference = name.substring("dataset.".length());
        int separator = reference.indexOf('.');
        if (separator <= 0 || separator == reference.length() - 1) {
            return Resolution.missing();
        }
        String datasetId = reference.substring(0, separator);
        String field = reference.substring(separator + 1);
        if (!datasets.contains(datasetId)) {
            return Resolution.missing();
        }
        DatasetResult result = datasets.get(datasetId);
        Object value;
        if (result.type() == DatasetType.SCALAR) {
            if (!result.schema().containsField(field)
                    && !"value".equals(field)) {
                return Resolution.missing();
            }
            value = result.scalar();
        } else if (result.type() == DatasetType.SINGLE) {
            DatasetRow row = result.single();
            if (row == null || !row.containsField(field)) {
                return Resolution.missing();
            }
            value = row.getOrNull(field);
        } else {
            throw new TextRenderException(
                    "Dataset placeholder requires SCALAR or SINGLE dataset: "
                            + datasetId);
        }
        return Resolution.found(value);
    }

    private Resolution fromRule(String name) {
        String reference = name.substring("rule.".length());
        int separator = reference.indexOf('.');
        if (separator <= 0 || separator == reference.length() - 1) {
            return Resolution.missing();
        }
        RuleResult result = rules.get(reference.substring(0, separator));
        if (result == null) {
            return Resolution.missing();
        }
        String property = reference.substring(separator + 1);
        if ("matchedCount".equals(property)) {
            return Resolution.found(Integer.valueOf(
                    result.getMatchedRows().size()));
        }
        String prefix = "summary.";
        if (property.startsWith(prefix)
                && property.length() > prefix.length()) {
            return fromMap(result.getSummaryValues(),
                    property.substring(prefix.length()));
        }
        return Resolution.missing();
    }

    private static Resolution fromMap(Map<String, Object> values, String key) {
        return values.containsKey(key)
                ? Resolution.found(values.get(key)) : Resolution.missing();
    }

    static final class Resolution {
        private final boolean found;
        private final Object value;

        private Resolution(boolean found, Object value) {
            this.found = found;
            this.value = value;
        }

        static Resolution found(Object value) {
            return new Resolution(true, value);
        }

        static Resolution missing() {
            return new Resolution(false, null);
        }

        boolean found() {
            return found;
        }

        Object value() {
            return value;
        }
    }

    public static final class Builder {
        private DatasetRow currentRow;
        private Map<String, Object> summary;
        private Map<String, Object> runtime;
        private DatasetContext datasets;
        private Map<String, RuleResult> rules;

        public Builder currentRow(DatasetRow currentRow) {
            this.currentRow = currentRow;
            return this;
        }

        public Builder summary(Map<String, Object> summary) {
            this.summary = summary;
            return this;
        }

        public Builder runtime(Map<String, Object> runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder datasets(DatasetContext datasets) {
            this.datasets = datasets;
            return this;
        }

        public Builder rules(Map<String, RuleResult> rules) {
            this.rules = rules;
            return this;
        }

        public TextRenderContext build() {
            return new TextRenderContext(this);
        }
    }
}
