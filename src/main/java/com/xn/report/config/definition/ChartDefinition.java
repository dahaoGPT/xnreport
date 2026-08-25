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

/**
 * 图表配置定义模型。
 * <p>
 * 在报表配置文件（YAML）中声明图表的元数据、数据源绑定、展示模式、分类维度与度量系列、坐标轴配置及导出图片尺寸与分辨率：
 * <ul>
 *   <li><b>渲染模式（{@link Mode}）</b>：支持 GENERATED_NATIVE（纯代码基于 JFreeChart 动态生成）、TEMPLATE_NATIVE（替换 Excel/Word 模板中的原生图表数据）与 IMAGE。</li>
 *   <li><b>数据绑定</b>：指定绑定的数据集 ID（dataset）、分类字段（categoryField）、分组字段（groupByField）及多系列配置（series）。</li>
 *   <li><b>坐标轴与样式</b>：主/次坐标轴范围（primaryAxisMin/Max、secondaryAxisMin/Max）、数据标签显示模式（dataLabelMode）、图例位置（legendPosition）。</li>
 *   <li><b>图片渲染规格</b>：宽（widthPixels）、高（heightPixels）以及 DPI 分辨率。</li>
 * </ul>
 * </p>
 */
public class ChartDefinition {

    /**
     * 图表渲染模式枚举。
     */
    public enum Mode {
        /** 使用 JFreeChart 动态生成图片并插入文档。 */
        GENERATED_NATIVE,

        /** 绑定并更新 Excel/Word 模板中已存在的原生图表数据源。 */
        TEMPLATE_NATIVE,

        /** 图片插入模式。 */
        IMAGE;

        @com.fasterxml.jackson.annotation.JsonCreator
        public static Mode fromConfig(String value) {
            return ChartEnumValue.parse(Mode.class, value);
        }
    }

    /** 图表唯一标识。 */
    private String id;

    /** 图表主标题文本。 */
    private String title;

    /** 图表渲染模式（默认为 GENERATED_NATIVE）。 */
    private Mode mode = Mode.GENERATED_NATIVE;

    /** 绑定的源数据集 ID。 */
    private String dataset;

    /** 对应 Excel 模板中的工作表名称（用于 TEMPLATE_NATIVE 模式）。 */
    private String excelSheet;

    /** 对应 Excel 模板中的表格名称。 */
    private String excelTable;

    /** 模板中用于匹配定位该图表的标记文本（如 "[CHART:api_trend]"）。 */
    private String templateChartMarker;

    /** 模板中原生图表的索引序号（0-based）。 */
    private Integer templateChartIndex;

    /** 分组原生图表定位器列表（用于按维度动态替换多个模板图表）。 */
    private List<TemplateChartLocatorDefinition> templateChartLocators =
            new ArrayList<TemplateChartLocatorDefinition>();

    /** Excel 中插入图表的起始锚点行号（0-based）。 */
    private Integer anchorRow;

    /** Excel 中插入图表的起始锚点列号（0-based）。 */
    private Integer anchorColumn;

    /** Excel 中图表占据的列宽度跨度。 */
    private Integer anchorWidthColumns;

    /** Excel 中图表占据的行高度跨度。 */
    private Integer anchorHeightRows;

    /** 分类 X 轴字段名称。 */
    private String categoryField;

    /** 分组/分面维度字段名称。 */
    private String groupByField;

    /** 显式声明的分类轴固定顺序列表。 */
    private List<String> categories = new ArrayList<String>();

    /** 分类轴默认排序规则（ASC 或 DESC）。 */
    private ChartCategorySort categorySort = ChartCategorySort.ASC;

    /** 图表包含的度量数据系列列表。 */
    private List<ChartSeriesDefinition> series =
            new ArrayList<ChartSeriesDefinition>();

    /** 图例显示位置（TOP, BOTTOM, LEFT, RIGHT, NONE）。 */
    private LegendPosition legendPosition = LegendPosition.BOTTOM;

    /** 主数值 Y 轴最小值。 */
    private BigDecimal primaryAxisMin;

    /** 主数值 Y 轴最大值。 */
    private BigDecimal primaryAxisMax;

    /** 次数值 Y 轴最小值。 */
    private BigDecimal secondaryAxisMin;

    /** 次数值 Y 轴最大值。 */
    private BigDecimal secondaryAxisMax;

    /** 数据点标签显示模式（NONE, VALUE, PERCENT, CATEGORY 等）。 */
    private ChartDataLabelMode dataLabelMode = ChartDataLabelMode.NONE;

    /** 生成图表图片的宽度像素（默认 1600）。 */
    private Integer widthPixels = 1600;

    /** 生成图表图片的高度像素（默认 850）。 */
    private Integer heightPixels = 850;

    /** 图表图片 DPI 清晰度（默认 180）。 */
    private Integer dpi = 180;

    /** 数据集为空时的图表处理策略。 */
    private ChartEmptyDataPolicy emptyDataPolicy =
            ChartEmptyDataPolicy.OUTPUT_MESSAGE;

    /** 空数据时渲染的提示文案（默认“暂无图表数据”）。 */
    private String emptyMessage = "暂无图表数据";

    /** 图表级别的异常降级策略。 */
    private PolicyDefinition policies = new PolicyDefinition();

    /** 记录配置文件中显式出现的属性名（用于反序列化校验）。 */
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

    public String getTemplateChartMarker() {
        return templateChartMarker;
    }

    public void setTemplateChartMarker(String templateChartMarker) {
        mark("templateChartMarker");
        this.templateChartMarker = templateChartMarker;
    }

    public Integer getTemplateChartIndex() {
        return templateChartIndex;
    }

    public void setTemplateChartIndex(Integer templateChartIndex) {
        mark("templateChartIndex");
        this.templateChartIndex = templateChartIndex;
    }

    public List<TemplateChartLocatorDefinition> getTemplateChartLocators() {
        return templateChartLocators;
    }

    public void setTemplateChartLocators(
            List<TemplateChartLocatorDefinition> templateChartLocators) {
        mark("templateChartLocators");
        this.templateChartLocators = templateChartLocators;
    }

    public Integer getAnchorRow() {
        return anchorRow;
    }

    public void setAnchorRow(Integer anchorRow) {
        mark("anchorRow");
        this.anchorRow = anchorRow;
    }

    public Integer getAnchorColumn() {
        return anchorColumn;
    }

    public void setAnchorColumn(Integer anchorColumn) {
        mark("anchorColumn");
        this.anchorColumn = anchorColumn;
    }

    public Integer getAnchorWidthColumns() {
        return anchorWidthColumns;
    }

    public void setAnchorWidthColumns(Integer anchorWidthColumns) {
        mark("anchorWidthColumns");
        this.anchorWidthColumns = anchorWidthColumns;
    }

    public Integer getAnchorHeightRows() {
        return anchorHeightRows;
    }

    public void setAnchorHeightRows(Integer anchorHeightRows) {
        mark("anchorHeightRows");
        this.anchorHeightRows = anchorHeightRows;
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

    public PolicyDefinition getPolicies() {
        return policies;
    }

    public void setPolicies(PolicyDefinition policies) {
        mark("policies");
        this.policies = policies == null ? new PolicyDefinition() : policies;
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
