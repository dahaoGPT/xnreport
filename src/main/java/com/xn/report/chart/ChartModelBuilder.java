package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import com.xn.report.text.DistributionResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ChartModelBuilder {

    public static final int MAX_CATEGORIES = 5000;
    public static final int MAX_SERIES = 100;
    public static final int MAX_POINTS = 200000;

    public ChartModel build(
            ChartDefinition definition, DatasetResult dataset) {
        List<ChartModel> models = buildAll(definition, dataset);
        if (models.size() != 1) {
            throw new ChartBuildException(
                    "Chart " + safeId(definition)
                            + " produced " + models.size()
                            + " groups; call buildAll");
        }
        return models.get(0);
    }

    public List<ChartModel> buildAll(
            ChartDefinition definition, DatasetResult dataset) {
        validateDefinition(definition);
        if (dataset == null) {
            throw new ChartBuildException("Chart dataset must not be null");
        }
        if (dataset.type() != DatasetType.LIST) {
            throw new ChartBuildException(
                    "Chart dataset must be LIST: " + dataset.id());
        }
        if (!definition.getDataset().equals(dataset.id())) {
            throw new ChartBuildException(
                    "Chart " + definition.getId() + " expects dataset "
                            + definition.getDataset() + ", not " + dataset.id());
        }
        if (dataset.list().isEmpty() && !dataset.hasExplicitSchema()) {
            return emptyModels(definition, dataset.id());
        }
        validateRuntimeFields(definition, dataset);
        if (dataset.list().isEmpty()) {
            return emptyModels(definition, dataset.id());
        }

        Map<TypedKey, List<DatasetRow>> groups =
                groups(definition, dataset.list());
        ensureUniqueDisplayLabels(groups.keySet(), "group");
        Map<TypedKey, GroupPlan> plans =
                new LinkedHashMap<TypedKey, GroupPlan>();
        long totalPoints = 0;
        for (Map.Entry<TypedKey, List<DatasetRow>> group : groups.entrySet()) {
            GroupPlan plan = prepareGroup(definition, group.getValue());
            totalPoints += (long) plan.categoryKeys.size()
                    * definition.getSeries().size();
            if (totalPoints > MAX_POINTS) {
                throw new ChartBuildException(
                        "Chart exceeds cumulative MAX_POINTS="
                                + MAX_POINTS + " across output groups");
            }
            plans.put(group.getKey(), plan);
        }
        List<ChartModel> models = new ArrayList<ChartModel>();
        for (Map.Entry<TypedKey, GroupPlan> group : plans.entrySet()) {
            models.add(buildGroup(definition, dataset.id(),
                    group.getKey() == null ? null : group.getKey().label(),
                    group.getValue()));
        }
        return Collections.unmodifiableList(models);
    }

    private List<ChartModel> emptyModels(
            ChartDefinition definition, String datasetId) {
            if (definition.getEmptyDataPolicy() == ChartEmptyDataPolicy.FAIL) {
                throw new ChartBuildException(
                        "Chart dataset is empty: " + datasetId);
            }
            if (definition.getEmptyDataPolicy() == ChartEmptyDataPolicy.SKIP) {
                return Collections.emptyList();
            }
        return Collections.singletonList(
                buildEmptyModel(definition, datasetId));
    }

    private ChartModel buildEmptyModel(
            ChartDefinition definition, String datasetId) {
        List<IndexedSeries> built = new ArrayList<IndexedSeries>();
        for (int index = 0; index < definition.getSeries().size(); index++) {
            ChartSeriesDefinition source = definition.getSeries().get(index);
            built.add(new IndexedSeries(
                    source.getLegendOrder() == null
                            ? index : source.getLegendOrder().intValue(),
                    index,
                    seriesModel(
                            definition, source, index,
                            Collections.<BigDecimal>emptyList(),
                            Collections.<BigDecimal>emptyList())));
        }
        Collections.sort(built, new Comparator<IndexedSeries>() {
            @Override
            public int compare(IndexedSeries left, IndexedSeries right) {
                int order = Integer.compare(left.legendOrder, right.legendOrder);
                return order != 0 ? order : Integer.compare(left.index, right.index);
            }
        });
        List<ChartSeriesModel> series = new ArrayList<ChartSeriesModel>();
        for (IndexedSeries item : built) {
            series.add(item.series);
        }
        return model(
                definition, null,
                Collections.<String>emptyList(),
                series,
                Collections.<String>emptyList(),
                datasetId);
    }

    public ChartModel buildDistribution(
            ChartDefinition definition, DistributionResult distribution) {
        validateDefinition(definition);
        if (distribution == null) {
            throw new ChartBuildException("Distribution result must not be null");
        }
        if (definition.getSeries().size() != 1
                || !definition.getSeries().get(0).getType().isPieLike()) {
            throw new ChartBuildException(
                    "Distribution chart requires exactly one PIE or DOUGHNUT series");
        }
        int categoryCount = distribution.bins().size();
        if (categoryCount > MAX_CATEGORIES) {
            throw new ChartBuildException(
                    "Chart exceeds MAX_CATEGORIES=" + MAX_CATEGORIES);
        }
        if ((long) categoryCount * definition.getSeries().size()
                > MAX_POINTS) {
            throw new ChartBuildException(
                    "Chart exceeds MAX_POINTS=" + MAX_POINTS);
        }
        List<String> categories = new ArrayList<String>(categoryCount);
        List<String> labels = new ArrayList<String>(categoryCount);
        List<BigDecimal> values = new ArrayList<BigDecimal>(categoryCount);
        for (DistributionResult.BinResult bin : distribution.bins()) {
            categories.add(bin.label());
            values.add(BigDecimal.valueOf(bin.count()));
            labels.add(bin.displayLabel());
        }
        ChartSeriesDefinition source = definition.getSeries().get(0);
        validateSeriesValues(source, values, Collections.<BigDecimal>emptyList());
        ChartSeriesModel series = seriesModel(
                definition, source, 0, values,
                Collections.<BigDecimal>emptyList());
        return model(definition, null, categories,
                Collections.singletonList(series), labels);
    }

    private ChartModel buildGroup(
            ChartDefinition definition,
            String datasetId,
            String groupKey,
            GroupPlan plan) {
        List<TypedKey> categoryKeys = plan.categoryKeys;
        Map<TypedKey, DatasetRow> categoryRows = plan.categoryRows;
        List<String> categoryLabels = labels(categoryKeys);
        List<IndexedSeries> built = new ArrayList<IndexedSeries>();
        for (int index = 0; index < definition.getSeries().size(); index++) {
            ChartSeriesDefinition source = definition.getSeries().get(index);
            List<BigDecimal> values =
                    new ArrayList<BigDecimal>(categoryKeys.size());
            List<BigDecimal> sizes = source.getType() == ChartType.BUBBLE
                    ? new ArrayList<BigDecimal>(categoryKeys.size())
                    : Collections.<BigDecimal>emptyList();
            for (TypedKey category : categoryKeys) {
                DatasetRow row = categoryRows.get(category);
                values.add(value(source.getField(), source.getNullHandling(), row));
                if (source.getType() == ChartType.BUBBLE) {
                    sizes.add(value(source.getSizeField(),
                            source.getNullHandling(), row));
                }
            }
            validateSeriesValues(source, values, sizes);
            built.add(new IndexedSeries(
                    source.getLegendOrder() == null
                            ? index : source.getLegendOrder().intValue(),
                    index,
                    seriesModel(definition, source, index, values, sizes)));
        }
        Collections.sort(built, new Comparator<IndexedSeries>() {
            @Override
            public int compare(IndexedSeries left, IndexedSeries right) {
                int order = Integer.compare(left.legendOrder, right.legendOrder);
                return order != 0 ? order : Integer.compare(left.index, right.index);
            }
        });
        List<ChartSeriesModel> series = new ArrayList<ChartSeriesModel>();
        for (IndexedSeries item : built) {
            series.add(item.series);
        }
        List<String> labels = series.size() == 1
                && series.get(0).getType().isPieLike()
                ? pieLabels(
                        categoryLabels,
                        series.get(0).getValues(),
                        effectivePieLabelMode(definition, series.get(0)))
                : Collections.<String>emptyList();
        return model(definition, groupKey, categoryLabels, series,
                labels, datasetId);
    }

    private static GroupPlan prepareGroup(
            ChartDefinition definition, List<DatasetRow> rows) {
        List<TypedKey> categoryKeys = categories(definition, rows);
        if (categoryKeys.size() > MAX_CATEGORIES) {
            throw new ChartBuildException(
                    "Chart exceeds MAX_CATEGORIES=" + MAX_CATEGORIES);
        }
        ensureUniqueDisplayLabels(categoryKeys, "category");
        Map<TypedKey, DatasetRow> categoryRows =
                indexRows(definition.getCategoryField(), rows);
        Set<TypedKey> skipped = skippedCategories(
                definition, categoryKeys, categoryRows);
        if (!skipped.isEmpty()) {
            categoryKeys.removeAll(skipped);
        }
        return new GroupPlan(categoryKeys, categoryRows);
    }

    private ChartModel model(
            ChartDefinition definition,
            String groupKey,
            List<String> categories,
            List<ChartSeriesModel> series,
            List<String> labels) {
        return model(definition, groupKey, categories, series, labels,
                definition.getDataset());
    }

    private ChartModel model(
            ChartDefinition definition,
            String groupKey,
            List<String> categories,
            List<ChartSeriesModel> series,
            List<String> labels,
            String datasetId) {
        return new ChartModel(
                definition.getId(),
                definition.getTitle(),
                datasetId,
                groupKey,
                categories,
                series,
                definition.getLegendPosition(),
                definition.getPrimaryAxisMin(),
                definition.getPrimaryAxisMax(),
                definition.getSecondaryAxisMin(),
                definition.getSecondaryAxisMax(),
                effectiveModelLabelMode(definition, labels, series),
                labels,
                intValue(definition.getWidthPixels(), 1600),
                intValue(definition.getHeightPixels(), 850),
                definition.getEmptyDataPolicy(),
                definition.getEmptyMessage());
    }

    private static ChartSeriesModel seriesModel(
            ChartDefinition definition,
            ChartSeriesDefinition source,
            int defaultOrder,
            List<BigDecimal> values,
            List<BigDecimal> sizes) {
        return new ChartSeriesModel(
                source.getField(),
                source.getName(),
                source.getType(),
                source.getAxis() == null ? ChartAxis.PRIMARY : source.getAxis(),
                source.getStackGroup(),
                source.getColor(),
                source.getLineStyle() == null
                        ? ChartLineStyle.SOLID : source.getLineStyle(),
                source.getLineWidth() == null
                        ? BigDecimal.valueOf(2) : source.getLineWidth(),
                (source.getType() == ChartType.SCATTER
                        && !source.hasProperty("marker"))
                        || source.isMarker(),
                source.hasProperty("dataLabels")
                        ? source.getDataLabels()
                        : definition.getDataLabelMode(),
                source.getFormat(),
                source.getNullHandling() == null
                        ? ChartNullHandling.GAP : source.getNullHandling(),
                source.getLegendOrder() == null
                        ? defaultOrder : source.getLegendOrder().intValue(),
                defaultOrder,
                values,
                sizes);
    }

    private static Map<TypedKey, List<DatasetRow>> groups(
            ChartDefinition definition, List<DatasetRow> rows) {
        if (!hasText(definition.getGroupByField())) {
            return Collections.singletonMap((TypedKey) null, rows);
        }
        Map<TypedKey, List<DatasetRow>> collected =
                new LinkedHashMap<TypedKey, List<DatasetRow>>();
        for (DatasetRow row : rows) {
            Object raw = row.getOrNull(definition.getGroupByField());
            TypedKey key = TypedKey.of(raw);
            List<DatasetRow> group = collected.get(key);
            if (group == null) {
                group = new ArrayList<DatasetRow>();
                collected.put(key, group);
            }
            group.add(row);
        }
        if (collected.isEmpty()) {
            collected.put(TypedKey.of(null),
                    Collections.<DatasetRow>emptyList());
        }
        List<TypedKey> keys =
                new ArrayList<TypedKey>(collected.keySet());
        Collections.sort(keys, TypedKey.DISPLAY_ORDER);
        Map<TypedKey, List<DatasetRow>> result =
                new LinkedHashMap<TypedKey, List<DatasetRow>>();
        for (TypedKey key : keys) {
            result.put(key, collected.get(key));
        }
        return result;
    }

    private static List<TypedKey> categories(
            ChartDefinition definition, List<DatasetRow> rows) {
        LinkedHashSet<TypedKey> values = new LinkedHashSet<TypedKey>();
        if (definition.getCategories() != null) {
            for (Object category : definition.getCategories()) {
                values.add(TypedKey.of(category));
                requireCategoryLimit(values.size());
            }
        }
        for (DatasetRow row : rows) {
            Object raw = row.getOrNull(definition.getCategoryField());
            TypedKey category = TypedKey.of(raw);
            if (definition.getCategorySort() == ChartCategorySort.EXPLICIT
                    && !values.contains(category)) {
                throw new ChartBuildException(
                        "Category is not declared in explicit category order: "
                                + category.label());
            }
            values.add(category);
            requireCategoryLimit(values.size());
        }
        List<TypedKey> categories = new ArrayList<TypedKey>(values);
        ChartCategorySort sort = definition.getCategorySort() == null
                ? ChartCategorySort.ASC : definition.getCategorySort();
        if (sort == ChartCategorySort.ASC) {
            Collections.sort(categories, TypedKey.DISPLAY_ORDER);
        } else if (sort == ChartCategorySort.DESC) {
            Collections.sort(categories,
                    Collections.reverseOrder(TypedKey.DISPLAY_ORDER));
        }
        return categories;
    }

    private static void requireCategoryLimit(int size) {
        if (size > MAX_CATEGORIES) {
            throw new ChartBuildException(
                    "Chart exceeds MAX_CATEGORIES=" + MAX_CATEGORIES);
        }
    }

    private static Map<TypedKey, DatasetRow> indexRows(
            String categoryField, List<DatasetRow> rows) {
        Map<TypedKey, DatasetRow> indexed =
                new LinkedHashMap<TypedKey, DatasetRow>();
        for (DatasetRow row : rows) {
            TypedKey category = TypedKey.of(row.getOrNull(categoryField));
            if (indexed.put(category, row) != null) {
                throw new ChartBuildException(
                        "Duplicate chart category in one group: "
                                + category.label());
            }
        }
        return indexed;
    }

    private static Set<TypedKey> skippedCategories(
            ChartDefinition definition,
            List<TypedKey> categories,
            Map<TypedKey, DatasetRow> rows) {
        Set<TypedKey> skipped = new LinkedHashSet<TypedKey>();
        for (TypedKey category : categories) {
            DatasetRow row = rows.get(category);
            for (ChartSeriesDefinition series : definition.getSeries()) {
                if (series.getNullHandling() == ChartNullHandling.SKIP_CATEGORY
                        && (row == null || row.getOrNull(series.getField()) == null
                        || series.getType() == ChartType.BUBBLE
                        && row.getOrNull(series.getSizeField()) == null)) {
                    skipped.add(category);
                    break;
                }
            }
        }
        return skipped;
    }

    private static BigDecimal value(
            String field, ChartNullHandling handling, DatasetRow row) {
        Object raw = row == null ? null : row.getOrNull(field);
        if (raw == null) {
            return handling == ChartNullHandling.ZERO ? BigDecimal.ZERO : null;
        }
        if (!(raw instanceof Number)) {
            throw new ChartBuildException(
                    "Chart series field must be numeric: " + field);
        }
        try {
            BigDecimal value = raw instanceof BigDecimal
                    ? (BigDecimal) raw
                    : new BigDecimal(String.valueOf(raw));
            double converted = value.doubleValue();
            if (Double.isNaN(converted) || Double.isInfinite(converted)) {
                throw new ChartBuildException(
                        "Chart series field must fit a finite double: " + field);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new ChartBuildException(
                    "Chart series field must be numeric: " + field, exception);
        }
    }

    private static void validateRuntimeFields(
            ChartDefinition definition, DatasetResult dataset) {
        requireRuntimeField(dataset, definition.getCategoryField());
        if (hasText(definition.getGroupByField())) {
            requireRuntimeField(dataset, definition.getGroupByField());
        }
        for (ChartSeriesDefinition series : definition.getSeries()) {
            requireRuntimeField(dataset, series.getField());
            if (series.getType() == ChartType.BUBBLE) {
                requireRuntimeField(dataset, series.getSizeField());
            }
        }
    }

    private static void requireRuntimeField(
            DatasetResult dataset, String field) {
        if (!dataset.schema().containsField(field)) {
            throw new ChartBuildException(
                    "Chart field is missing from runtime schema: " + field);
        }
    }

    private static void validateDefinition(ChartDefinition definition) {
        if (definition == null) {
            throw new ChartBuildException("Chart definition must not be null");
        }
        if (!hasText(definition.getId())
                || !hasText(definition.getDataset())
                || !hasText(definition.getCategoryField())) {
            throw new ChartBuildException(
                    "Chart id, dataset, and categoryField are required");
        }
        if (definition.getSeries() == null || definition.getSeries().isEmpty()) {
            throw new ChartBuildException(
                    "Chart must contain at least one series: " + definition.getId());
        }
        if (definition.getSeries().size() > MAX_SERIES) {
            throw new ChartBuildException(
                    "Chart exceeds MAX_SERIES=" + MAX_SERIES);
        }
        if (definition.getCategories() != null
                && definition.getCategories().size() > MAX_CATEGORIES) {
            throw new ChartBuildException(
                    "Chart exceeds MAX_CATEGORIES=" + MAX_CATEGORIES);
        }
        if (definition.getCategories() != null
                && (long) definition.getCategories().size()
                * definition.getSeries().size() > MAX_POINTS) {
            throw new ChartBuildException(
                    "Configured categories and series exceed MAX_POINTS="
                            + MAX_POINTS);
        }
        for (String property : definition.getPresentProperties()) {
            if (chartProperty(definition, property) == null) {
                throw new ChartBuildException(
                        "Chart property must not be explicitly null: " + property);
            }
        }
        if (containsType(definition, ChartType.STOCK)
                && definition.getMode() != ChartDefinition.Mode.TEMPLATE_NATIVE) {
            throw new ChartBuildException(
                    "STOCK chart requires TEMPLATE_NATIVE mode");
        }
        if (definition.getCategorySort() == ChartCategorySort.EXPLICIT
                && (definition.getCategories() == null
                || definition.getCategories().isEmpty())) {
            throw new ChartBuildException(
                    "EXPLICIT category sort requires categories");
        }
        int pieSeries = 0;
        Map<String, StackContract> stackContracts =
                new LinkedHashMap<String, StackContract>();
        Map<String, String> stackSlots =
                new LinkedHashMap<String, String>();
        for (ChartSeriesDefinition series : definition.getSeries()) {
            if (series == null || !hasText(series.getField())
                    || !hasText(series.getName()) || series.getType() == null) {
                throw new ChartBuildException(
                        "Chart series field, name, and type are required");
            }
            if (series.getType().isStacked()
                    && !hasText(series.getStackGroup())) {
                throw new ChartBuildException(
                        "Stacked series requires stackGroup: " + series.getName());
            }
            if (!series.getType().isStacked()
                    && hasText(series.getStackGroup())) {
                throw new ChartBuildException(
                        "stackGroup is only valid for stacked series: "
                                + series.getName());
            }
            if (series.getType() == ChartType.BUBBLE
                    && !hasText(series.getSizeField())) {
                throw new ChartBuildException(
                        "Bubble series requires sizeField: " + series.getName());
            }
            if (series.getType() != ChartType.BUBBLE
                    && hasText(series.getSizeField())) {
                throw new ChartBuildException(
                        "sizeField is only valid for BUBBLE series: "
                                + series.getName());
            }
            for (String property : series.getPresentProperties()) {
                if (seriesProperty(series, property) == null) {
                    throw new ChartBuildException(
                            "Chart series property must not be explicitly null: "
                                    + property);
                }
            }
            if (series.getType().isPieLike()) {
                pieSeries++;
            }
            validateSeriesPropertyMatrix(definition, series);
            String renderSlot = renderSlot(series.getType());
            if (renderSlot != null) {
                ChartAxis axis = series.getAxis() == null
                        ? ChartAxis.PRIMARY : series.getAxis();
                String slot = renderSlot + "|" + axis.name();
                String token = hasText(series.getStackGroup())
                        ? series.getStackGroup()
                        : series.getType().name();
                String occupyingGroup = stackSlots.get(slot);
                if (occupyingGroup != null
                        && !occupyingGroup.equals(token)) {
                    throw new ChartBuildException(
                            "Chart stack slot " + renderSlot + " on "
                                    + axis + " axis has conflicting series groups "
                                    + "or multiple stackGroup values");
                }
                stackSlots.put(slot, token);
            }
            if (hasText(series.getStackGroup())) {
                StackContract contract = stackContracts.get(
                        series.getStackGroup());
                if (contract == null) {
                    stackContracts.put(series.getStackGroup(),
                            new StackContract(
                                    series.getType(), series.getAxis()));
                } else {
                    if (contract.type != series.getType()) {
                        throw new ChartBuildException(
                                "All series in stackGroup "
                                        + series.getStackGroup()
                                        + " must use the same type");
                    }
                    if (contract.axis != series.getAxis()) {
                        throw new ChartBuildException(
                                "All series in stackGroup "
                                        + series.getStackGroup()
                                        + " must use the same axis");
                    }
                }
            }
        }
        if (pieSeries > 0 && definition.getSeries().size() != 1) {
            throw new ChartBuildException(
                    "PIE and DOUGHNUT charts require exactly one series");
        }
        if (pieSeries == 0
                && definition.getDataLabelMode() != ChartDataLabelMode.NONE
                && definition.getDataLabelMode() != ChartDataLabelMode.VALUE) {
            throw new ChartBuildException(
                    "Non-pie charts support only VALUE dataLabelMode");
        }
        validatePercentAxisSharing(definition);
        validateAxisRanges(definition);
    }

    private static void validatePercentAxisSharing(
            ChartDefinition definition) {
        for (ChartAxis axis : ChartAxis.values()) {
            boolean percent = false;
            boolean ordinary = false;
            for (ChartSeriesDefinition series : definition.getSeries()) {
                ChartAxis seriesAxis = series.getAxis() == null
                        ? ChartAxis.PRIMARY : series.getAxis();
                if (seriesAxis != axis || series.getType().isPieLike()
                        || series.getType() == ChartType.RADAR) {
                    continue;
                }
                if (series.getType()
                        == ChartType.PERCENT_STACKED_COLUMN) {
                    percent = true;
                } else {
                    ordinary = true;
                }
            }
            if (percent && ordinary) {
                throw new ChartBuildException(
                        "A percent axis cannot share " + axis
                                + " with ordinary numeric series");
            }
        }
    }

    private static String renderSlot(ChartType type) {
        if (type == ChartType.COLUMN
                || type == ChartType.STACKED_COLUMN
                || type == ChartType.PERCENT_STACKED_COLUMN) {
            return "VERTICAL_COLUMN";
        }
        if (type == ChartType.BAR || type == ChartType.STACKED_BAR) {
            return "HORIZONTAL_BAR";
        }
        if (type == ChartType.AREA || type == ChartType.STACKED_AREA) {
            return "VERTICAL_AREA";
        }
        return null;
    }

    private static void validateAxisRanges(ChartDefinition definition) {
        boolean primaryPercent = false;
        boolean secondaryPercent = false;
        for (ChartSeriesDefinition series : definition.getSeries()) {
            if (series.getType() == ChartType.PERCENT_STACKED_COLUMN) {
                if (series.getAxis() == ChartAxis.SECONDARY) {
                    secondaryPercent = true;
                } else {
                    primaryPercent = true;
                }
            }
        }
        validateAxisRange("primary",
                definition.getPrimaryAxisMin(),
                definition.getPrimaryAxisMax(), primaryPercent);
        validateAxisRange("secondary",
                definition.getSecondaryAxisMin(),
                definition.getSecondaryAxisMax(), secondaryPercent);
    }

    private static void validateAxisRange(
            String name, BigDecimal minimum, BigDecimal maximum,
            boolean percent) {
        requireFinite(name + "AxisMin", minimum);
        requireFinite(name + "AxisMax", maximum);
        if (minimum != null && maximum != null
                && minimum.compareTo(maximum) >= 0) {
            throw new ChartBuildException(
                    name + " axis minimum must be less than maximum");
        }
        if (percent
                && (minimum != null
                && (minimum.compareTo(BigDecimal.ZERO) < 0
                || minimum.compareTo(BigDecimal.ONE) > 0)
                || maximum != null
                && (maximum.compareTo(BigDecimal.ZERO) < 0
                || maximum.compareTo(BigDecimal.ONE) > 0))) {
            throw new ChartBuildException(
                    name + " percent axis bounds use ratio units from 0 to 1");
        }
    }

    private static void requireFinite(String property, BigDecimal value) {
        if (value == null) {
            return;
        }
        double converted = value.doubleValue();
        if (Double.isInfinite(converted) || Double.isNaN(converted)) {
            throw new ChartBuildException(
                    property + " must fit a finite double");
        }
    }

    private static void validateSeriesPropertyMatrix(
            ChartDefinition definition, ChartSeriesDefinition series) {
        ChartType type = series.getType();
        if ((type == ChartType.SCATTER || type == ChartType.BUBBLE)
                && (series.hasProperty("lineStyle")
                || series.hasProperty("lineWidth"))) {
            throw new ChartBuildException(
                    type + " does not support lineStyle or lineWidth");
        }
        if ((series.hasProperty("lineStyle")
                || series.hasProperty("lineWidth"))
                && type != ChartType.LINE
                && type != ChartType.AREA
                && type != ChartType.STACKED_AREA
                && type != ChartType.RADAR
                && type != ChartType.STOCK) {
            throw new ChartBuildException(
                    type + " does not support lineStyle or lineWidth");
        }
        if (type == ChartType.BUBBLE && series.hasProperty("marker")) {
            throw new ChartBuildException(
                    "BUBBLE does not support marker");
        }
        if (series.hasProperty("marker")
                && type != ChartType.LINE
                && type != ChartType.SCATTER
                && type != ChartType.STOCK) {
            throw new ChartBuildException(
                    type + " does not support marker");
        }
        if (type == ChartType.SCATTER
                && series.hasProperty("marker")
                && !series.isMarker()) {
            throw new ChartBuildException(
                    "SCATTER requires a visible marker");
        }
        if (type == ChartType.RADAR
                && (series.hasProperty("marker")
                || series.hasProperty("dataLabels")
                || series.hasProperty("format")
                || series.hasProperty("axis"))) {
            throw new ChartBuildException(
                    "RADAR does not support marker, dataLabels, format, or axis");
        }
        if (series.hasProperty("format")
                && (series.hasProperty("dataLabels")
                ? series.getDataLabels() == ChartDataLabelMode.NONE
                : definition.getDataLabelMode()
                == ChartDataLabelMode.NONE)) {
            throw new ChartBuildException(
                    "format requires visible dataLabels");
        }
        if (type.isPieLike() && series.hasProperty("format")) {
            throw new ChartBuildException(
                    type + " does not support series format");
        }
        if (!type.isPieLike()
                && series.hasProperty("dataLabels")
                && series.getDataLabels() != ChartDataLabelMode.NONE
                && series.getDataLabels() != ChartDataLabelMode.VALUE) {
            throw new ChartBuildException(
                    type + " supports only VALUE dataLabels");
        }
        if ((type.isPieLike() || type == ChartType.RADAR)
                && series.hasProperty("axis")) {
            throw new ChartBuildException(
                    type + " does not support an axis selection");
        }
    }

    private static boolean containsType(
            ChartDefinition definition, ChartType type) {
        for (ChartSeriesDefinition series : definition.getSeries()) {
            if (series != null && series.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static ChartDataLabelMode effectivePieLabelMode(
            ChartDefinition definition, ChartSeriesModel series) {
        return definition.getDataLabelMode() != ChartDataLabelMode.NONE
                ? definition.getDataLabelMode() : series.getDataLabelMode();
    }

    private static ChartDataLabelMode effectiveModelLabelMode(
            ChartDefinition definition,
            List<String> labels,
            List<ChartSeriesModel> series) {
        if (definition.getDataLabelMode() != null
                && definition.getDataLabelMode()
                != ChartDataLabelMode.NONE) {
            return definition.getDataLabelMode();
        }
        if (series.size() == 1
                && series.get(0).getType().isPieLike()
                && series.get(0).getDataLabelMode()
                != ChartDataLabelMode.NONE) {
            return series.get(0).getDataLabelMode();
        }
        return labels.isEmpty()
                ? ChartDataLabelMode.NONE
                : ChartDataLabelMode.COUNT_AND_PERCENT;
    }

    private static List<String> pieLabels(
            List<String> categories,
            List<BigDecimal> values,
            ChartDataLabelMode mode) {
        if (mode == null || mode == ChartDataLabelMode.NONE) {
            return Collections.emptyList();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                if (value.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ChartBuildException(
                            "PIE and DOUGHNUT values must not be negative");
                }
                total = total.add(value);
            }
        }
        List<String> labels = new ArrayList<String>();
        for (int index = 0; index < categories.size(); index++) {
            BigDecimal value = values.get(index) == null
                    ? BigDecimal.ZERO : values.get(index);
            BigDecimal percent = total.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : value.multiply(BigDecimal.valueOf(100))
                            .divide(total, 2, RoundingMode.HALF_UP);
            String count = value.stripTrailingZeros().toPlainString();
            String percentText = percent.setScale(2, RoundingMode.HALF_UP)
                    .toPlainString() + "%";
            if (mode == ChartDataLabelMode.COUNT
                    || mode == ChartDataLabelMode.VALUE) {
                labels.add(categories.get(index) + " " + count);
            } else if (mode == ChartDataLabelMode.PERCENT) {
                labels.add(categories.get(index) + " " + percentText);
            } else {
                labels.add(categories.get(index) + " " + count
                        + " (" + percentText + ")");
            }
        }
        return Collections.unmodifiableList(labels);
    }

    private static void validateSeriesValues(
            ChartSeriesDefinition series,
            List<BigDecimal> values,
            List<BigDecimal> sizes) {
        if (series.getType().isPieLike()) {
            for (BigDecimal value : values) {
                if (value != null
                        && value.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ChartBuildException(
                            "PIE and DOUGHNUT values must not be negative");
                }
            }
        }
        if (series.getType() == ChartType.BUBBLE) {
            for (BigDecimal size : sizes) {
                if (size != null
                        && size.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ChartBuildException(
                            "BUBBLE size values must be greater than zero");
                }
            }
        }
    }

    private static List<String> labels(List<TypedKey> keys) {
        List<String> labels = new ArrayList<String>(keys.size());
        for (TypedKey key : keys) {
            labels.add(key.label());
        }
        return labels;
    }

    private static void ensureUniqueDisplayLabels(
            Iterable<TypedKey> keys, String kind) {
        Map<String, TypedKey> seen =
                new LinkedHashMap<String, TypedKey>();
        for (TypedKey key : keys) {
            if (key == null) {
                continue;
            }
            TypedKey previous = seen.put(key.label(), key);
            if (previous != null && !previous.equals(key)) {
                throw new ChartBuildException(
                        "Chart " + kind + " display label collision: "
                                + key.label());
            }
        }
    }

    private static Object chartProperty(
            ChartDefinition chart, String property) {
        if ("id".equals(property)) return chart.getId();
        if ("title".equals(property)) return chart.getTitle();
        if ("mode".equals(property)) return chart.getMode();
        if ("dataset".equals(property)) return chart.getDataset();
        if ("excelSheet".equals(property)) return chart.getExcelSheet();
        if ("excelTable".equals(property)) return chart.getExcelTable();
        if ("templateChartMarker".equals(property)) return chart.getTemplateChartMarker();
        if ("templateChartIndex".equals(property)) return chart.getTemplateChartIndex();
        if ("templateChartLocators".equals(property)) return chart.getTemplateChartLocators();
        if ("anchorRow".equals(property)) return chart.getAnchorRow();
        if ("anchorColumn".equals(property)) return chart.getAnchorColumn();
        if ("anchorWidthColumns".equals(property)) return chart.getAnchorWidthColumns();
        if ("anchorHeightRows".equals(property)) return chart.getAnchorHeightRows();
        if ("categoryField".equals(property)) return chart.getCategoryField();
        if ("groupByField".equals(property)) return chart.getGroupByField();
        if ("categories".equals(property)) return chart.getCategories();
        if ("categorySort".equals(property)) return chart.getCategorySort();
        if ("series".equals(property)) return chart.getSeries();
        if ("legendPosition".equals(property)) return chart.getLegendPosition();
        if ("primaryAxisMin".equals(property)) return chart.getPrimaryAxisMin();
        if ("primaryAxisMax".equals(property)) return chart.getPrimaryAxisMax();
        if ("secondaryAxisMin".equals(property)) return chart.getSecondaryAxisMin();
        if ("secondaryAxisMax".equals(property)) return chart.getSecondaryAxisMax();
        if ("dataLabelMode".equals(property)) return chart.getDataLabelMode();
        if ("widthPixels".equals(property)) return chart.getWidthPixels();
        if ("heightPixels".equals(property)) return chart.getHeightPixels();
        if ("dpi".equals(property)) return chart.getDpi();
        if ("emptyDataPolicy".equals(property)) return chart.getEmptyDataPolicy();
        if ("emptyMessage".equals(property)) return chart.getEmptyMessage();
        return null;
    }

    private static Object seriesProperty(
            ChartSeriesDefinition series, String property) {
        if ("field".equals(property)) return series.getField();
        if ("name".equals(property)) return series.getName();
        if ("type".equals(property)) return series.getType();
        if ("axis".equals(property)) return series.getAxis();
        if ("stackGroup".equals(property)) return series.getStackGroup();
        if ("color".equals(property)) return series.getColor();
        if ("lineStyle".equals(property)) return series.getLineStyle();
        if ("lineWidth".equals(property)) return series.getLineWidth();
        if ("marker".equals(property)) return series.getMarker();
        if ("dataLabels".equals(property)) return series.getDataLabels();
        if ("format".equals(property)) return series.getFormat();
        if ("nullHandling".equals(property)) return series.getNullHandling();
        if ("legendOrder".equals(property)) return series.getLegendOrder();
        if ("sizeField".equals(property)) return series.getSizeField();
        return null;
    }

    private static int intValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value.intValue();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safeId(ChartDefinition definition) {
        return definition == null ? "<null>" : String.valueOf(definition.getId());
    }

    private static final class IndexedSeries {
        private final int legendOrder;
        private final int index;
        private final ChartSeriesModel series;

        private IndexedSeries(
                int legendOrder, int index, ChartSeriesModel series) {
            this.legendOrder = legendOrder;
            this.index = index;
            this.series = series;
        }
    }

    private static final class GroupPlan {
        private final List<TypedKey> categoryKeys;
        private final Map<TypedKey, DatasetRow> categoryRows;

        private GroupPlan(
                List<TypedKey> categoryKeys,
                Map<TypedKey, DatasetRow> categoryRows) {
            this.categoryKeys = categoryKeys;
            this.categoryRows = categoryRows;
        }
    }

    private static final class StackContract {
        private final ChartType type;
        private final ChartAxis axis;

        private StackContract(ChartType type, ChartAxis axis) {
            this.type = type;
            this.axis = axis == null ? ChartAxis.PRIMARY : axis;
        }
    }

    private static final class TypedKey {
        private static final Comparator<TypedKey> DISPLAY_ORDER =
                new Comparator<TypedKey>() {
                    @Override
                    public int compare(TypedKey left, TypedKey right) {
                        int label = left.label().compareTo(right.label());
                        if (label != 0) {
                            return label;
                        }
                        return left.typeName.compareTo(right.typeName);
                    }
                };

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
