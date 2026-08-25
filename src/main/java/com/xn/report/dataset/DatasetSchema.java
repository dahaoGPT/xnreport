package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据集结构 Schema 元数据契约。
 * <p>
 * 定义数据集的列名及其对应的强类型 Class 类型信息。
 * 支持从现有数据行中进行自动类型推断（{@link #infer(List)}），并支持大小写不敏感的字段类型查询。
 * </p>
 */
public final class DatasetSchema {

    private enum InferenceState {
        UNKNOWN,
        RESOLVED,
        CONFLICT
    }

    /** 空 Schema 契约常量。 */
    private static final DatasetSchema EMPTY =
            new DatasetSchema(Collections.<String, Class<?>>emptyMap());

    /** 原始列名到 Java 类型的不可变映射。 */
    private final Map<String, Class<?>> fieldTypes;

    /** 小写列名到原始列名的索引映射。 */
    private final Map<String, String> lowerCaseToOriginal;

    /** 保持插入顺序的字段名列表。 */
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

    /**
     * 根据偶数个字段/类型对构造 Schema。
     *
     * @param pairs 格式为 "col1", Long.class, "col2", String.class...
     * @return DatasetSchema 实例
     */
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

    /**
     * 获取空 Schema 实例。
     *
     * @return 空 Schema
     */
    public static DatasetSchema empty() {
        return EMPTY;
    }

    /**
     * 从数据行集合推断 Schema 契约类型。
     *
     * @param rows 数据行列表
     * @return 推断出的 DatasetSchema
     */
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

    /**
     * 查询指定字段的 Java 类型（忽略大小写）。
     *
     * @param field 字段名
     * @return 字段对应的 Class 类型
     */
    public Class<?> typeOf(String field) {
        String original = lowerCaseToOriginal.get(normalize(requireField(field)));
        if (original == null) {
            throw new IllegalArgumentException("Missing schema field: " + field);
        }
        return fieldTypes.get(original);
    }

    /**
     * 检查是否包含指定字段（忽略大小写）。
     *
     * @param field 字段名
     * @return true 表示存在，false 表示不存在
     */
    public boolean containsField(String field) {
        return lowerCaseToOriginal.containsKey(normalize(requireField(field)));
    }

    /**
     * 获取所有列名列表。
     *
     * @return 列名列表
     */
    public List<String> fieldNames() {
        return fieldNames;
    }

    /**
     * 转换为只读 Map 映射。
     *
     * @return 字段名到 Class 类型的不可变 Map
     */
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
