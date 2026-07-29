package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One-pass typed index from rendered category labels to source values.
 */
final class ChartSourceCategoryIndex {

    interface Factory {
        ChartSourceCategoryIndex build(
                ChartDefinition definition,
                DatasetResult result);
    }

    private final Map<String, Map<String, SourceValue>> groups;
    private final String selectedGroup;

    private ChartSourceCategoryIndex(
            Map<String, Map<String, SourceValue>> groups,
            String selectedGroup) {
        this.groups = immutableGroups(groups);
        this.selectedGroup = selectedGroup;
    }

    static Factory factory() {
        return new Factory() {
            @Override
            public ChartSourceCategoryIndex build(
                    ChartDefinition definition,
                    DatasetResult result) {
                return ChartSourceCategoryIndex.build(
                        definition, result);
            }
        };
    }

    static ChartSourceCategoryIndex build(
            ChartDefinition definition,
            DatasetResult result) {
        return buildAll(definition, result, null);
    }

    static ChartSourceCategoryIndex build(
            ChartDefinition definition,
            DatasetResult result,
            String selectedGroupLabel) {
        return buildAll(
                definition, result, selectedGroupLabel);
    }

    private static ChartSourceCategoryIndex buildAll(
            ChartDefinition definition,
            DatasetResult result,
            String selectedGroupLabel) {
        if (result.type() != DatasetType.LIST) {
            return new ChartSourceCategoryIndex(
                    new LinkedHashMap<
                            String, Map<String, SourceValue>>(),
                    selectedGroupLabel);
        }
        Map<TypedKey, Map<String, SourceValue>> groups =
                new LinkedHashMap<TypedKey, Map<String, SourceValue>>();
        Map<String, TypedKey> groupLabels =
                new LinkedHashMap<String, TypedKey>();
        for (DatasetRow row : result.list()) {
            Object rawGroup = definition.getGroupByField() == null
                    ? null
                    : row.getOrNull(definition.getGroupByField());
            TypedKey group = TypedKey.of(rawGroup);
            ensureUniqueLabel(
                    groupLabels, group.label(), group, "group");
            Map<String, SourceValue> categories = groups.get(group);
            if (categories == null) {
                categories =
                        new LinkedHashMap<String, SourceValue>();
                groups.put(group, categories);
            }
            Object rawCategory =
                    row.getOrNull(definition.getCategoryField());
            TypedKey category = TypedKey.of(rawCategory);
            SourceValue previous = categories.get(category.label());
            if (previous == null) {
                categories.put(
                        category.label(),
                        new SourceValue(category, rawCategory));
            } else if (!previous.key.equals(category)) {
                throw new ChartBuildException(
                        "Chart category display label collision: "
                                + category.label());
            }
        }

        Map<String, Map<String, SourceValue>> byLabel =
                new LinkedHashMap<
                        String, Map<String, SourceValue>>();
        for (Map.Entry<TypedKey, Map<String, SourceValue>> group
                : groups.entrySet()) {
            String label = definition.getGroupByField() == null
                    ? null : group.getKey().label();
            byLabel.put(label, group.getValue());
        }
        return new ChartSourceCategoryIndex(
                byLabel, selectedGroupLabel);
    }

    Object source(String categoryLabel) {
        return source(selectedGroup, categoryLabel);
    }

    Object source(
            String groupLabel,
            String categoryLabel) {
        Map<String, SourceValue> categories =
                groups.get(groupLabel);
        SourceValue value = categories == null
                ? null : categories.get(categoryLabel);
        if (value == null || value.raw == null) {
            return categoryLabel;
        }
        return value.raw;
    }

    private static Map<String, Map<String, SourceValue>>
            immutableGroups(
                    Map<String, Map<String, SourceValue>> source) {
        Map<String, Map<String, SourceValue>> copy =
                new LinkedHashMap<
                        String, Map<String, SourceValue>>();
        for (Map.Entry<String, Map<String, SourceValue>> entry
                : source.entrySet()) {
            copy.put(
                    entry.getKey(),
                    Collections.unmodifiableMap(
                            new LinkedHashMap<String, SourceValue>(
                                    entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void ensureUniqueLabel(
            Map<String, TypedKey> labels,
            String label,
            TypedKey key,
            String kind) {
        TypedKey previous = labels.get(label);
        if (previous == null) {
            labels.put(label, key);
        } else if (!previous.equals(key)) {
            throw new ChartBuildException(
                    "Chart " + kind
                            + " display label collision: " + label);
        }
    }

    private static final class SourceValue {
        private final TypedKey key;
        private final Object raw;

        private SourceValue(TypedKey key, Object raw) {
            this.key = key;
            this.raw = raw;
        }
    }

    private static final class TypedKey {
        private final Object value;
        private final String typeName;

        private TypedKey(Object value) {
            this.value = value;
            this.typeName = value == null
                    ? "<null>" : value.getClass().getName();
        }

        private static TypedKey of(Object value) {
            return new TypedKey(value);
        }

        private String label() {
            return value == null ? "<null>" : String.valueOf(value);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TypedKey)) {
                return false;
            }
            TypedKey that = (TypedKey) other;
            return typeName.equals(that.typeName)
                    && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeName, value);
        }
    }
}
