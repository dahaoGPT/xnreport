package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import com.xn.report.text.DistributionResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ChartModelBuilder {

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
        validateRuntimeFields(definition, dataset);
        if (dataset.list().isEmpty()) {
            if (definition.getEmptyDataPolicy() == ChartEmptyDataPolicy.FAIL) {
                throw new ChartBuildException(
                        "Chart dataset is empty: " + dataset.id());
            }
            if (definition.getEmptyDataPolicy() == ChartEmptyDataPolicy.SKIP) {
                return Collections.emptyList();
            }
        }

        Map<String, List<DatasetRow>> groups = groups(definition, dataset.list());
        List<ChartModel> models = new ArrayList<ChartModel>();
        for (Map.Entry<String, List<DatasetRow>> group : groups.entrySet()) {
            models.add(buildGroup(definition, dataset.id(),
                    group.getKey(), group.getValue()));
        }
        return Collections.unmodifiableList(models);
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
        List<String> categories = new ArrayList<String>();
        List<String> labels = new ArrayList<String>();
        List<BigDecimal> values = new ArrayList<BigDecimal>();
        for (DistributionResult.BinResult bin : distribution.bins()) {
            categories.add(bin.label());
            values.add(BigDecimal.valueOf(bin.count()));
            labels.add(distributionLabel(
                    definition.getDataLabelMode(), bin, distribution.total()));
        }
        ChartSeriesDefinition source = definition.getSeries().get(0);
        ChartSeriesModel series = seriesModel(
                source, 0, values, Collections.<BigDecimal>emptyList());
        return model(definition, null, categories,
                Collections.singletonList(series), labels);
    }

    private ChartModel buildGroup(
            ChartDefinition definition,
            String datasetId,
            String groupKey,
            List<DatasetRow> rows) {
        List<String> categories = categories(definition, rows);
        Map<String, DatasetRow> categoryRows =
                indexRows(definition.getCategoryField(), rows);
        Set<String> skipped = skippedCategories(
                definition, categories, categoryRows);
        if (!skipped.isEmpty()) {
            categories.removeAll(skipped);
        }
        List<IndexedSeries> built = new ArrayList<IndexedSeries>();
        for (int index = 0; index < definition.getSeries().size(); index++) {
            ChartSeriesDefinition source = definition.getSeries().get(index);
            List<BigDecimal> values = new ArrayList<BigDecimal>();
            List<BigDecimal> sizes = new ArrayList<BigDecimal>();
            for (String category : categories) {
                DatasetRow row = categoryRows.get(category);
                values.add(value(source.getField(), source.getNullHandling(), row));
                if (source.getType() == ChartType.BUBBLE) {
                    sizes.add(value(source.getSizeField(),
                            source.getNullHandling(), row));
                }
            }
            built.add(new IndexedSeries(
                    source.getLegendOrder() == null
                            ? index : source.getLegendOrder().intValue(),
                    index,
                    seriesModel(source, index, values, sizes)));
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
        return model(definition, groupKey, categories, series,
                Collections.<String>emptyList(), datasetId);
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
                definition.getDataLabelMode(),
                labels,
                intValue(definition.getWidthPixels(), 1600),
                intValue(definition.getHeightPixels(), 850),
                definition.getEmptyDataPolicy(),
                definition.getEmptyMessage());
    }

    private static ChartSeriesModel seriesModel(
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
                source.isMarker(),
                source.getDataLabels() == null
                        ? ChartDataLabelMode.NONE : source.getDataLabels(),
                source.getFormat(),
                source.getNullHandling() == null
                        ? ChartNullHandling.GAP : source.getNullHandling(),
                source.getLegendOrder() == null
                        ? defaultOrder : source.getLegendOrder().intValue(),
                values,
                sizes);
    }

    private static Map<String, List<DatasetRow>> groups(
            ChartDefinition definition, List<DatasetRow> rows) {
        if (!hasText(definition.getGroupByField())) {
            return Collections.singletonMap((String) null, rows);
        }
        Map<String, List<DatasetRow>> result =
                new TreeMap<String, List<DatasetRow>>();
        for (DatasetRow row : rows) {
            Object raw = row.getOrNull(definition.getGroupByField());
            String key = raw == null ? "" : String.valueOf(raw);
            List<DatasetRow> group = result.get(key);
            if (group == null) {
                group = new ArrayList<DatasetRow>();
                result.put(key, group);
            }
            group.add(row);
        }
        if (result.isEmpty()) {
            result.put("", Collections.<DatasetRow>emptyList());
        }
        return result;
    }

    private static List<String> categories(
            ChartDefinition definition, List<DatasetRow> rows) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (definition.getCategories() != null) {
            for (Object category : definition.getCategories()) {
                if (category == null) {
                    throw new ChartBuildException(
                            "Configured chart categories must not contain null");
                }
                values.add(String.valueOf(category));
            }
        }
        for (DatasetRow row : rows) {
            Object raw = row.getOrNull(definition.getCategoryField());
            if (raw == null) {
                throw new ChartBuildException(
                        "Chart category field contains null: "
                                + definition.getCategoryField());
            }
            String category = String.valueOf(raw);
            if (definition.getCategorySort() == ChartCategorySort.EXPLICIT
                    && !values.contains(category)) {
                throw new ChartBuildException(
                        "Category is not declared in explicit category order: "
                                + category);
            }
            values.add(category);
        }
        List<String> categories = new ArrayList<String>(values);
        ChartCategorySort sort = definition.getCategorySort() == null
                ? ChartCategorySort.ASC : definition.getCategorySort();
        if (sort == ChartCategorySort.ASC) {
            Collections.sort(categories);
        } else if (sort == ChartCategorySort.DESC) {
            Collections.sort(categories, Collections.reverseOrder());
        }
        return categories;
    }

    private static Map<String, DatasetRow> indexRows(
            String categoryField, List<DatasetRow> rows) {
        Map<String, DatasetRow> indexed =
                new LinkedHashMap<String, DatasetRow>();
        for (DatasetRow row : rows) {
            String category = String.valueOf(row.get(categoryField));
            if (indexed.put(category, row) != null) {
                throw new ChartBuildException(
                        "Duplicate chart category in one group: " + category);
            }
        }
        return indexed;
    }

    private static Set<String> skippedCategories(
            ChartDefinition definition,
            List<String> categories,
            Map<String, DatasetRow> rows) {
        Set<String> skipped = new LinkedHashSet<String>();
        for (String category : categories) {
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
            return raw instanceof BigDecimal
                    ? (BigDecimal) raw
                    : new BigDecimal(String.valueOf(raw));
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
        for (String property : definition.getPresentProperties()) {
            if (chartProperty(definition, property) == null) {
                throw new ChartBuildException(
                        "Chart property must not be explicitly null: " + property);
            }
        }
        if (definition.getCategorySort() == ChartCategorySort.EXPLICIT
                && (definition.getCategories() == null
                || definition.getCategories().isEmpty())) {
            throw new ChartBuildException(
                    "EXPLICIT category sort requires categories");
        }
        int pieSeries = 0;
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
        }
        if (pieSeries > 0 && definition.getSeries().size() != 1) {
            throw new ChartBuildException(
                    "PIE and DOUGHNUT charts require exactly one series");
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

    private static String distributionLabel(
            ChartDataLabelMode mode,
            DistributionResult.BinResult bin,
            int total) {
        ChartDataLabelMode actual = mode == null
                ? ChartDataLabelMode.NONE : mode;
        BigDecimal percent = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(bin.count())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat(
                "0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        if (actual == ChartDataLabelMode.COUNT) {
            return bin.label() + " " + bin.count();
        }
        if (actual == ChartDataLabelMode.PERCENT) {
            return bin.label() + " " + format.format(percent) + "%";
        }
        if (actual == ChartDataLabelMode.COUNT_AND_PERCENT) {
            return bin.label() + " " + bin.count()
                    + " (" + format.format(percent) + "%)";
        }
        return bin.label();
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
}
