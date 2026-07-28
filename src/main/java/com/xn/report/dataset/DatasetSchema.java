package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DatasetSchema {

    private enum InferenceState {
        UNKNOWN,
        RESOLVED,
        CONFLICT
    }

    private static final DatasetSchema EMPTY =
            new DatasetSchema(Collections.<String, Class<?>>emptyMap());

    private final Map<String, Class<?>> fieldTypes;
    private final Map<String, String> lowerCaseToOriginal;
    private final List<String> fieldNames;

    private DatasetSchema(Map<String, Class<?>> source) {
        LinkedHashMap<String, Class<?>> copiedTypes =
                new LinkedHashMap<String, Class<?>>();
        LinkedHashMap<String, String> copiedIndex =
                new LinkedHashMap<String, String>();
        for (Map.Entry<String, Class<?>> entry : source.entrySet()) {
            String field = requireField(entry.getKey());
            Class<?> type = requireType(entry.getValue(), field);
            String normalized = normalize(field);
            if (copiedIndex.containsKey(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate schema field ignoring case: " + field);
            }
            copiedTypes.put(field, type);
            copiedIndex.put(normalized, field);
        }
        this.fieldTypes = Collections.unmodifiableMap(copiedTypes);
        this.lowerCaseToOriginal = Collections.unmodifiableMap(copiedIndex);
        this.fieldNames = Collections.unmodifiableList(
                new ArrayList<String>(copiedTypes.keySet()));
    }

    public static DatasetSchema of(Object... pairs) {
        if (pairs == null || pairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Dataset schema values must be field/type pairs");
        }
        LinkedHashMap<String, Class<?>> fields =
                new LinkedHashMap<String, Class<?>>();
        LinkedHashMap<String, String> normalizedFields =
                new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) {
            if (!(pairs[index] instanceof String)) {
                throw new IllegalArgumentException(
                        "Dataset schema field names must be strings");
            }
            String field = requireField((String) pairs[index]);
            if (!(pairs[index + 1] instanceof Class<?>)) {
                throw new IllegalArgumentException(
                        "Dataset schema type for " + field + " must be a Class");
            }
            String normalized = normalize(field);
            if (normalizedFields.containsKey(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate schema field ignoring case: " + field);
            }
            normalizedFields.put(normalized, field);
            fields.put(field, (Class<?>) pairs[index + 1]);
        }
        return fields.isEmpty() ? EMPTY : new DatasetSchema(fields);
    }

    public static DatasetSchema empty() {
        return EMPTY;
    }

    static DatasetSchema infer(List<DatasetRow> rows) {
        LinkedHashMap<String, Class<?>> types =
                new LinkedHashMap<String, Class<?>>();
        LinkedHashMap<String, String> normalizedFields =
                new LinkedHashMap<String, String>();
        LinkedHashMap<String, InferenceState> states =
                new LinkedHashMap<String, InferenceState>();
        for (DatasetRow row : rows) {
            for (Map.Entry<String, Object> entry : row.asMap().entrySet()) {
                String normalized = normalize(entry.getKey());
                String original = normalizedFields.get(normalized);
                if (original == null) {
                    normalizedFields.put(normalized, entry.getKey());
                    if (entry.getValue() == null) {
                        types.put(entry.getKey(), Object.class);
                        states.put(normalized, InferenceState.UNKNOWN);
                    } else {
                        types.put(
                                entry.getKey(),
                                DatasetValues.schemaType(entry.getValue()));
                        states.put(normalized, InferenceState.RESOLVED);
                    }
                    continue;
                }

                InferenceState state = states.get(normalized);
                if (state == InferenceState.CONFLICT || entry.getValue() == null) {
                    continue;
                }
                Class<?> valueType = DatasetValues.schemaType(entry.getValue());
                if (state == InferenceState.UNKNOWN) {
                    types.put(original, valueType);
                    states.put(normalized, InferenceState.RESOLVED);
                    continue;
                }
                Class<?> merged = merge(types.get(original), valueType);
                types.put(original, merged);
                if (merged == Object.class) {
                    states.put(normalized, InferenceState.CONFLICT);
                }
            }
        }
        return types.isEmpty() ? EMPTY : new DatasetSchema(types);
    }

    public Class<?> typeOf(String field) {
        String original = lowerCaseToOriginal.get(normalize(requireField(field)));
        if (original == null) {
            throw new IllegalArgumentException("Missing schema field: " + field);
        }
        return fieldTypes.get(original);
    }

    public boolean containsField(String field) {
        return lowerCaseToOriginal.containsKey(normalize(requireField(field)));
    }

    public List<String> fieldNames() {
        return fieldNames;
    }

    public Map<String, Class<?>> asMap() {
        return fieldTypes;
    }

    private static Class<?> merge(Class<?> current, Class<?> candidate) {
        if (current.equals(candidate)) {
            return current;
        }
        if (current.isAssignableFrom(candidate)) {
            return current;
        }
        if (candidate.isAssignableFrom(current)) {
            return candidate;
        }
        return Object.class;
    }

    private static String requireField(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException("Dataset schema field name must not be blank");
        }
        return field;
    }

    private static Class<?> requireType(Class<?> type, String field) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Dataset schema type must not be null for " + field);
        }
        return type;
    }

    private static String normalize(String field) {
        return field.toLowerCase(Locale.ROOT);
    }
}
