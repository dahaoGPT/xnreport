package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

public class ExcelTableBinding {

    private String dataset;
    private String sheet;
    private String table;
    private Integer startRow = Integer.valueOf(0);
    private List<ColumnBinding> columns = new ArrayList<ColumnBinding>();
    @JsonIgnore
    private boolean columnsExplicitNull;

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getSheet() {
        return sheet;
    }

    public void setSheet(String sheet) {
        this.sheet = sheet;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Integer getStartRow() {
        return startRow;
    }

    public void setStartRow(Integer startRow) {
        this.startRow = startRow;
    }

    public List<ColumnBinding> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnBinding> columns) {
        this.columnsExplicitNull = columns == null;
        this.columns = columns == null
                ? new ArrayList<ColumnBinding>() : columns;
    }

    @JsonIgnore
    public boolean isColumnsExplicitNull() {
        return columnsExplicitNull;
    }

    public static class ColumnBinding {

        private String field;
        private String header;
        private String format;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }
}
