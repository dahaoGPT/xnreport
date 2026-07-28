package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xn.report.chart.ChartCategorySort;
import com.xn.report.chart.ChartDataLabelMode;
import com.xn.report.chart.ChartEmptyDataPolicy;
import com.xn.report.chart.ChartEnumValue;
import com.xn.report.chart.LegendPosition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChartDefinition {

    public enum Mode {
        GENERATED_NATIVE,
        TEMPLATE_NATIVE,
        IMAGE;

        @com.fasterxml.jackson.annotation.JsonCreator
        public static Mode fromConfig(String value) {
            return ChartEnumValue.parse(Mode.class, value);
        }
    }

    private String id;
    private String title;
    private Mode mode = Mode.GENERATED_NATIVE;
    private String dataset;
    private String excelSheet;
    private String excelTable;
    private String categoryField;
    private String groupByField;
    private List<String> categories = new ArrayList<String>();
    private ChartCategorySort categorySort = ChartCategorySort.ASC;
    private List<ChartSeriesDefinition> series =
            new ArrayList<ChartSeriesDefinition>();
    private LegendPosition legendPosition = LegendPosition.BOTTOM;
    private BigDecimal primaryAxisMin;
    private BigDecimal primaryAxisMax;
    private BigDecimal secondaryAxisMin;
    private BigDecimal secondaryAxisMax;
    private ChartDataLabelMode dataLabelMode = ChartDataLabelMode.NONE;
    private Integer widthPixels = 1600;
    private Integer heightPixels = 850;
    private Integer dpi = 180;
    private ChartEmptyDataPolicy emptyDataPolicy =
            ChartEmptyDataPolicy.OUTPUT_MESSAGE;
    private String emptyMessage = "暂无图表数据";
    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        mark("id");
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        mark("title");
        this.title = title;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        mark("mode");
        this.mode = mode;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        mark("dataset");
        this.dataset = dataset;
    }

    public String getExcelSheet() {
        return excelSheet;
    }

    public void setExcelSheet(String excelSheet) {
        mark("excelSheet");
        this.excelSheet = excelSheet;
    }

    public String getExcelTable() {
        return excelTable;
    }

    public void setExcelTable(String excelTable) {
        mark("excelTable");
        this.excelTable = excelTable;
    }

    public String getCategoryField() {
        return categoryField;
    }

    public void setCategoryField(String categoryField) {
        mark("categoryField");
        this.categoryField = categoryField;
    }

    public String getGroupByField() {
        return groupByField;
    }

    public void setGroupByField(String groupByField) {
        mark("groupByField");
        this.groupByField = groupByField;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        mark("categories");
        this.categories = categories;
    }

    public ChartCategorySort getCategorySort() {
        return categorySort;
    }

    public void setCategorySort(ChartCategorySort categorySort) {
        mark("categorySort");
        this.categorySort = categorySort;
    }

    public List<ChartSeriesDefinition> getSeries() {
        return series;
    }

    public void setSeries(List<ChartSeriesDefinition> series) {
        mark("series");
        this.series = series;
    }

    public LegendPosition getLegendPosition() {
        return legendPosition;
    }

    public void setLegendPosition(LegendPosition legendPosition) {
        mark("legendPosition");
        this.legendPosition = legendPosition;
    }

    public BigDecimal getPrimaryAxisMin() {
        return primaryAxisMin;
    }

    public void setPrimaryAxisMin(BigDecimal primaryAxisMin) {
        mark("primaryAxisMin");
        this.primaryAxisMin = primaryAxisMin;
    }

    public BigDecimal getPrimaryAxisMax() {
        return primaryAxisMax;
    }

    public void setPrimaryAxisMax(BigDecimal primaryAxisMax) {
        mark("primaryAxisMax");
        this.primaryAxisMax = primaryAxisMax;
    }

    public BigDecimal getSecondaryAxisMin() {
        return secondaryAxisMin;
    }

    public void setSecondaryAxisMin(BigDecimal secondaryAxisMin) {
        mark("secondaryAxisMin");
        this.secondaryAxisMin = secondaryAxisMin;
    }

    public BigDecimal getSecondaryAxisMax() {
        return secondaryAxisMax;
    }

    public void setSecondaryAxisMax(BigDecimal secondaryAxisMax) {
        mark("secondaryAxisMax");
        this.secondaryAxisMax = secondaryAxisMax;
    }

    public ChartDataLabelMode getDataLabelMode() {
        return dataLabelMode;
    }

    public void setDataLabelMode(ChartDataLabelMode dataLabelMode) {
        mark("dataLabelMode");
        this.dataLabelMode = dataLabelMode;
    }

    public Integer getWidthPixels() {
        return widthPixels;
    }

    public void setWidthPixels(Integer widthPixels) {
        mark("widthPixels");
        this.widthPixels = widthPixels;
    }

    public Integer getHeightPixels() {
        return heightPixels;
    }

    public void setHeightPixels(Integer heightPixels) {
        mark("heightPixels");
        this.heightPixels = heightPixels;
    }

    public Integer getDpi() {
        return dpi;
    }

    public void setDpi(Integer dpi) {
        mark("dpi");
        this.dpi = dpi;
    }

    public ChartEmptyDataPolicy getEmptyDataPolicy() {
        return emptyDataPolicy;
    }

    public void setEmptyDataPolicy(ChartEmptyDataPolicy emptyDataPolicy) {
        mark("emptyDataPolicy");
        this.emptyDataPolicy = emptyDataPolicy;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        mark("emptyMessage");
        this.emptyMessage = emptyMessage;
    }

    @JsonIgnore
    public boolean hasProperty(String property) {
        return presentProperties.contains(property);
    }

    @JsonIgnore
    public Set<String> getPresentProperties() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(presentProperties));
    }

    private void mark(String property) {
        presentProperties.add(property);
    }
}
