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

public class ChartSeriesDefinition {

    private String field;
    private String name;
    private ChartType type;
    private ChartAxis axis = ChartAxis.PRIMARY;
    private String stackGroup;
    private String color;
    private ChartLineStyle lineStyle = ChartLineStyle.SOLID;
    private BigDecimal lineWidth = BigDecimal.valueOf(2);
    private Boolean marker = Boolean.FALSE;
    private ChartDataLabelMode dataLabels = ChartDataLabelMode.NONE;
    private String format;
    private ChartNullHandling nullHandling = ChartNullHandling.GAP;
    private Integer legendOrder;
    private String sizeField;
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
