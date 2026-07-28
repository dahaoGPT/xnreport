package com.xn.report.rule;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;

public final class ValueReference {

    public enum Source {
        LITERAL,
        CURRENT_FIELD,
        DATASET_FIELD,
        RUNTIME_PARAMETER
    }

    private final Source source;
    private final Object literal;
    private final String dataset;
    private final String field;
    private final String parameter;

    private ValueReference(
            Source source,
            Object literal,
            String dataset,
            String field,
            String parameter) {
        this.source = source;
        this.literal = literal;
        this.dataset = dataset;
        this.field = field;
        this.parameter = parameter;
    }

    public static ValueReference literal(Object value) {
        return new ValueReference(Source.LITERAL, value, null, null, null);
    }

    public static ValueReference currentField(String field) {
        return new ValueReference(
                Source.CURRENT_FIELD, null, null, requireText(field, "field"), null);
    }

    public static ValueReference datasetField(String dataset, String field) {
        return new ValueReference(
                Source.DATASET_FIELD,
                null,
                requireText(dataset, "dataset"),
                requireText(field, "field"),
                null);
    }

    public static ValueReference runtimeParameter(String parameter) {
        return new ValueReference(
                Source.RUNTIME_PARAMETER,
                null,
                null,
                null,
                requireText(parameter, "parameter"));
    }

    public Object resolve(RuleEvaluationContext context, DatasetRow row) {
        if (context == null || row == null) {
            throw new IllegalArgumentException("Rule context and current row are required");
        }
        switch (source) {
            case LITERAL:
                return literal;
            case CURRENT_FIELD:
                if (!row.containsField(field)) {
                    throw RuleErrors.reference("Missing current field: " + field);
                }
                return row.get(field);
            case RUNTIME_PARAMETER:
                return context.getRuntimeParameter(parameter);
            case DATASET_FIELD:
                return resolveDatasetField(context);
            default:
                throw RuleErrors.reference("Unsupported value source: " + source);
        }
    }

    private Object resolveDatasetField(RuleEvaluationContext context) {
        final DatasetResult result;
        try {
            result = context.getDatasets().get(dataset);
        } catch (IllegalArgumentException exception) {
            throw RuleErrors.reference("Missing referenced dataset: " + dataset);
        }
        DatasetRow sourceRow;
        if (result.type() == DatasetType.SINGLE) {
            sourceRow = result.single();
        } else if (result.type() == DatasetType.SCALAR) {
            if (result.schema().fieldNames().size() != 1
                    || !result.schema().fieldNames().get(0).equalsIgnoreCase(field)) {
                throw RuleErrors.reference(
                        "Scalar dataset field does not exist: " + dataset + "." + field);
            }
            return result.scalar();
        } else {
            throw RuleErrors.reference(
                    "DATASET_FIELD requires SCALAR or SINGLE dataset: " + dataset);
        }
        if (sourceRow == null || !sourceRow.containsField(field)) {
            throw RuleErrors.reference(
                    "Missing referenced dataset field: " + dataset + "." + field);
        }
        return sourceRow.get(field);
    }

    public Source getSource() {
        return source;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
