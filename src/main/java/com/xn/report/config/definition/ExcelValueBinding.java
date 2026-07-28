package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ExcelValueBinding {

    private String sheet;
    private String cell;
    private String value;
    private String format;
    @JsonIgnore
    private boolean formatPresent;

    public String getSheet() {
        return sheet;
    }

    public void setSheet(String sheet) {
        this.sheet = sheet;
    }

    public String getCell() {
        return cell;
    }

    public void setCell(String cell) {
        this.cell = cell;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.formatPresent = true;
        this.format = format;
    }

    @JsonIgnore
    public boolean isFormatPresent() {
        return formatPresent;
    }
}
