package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 数据集预期字段规格定义模型。
 * <p>
 * 声明数据集返回列的数据类型约束（STRING, INTEGER, DECIMAL, DATE_TIME 等）、
 * 是否为必填列（required）以及缺失或为空时的默认填充值（defaultValue）。
 * </p>
 */
public class FieldDefinition {

    /** 字段数据类型（如 STRING, INTEGER, LONG, DOUBLE, DECIMAL, BOOLEAN, DATE_TIME 等）。 */
    private String type;

    /** 是否为必需字段（不可缺失）。 */
    private boolean required;

    /** 字段缺失或为 NULL 时的预设默认值。 */
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
