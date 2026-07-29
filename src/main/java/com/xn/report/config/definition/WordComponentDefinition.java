package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

public class WordComponentDefinition {

    private String type;
    private String text;
    private String chartId;
    private String narrativeId;
    private String tableId;
    private String dataset;
    private List<WordTableColumnDefinition> columns =
            new ArrayList<WordTableColumnDefinition>();
    private String emptyMessage = "暂无数据";
    private Double widthInches;
    private String alignment = WordImageAlignment.CENTER.name();
    private String caption;
    private String altText;
    private String title;
    private String description;
    private List<String> items = new ArrayList<String>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getChartId() {
        return chartId;
    }

    public void setChartId(String chartId) {
        this.chartId = chartId;
    }

    public String getNarrativeId() {
        return narrativeId;
    }

    public void setNarrativeId(String narrativeId) {
        this.narrativeId = narrativeId;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public List<WordTableColumnDefinition> getColumns() {
        return columns;
    }

    public void setColumns(List<WordTableColumnDefinition> columns) {
        this.columns = columns == null
                ? new ArrayList<WordTableColumnDefinition>() : columns;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        this.emptyMessage = emptyMessage;
    }

    public Double getWidthInches() {
        return widthInches;
    }

    public void setWidthInches(Double widthInches) {
        this.widthInches = widthInches;
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items == null ? new ArrayList<String>() : items;
    }
}
