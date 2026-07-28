package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DatasetRow {

    private static final DatasetRow EMPTY =
            new DatasetRow(Collections.<String, Object>emptyMap());

    private final Map<String, Object> values;
    private final Map<String, String> lowerCaseToOriginal;
    private final List<String> fieldNames;

    private DatasetRow(Map<String, Object> source) {
        LinkedHashMap<String, Object> copiedValues =
                new LinkedHashMap<String, Object>();
        LinkedHashMap<String, String> copiedIndex =
                new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String field = requireField(entry.getKey());
            String normalized = normalize(field);
            if (copiedIndex.containsKey(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate field ignoring case: " + field);
            }
            copiedValues.put(field, entry.getValue());
            copiedIndex.put(normalized, field);
        }
        this.values = Collections.unmodifiableMap(copiedValues);
        this.lowerCaseToOriginal = Collections.unmodifiableMap(copiedIndex);
        this.fieldNames = Collections.unmodifiableList(
                new ArrayList<String>(copiedValues.keySet()));
    }

    public static DatasetRow of(Object... pairs) {
        if (pairs == null || pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Dataset row values must be field/value pairs");
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, String> normalizedFields =
                new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) {
            if (!(pairs[index] instanceof String)) {
                throw new IllegalArgumentException("Dataset row field names must be strings");
            }
            String field = requireField((String) pairs[index]);
            String normalized = normalize(field);
            if (normalizedFields.containsKey(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate field ignoring case: " + field);
            }
            normalizedFields.put(normalized, field);
            values.put(field, pairs[index + 1]);
        }
        return values.isEmpty() ? EMPTY : new DatasetRow(values);
    }

    public static DatasetRow empty() {
        return EMPTY;
    }

    public Object get(String field) {
        String original = lowerCaseToOriginal.get(normalize(requireField(field)));
        if (original == null) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return values.get(original);
    }

    public Object getOrNull(String field) {
        String original = lowerCaseToOriginal.get(normalize(requireField(field)));
        return original == null ? null : values.get(original);
    }

    public boolean containsField(String field) {
        return lowerCaseToOriginal.containsKey(normalize(requireField(field)));
    }

    public List<String> fieldNames() {
        return fieldNames;
    }

    public Map<String, Object> asMap() {
        return values;
    }

    private static String requireField(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException("Dataset row field name must not be blank");
        }
        return field;
    }

    private static String normalize(String field) {
        return field.toLowerCase(Locale.ROOT);
    }
}
