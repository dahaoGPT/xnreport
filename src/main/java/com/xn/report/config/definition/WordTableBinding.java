package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

public class WordTableBinding {

    private String id;
    private String dataset;
    private String marker;
    private String tableId;
    private String strategy = "PROTOTYPE";
    private List<WordTableColumnDefinition> columns =
            new ArrayList<WordTableColumnDefinition>();
    private String emptyStrategy = "SHOW_EMPTY";
    private String emptyMessage = "暂无数据";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<WordTableColumnDefinition> getColumns() {
        return columns;
    }

    public void setColumns(List<WordTableColumnDefinition> columns) {
        this.columns = columns == null
                ? new ArrayList<WordTableColumnDefinition>() : columns;
    }

    public String getEmptyStrategy() {
        return emptyStrategy;
    }

    public void setEmptyStrategy(String emptyStrategy) {
        this.emptyStrategy = emptyStrategy;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        this.emptyMessage = emptyMessage;
    }
}
