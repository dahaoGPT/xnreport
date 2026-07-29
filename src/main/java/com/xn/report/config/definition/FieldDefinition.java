package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FieldDefinition {

    private String type;
    private boolean required;
    private Object defaultValue;
    @JsonIgnore
    private boolean defaultValuePresent;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValuePresent = true;
        this.defaultValue = defaultValue;
    }

    @JsonIgnore
    public boolean hasDefaultValue() {
        return defaultValuePresent;
    }
}
