package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据集单行记录不可变数据载体。
 * <p>
 * 封装单行记录的所有列键值映射（字段名称大小写不敏感匹配），
 * 在存入与读取时对所有可变值类型进行深度不可变防御性拷贝。
 * </p>
 */
public final class DatasetRow {

    /** 空记录单例常量。 */
    private static final DatasetRow EMPTY =
            new DatasetRow(Collections.<String, Object>emptyMap());

    /** 原始大小写列名字段到深拷贝列值的不可变映射。 */
    private final Map<String, Object> values;

    /** 小写列名到原始列名的索引映射（支持忽略大小写查询）。 */
    private final Map<String, String> lowerCaseToOriginal;

    /** 保持插入顺序的字段列名列表。 */
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
            copiedValues.put(field, DatasetValues.freeze(entry.getValue()));
            copiedIndex.put(normalized, field);
        }
        this.values = Collections.unmodifiableMap(copiedValues);
        this.lowerCaseToOriginal = Collections.unmodifiableMap(copiedIndex);
        this.fieldNames = Collections.unmodifiableList(
                new ArrayList<String>(copiedValues.keySet()));
    }

    /**
     * 根据偶数个键值对构造 DatasetRow。
     *
     * @param pairs 格式为 "key1", val1, "key2", val2...
     * @return DatasetRow 实例
     */
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

    /**
     * 获取空行实例。
     *
     * @return 空 DatasetRow
     */
    public static DatasetRow empty() {
        return EMPTY;
    }

    /**
     * 获取指定字段的值（忽略大小写，不存在时抛出异常）。
     *
     * @param field 字段列名
     * @return 字段值的安全副本
     * @throws IllegalArgumentException 如果字段不存在
     */
    public Object get(String field) {
        String original = lowerCaseToOriginal.get(normalize(requireField(field)));
        if (original == null) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return DatasetValues.copyForRead(values.get(original));
    }

    /**
     * 获取指定字段的值（忽略大小写，不存在时安全返回 null）。
     *
     * @param field 字段列名
     * @return 字段值的安全副本或 null
     */
    public Object getOrNull(String field) {
        String original = lowerCaseToOriginal.get(normalize(requireField(field)));
        return original == null
                ? null : DatasetValues.copyForRead(values.get(original));
    }

    /**
     * 检查当前行是否包含指定字段（忽略大小写）。
     *
     * @param field 字段名
     * @return true 表示存在，false 表示不存在
     */
    public boolean containsField(String field) {
        return lowerCaseToOriginal.containsKey(normalize(requireField(field)));
    }

    /**
     * 获取当前行所有字段列名列表。
     *
     * @return 字段名列表
     */
    public List<String> fieldNames() {
        return fieldNames;
    }

    /**
     * 转换为只读 Map 字典视图（值类型均做防御性深拷贝）。
     *
     * @return 字段名到值的不可变 Map
     */
    public Map<String, Object> asMap() {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            copy.put(entry.getKey(), DatasetValues.copyForRead(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
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
