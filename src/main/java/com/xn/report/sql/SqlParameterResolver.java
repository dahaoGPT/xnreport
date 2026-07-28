package com.xn.report.sql;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ParameterBindingDefinition;
import com.xn.report.config.definition.ParameterSource;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SqlParameterResolver {

    public ResolvedSqlParameters resolve(
            DatasetDefinition definition,
            Map<String, Object> runtimeParameters,
            DatasetContext datasetContext) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(runtimeParameters, "runtimeParameters");
        Objects.requireNonNull(datasetContext, "datasetContext");

        Map<String, Object> resolved = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, ParameterBindingDefinition> entry
                : definition.getParameters().entrySet()) {
            String parameterName = requireText(entry.getKey(), "parameter name");
            ParameterBindingDefinition binding = entry.getValue();
            if (binding == null || binding.getFrom() == null) {
                throw new IllegalArgumentException(
                        "Parameter binding source is required: " + parameterName);
            }
            Object value = resolveValue(
                    parameterName, binding, runtimeParameters, datasetContext);
            resolved.put(parameterName, normalize(parameterName, value));
        }
        return new ResolvedSqlParameters(resolved);
    }

    private static Object resolveValue(
            String parameterName,
            ParameterBindingDefinition binding,
            Map<String, Object> runtimeParameters,
            DatasetContext datasetContext) {
        ParameterSource source = binding.getFrom();
        if (source == ParameterSource.RUNTIME) {
            String key = requireText(binding.getKey(), "runtime parameter key");
            if (!runtimeParameters.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Missing runtime parameter " + key
                                + " for SQL parameter " + parameterName);
            }
            return runtimeParameters.get(key);
        }
        if (source == ParameterSource.CONSTANT) {
            return binding.getValue();
        }
        if (source == ParameterSource.DATASET) {
            String datasetId = requireText(binding.getDataset(), "dataset id");
            String field = requireText(binding.getField(), "dataset field");
            DatasetResult result = datasetContext.get(datasetId);
            if (result.type() != DatasetType.SINGLE) {
                throw new IllegalArgumentException(
                        "Dataset parameter source must have SINGLE result type: "
                                + datasetId);
            }
            DatasetRow row = result.single();
            if (row == null) {
                throw new IllegalArgumentException(
                        "Dataset parameter source has no row: " + datasetId);
            }
            return row.get(field);
        }
        throw new IllegalArgumentException(
                "Unsupported parameter source for " + parameterName + ": " + source);
    }

    private static Object normalize(String parameterName, Object value) {
        if (value instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) value);
        }
        if (value instanceof LocalDate) {
            return java.sql.Date.valueOf((LocalDate) value);
        }
        if (value instanceof Collection<?>) {
            Collection<?> source = (Collection<?>) value;
            if (source.isEmpty()) {
                throw new IllegalArgumentException(
                        "SQL collection parameter must not be empty: " + parameterName);
            }
            List<Object> copy = new ArrayList<Object>(source.size());
            for (Object element : source) {
                copy.add(normalize(parameterName, element));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Timestamp) {
            Timestamp source = (Timestamp) value;
            Timestamp copy = new Timestamp(source.getTime());
            copy.setNanos(source.getNanos());
            return copy;
        }
        if (value instanceof java.sql.Date) {
            return new java.sql.Date(((java.sql.Date) value).getTime());
        }
        if (value instanceof Date) {
            return new Date(((Date) value).getTime());
        }
        return value;
    }

    private static String requireText(String value, String description) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return value;
    }
}
