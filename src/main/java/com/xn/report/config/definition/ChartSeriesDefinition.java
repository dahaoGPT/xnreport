package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xn.report.chart.ChartAxis;
import com.xn.report.chart.ChartDataLabelMode;
import com.xn.report.chart.ChartLineStyle;
import com.xn.report.chart.ChartNullHandling;
import com.xn.report.chart.ChartType;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 图表度量数据系列定义模型。
 * <p>
 * 定义图表中单个数据系列（Series）的取值字段、系列名称、图表类型、归属坐标轴、堆叠分组、颜色、线条样式与数据标签格式：
 * <ul>
 *   <li><b>图表类型（{@link ChartType}）</b>：COLUMN、BAR、LINE、AREA、PIE、DONUT、SCATTER、BUBBLE、RADAR、STOCK 等。</li>
 *   <li><b>坐标轴与堆叠</b>：归属主轴/次轴（{@link ChartAxis}），堆叠图表通过 stackGroup 绑定同一组。</li>
 *   <li><b>样式与格式</b>：RGB 颜色 Hex、实线/虚线（{@link ChartLineStyle}）、线宽、数据点标记（marker）、数值格式化 pattern（format）。</li>
 * </ul>
 * </p>
 */
public class ChartSeriesDefinition {

    /** 数据系列对应的数值度量字段名称。 */
    private String field;

    /** 图例中显示的系列展示名称。 */
    private String name;

    /** 当前系列的图表展现类型（如 COLUMN, LINE, PIE 等）。 */
    private ChartType type;

    /** 该系列绑定的数值轴（PRIMARY 主轴或 SECONDARY 次轴）。 */
    private ChartAxis axis = ChartAxis.PRIMARY;

    /** 堆叠图表的分组标识（属于同一 stackGroup 的系列堆叠在一起）。 */
    private String stackGroup;

    /** 系列显示颜色（6位十六进制 RGB，如 "#1890FF"）。 */
    private String color;

    /** 折线/边框线型（SOLID 实线, DASHED 虚线, DOTTED 点线）。 */
    private ChartLineStyle lineStyle = ChartLineStyle.SOLID;

    /** 线条宽度（默认 2.0）。 */
    private BigDecimal lineWidth = BigDecimal.valueOf(2);

    /** 是否显示数据点上的折点标记图标。 */
    private Boolean marker = Boolean.FALSE;

    /** 当前系列的数据标签显示模式。 */
    private ChartDataLabelMode dataLabels = ChartDataLabelMode.NONE;

    /** 数据标签数值格式化模式（如 "#,##0.00"、"0.0%"）。 */
    private String format;

    /** 空值（null）数据点的连接处理方式（GAP 间断, ZERO 归零, CONNECT 连线）。 */
    private ChartNullHandling nullHandling = ChartNullHandling.GAP;

    /** 图例项排序序号。 */
    private Integer legendOrder;

    /** 气泡图（BUBBLE）专用的气泡大小字段名称。 */
    private String sizeField;

    /** 显式出现的配置属性记录集合。 */
    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public String getField() {
        return field;
    }

    public void setField(String field) {
        mark("field");
        this.field = field;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        mark("name");
        this.name = name;
    }

    public ChartType getType() {
        return type;
    }

    public void setType(ChartType type) {
        mark("type");
        this.type = type;
    }

    public ChartAxis getAxis() {
        return axis;
    }

    public void setAxis(ChartAxis axis) {
        mark("axis");
        this.axis = axis;
    }

    public String getStackGroup() {
        return stackGroup;
    }

    public void setStackGroup(String stackGroup) {
        mark("stackGroup");
        this.stackGroup = stackGroup;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        mark("color");
        this.color = color;
    }

    public ChartLineStyle getLineStyle() {
        return lineStyle;
    }

    public void setLineStyle(ChartLineStyle lineStyle) {
        mark("lineStyle");
        this.lineStyle = lineStyle;
    }

    public BigDecimal getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(BigDecimal lineWidth) {
        mark("lineWidth");
        this.lineWidth = lineWidth;
    }

    public boolean isMarker() {
        return Boolean.TRUE.equals(marker);
    }

    public Boolean getMarker() {
        return marker;
    }

    public void setMarker(Boolean marker) {
        mark("marker");
        this.marker = marker;
    }

    public ChartDataLabelMode getDataLabels() {
        return dataLabels;
    }

    public void setDataLabels(ChartDataLabelMode dataLabels) {
        mark("dataLabels");
        this.dataLabels = dataLabels;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        mark("format");
        this.format = format;
    }

    public ChartNullHandling getNullHandling() {
        return nullHandling;
    }

    public void setNullHandling(ChartNullHandling nullHandling) {
        mark("nullHandling");
        this.nullHandling = nullHandling;
    }

    public Integer getLegendOrder() {
        return legendOrder;
    }

    public void setLegendOrder(Integer legendOrder) {
        mark("legendOrder");
        this.legendOrder = legendOrder;
    }

    public String getSizeField() {
        return sizeField;
    }

    public void setSizeField(String sizeField) {
        mark("sizeField");
        this.sizeField = sizeField;
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
