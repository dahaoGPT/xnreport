package com.xn.report.config.definition;

public class ParameterBindingDefinition {

    private ParameterSource from;
    private String key;
    private Object value;
    private String dataset;
    private String field;

    public ParameterSource getFrom() {
        return from;
    }

    public void setFrom(ParameterSource from) {
        this.from = from;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
