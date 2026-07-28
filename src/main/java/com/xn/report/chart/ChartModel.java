package com.xn.report.chart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChartModel {

    private final String chartId;
    private final String title;
    private final String datasetId;
    private final String groupKey;
    private final List<String> categories;
    private final List<ChartSeriesModel> series;
    private final LegendPosition legendPosition;
    private final BigDecimal primaryAxisMin;
    private final BigDecimal primaryAxisMax;
    private final BigDecimal secondaryAxisMin;
    private final BigDecimal secondaryAxisMax;
    private final ChartDataLabelMode dataLabelMode;
    private final List<String> dataLabels;
    private final int widthPixels;
    private final int heightPixels;
    private final ChartEmptyDataPolicy emptyDataPolicy;
    private final String emptyMessage;

    public ChartModel(
            String chartId,
            String title,
            String datasetId,
            String groupKey,
            List<String> categories,
            List<ChartSeriesModel> series,
            LegendPosition legendPosition,
            BigDecimal primaryAxisMin,
            BigDecimal primaryAxisMax,
            BigDecimal secondaryAxisMin,
            BigDecimal secondaryAxisMax,
            ChartDataLabelMode dataLabelMode,
            List<String> dataLabels,
            int widthPixels,
            int heightPixels,
            ChartEmptyDataPolicy emptyDataPolicy,
            String emptyMessage) {
        this.chartId = requireText(chartId, "chartId");
        this.title = title == null ? "" : title;
        this.datasetId = requireText(datasetId, "datasetId");
        this.groupKey = groupKey;
        this.categories = immutableStrings(categories);
        this.series = immutableSeries(series);
        this.legendPosition = legendPosition == null
                ? LegendPosition.BOTTOM : legendPosition;
        this.primaryAxisMin = primaryAxisMin;
        this.primaryAxisMax = primaryAxisMax;
        this.secondaryAxisMin = secondaryAxisMin;
        this.secondaryAxisMax = secondaryAxisMax;
        this.dataLabelMode = dataLabelMode == null
                ? ChartDataLabelMode.NONE : dataLabelMode;
        this.dataLabels = immutableStrings(dataLabels);
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.emptyDataPolicy = emptyDataPolicy == null
                ? ChartEmptyDataPolicy.OUTPUT_MESSAGE : emptyDataPolicy;
        this.emptyMessage = emptyMessage == null ? "暂无图表数据" : emptyMessage;
        validate();
    }

    private void validate() {
        for (ChartSeriesModel item : series) {
            if (item == null) {
                throw new IllegalArgumentException("Chart series must not contain null");
            }
            if (item.getValues().size() != categories.size()) {
                throw new IllegalArgumentException(
                        "Chart series point count must equal category count: "
                                + item.getName());
            }
            if (item.getType() == ChartType.BUBBLE
                    && item.getSizes().size() != categories.size()) {
                throw new IllegalArgumentException(
                        "Bubble size count must equal category count: "
                                + item.getName());
            }
        }
        if (!dataLabels.isEmpty() && dataLabels.size() != categories.size()) {
            throw new IllegalArgumentException(
                    "Chart data label count must equal category count");
        }
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new IllegalArgumentException("Chart dimensions must be positive");
        }
    }

    private static List<String> immutableStrings(List<String> values) {
        return values == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static List<ChartSeriesModel> immutableSeries(
            List<ChartSeriesModel> values) {
        return values == null
                ? Collections.<ChartSeriesModel>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<ChartSeriesModel>(values));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String getChartId() {
        return chartId;
    }

    public String getTitle() {
        return title;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<ChartSeriesModel> getSeries() {
        return series;
    }

    public LegendPosition getLegendPosition() {
        return legendPosition;
    }

    public BigDecimal getPrimaryAxisMin() {
        return primaryAxisMin;
    }

    public BigDecimal getPrimaryAxisMax() {
        return primaryAxisMax;
    }

    public BigDecimal getSecondaryAxisMin() {
        return secondaryAxisMin;
    }

    public BigDecimal getSecondaryAxisMax() {
        return secondaryAxisMax;
    }

    public ChartDataLabelMode getDataLabelMode() {
        return dataLabelMode;
    }

    public List<String> getDataLabels() {
        return dataLabels;
    }

    public int getWidthPixels() {
        return widthPixels;
    }

    public int getHeightPixels() {
        return heightPixels;
    }

    public ChartEmptyDataPolicy getEmptyDataPolicy() {
        return emptyDataPolicy;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }
}
