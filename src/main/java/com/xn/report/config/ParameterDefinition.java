package com.xn.report.config;

/**
 * 报表入参定义模型。
 * <p>
 * 在报表配置文件（YAML）中声明报表所需的外部运行时入参规范：
 * 包括数据类型（STRING、DATE_TIME、LIST_STRING 等）、是否必填（required）、
 * 列表参数的最小/最大元素个数（minItems、maxItems）以及默认值（defaultValue）。
 * </p>
 */
public class ParameterDefinition {

    /** 参数数据类型（如 STRING, DATE_TIME, LIST_STRING, INTEGER, DOUBLE 等）。 */
    private String type;

    /** 是否为必需参数。 */
    private boolean required;

    /** 集合类型参数的最小元素数量限制。 */
    private Integer minItems;

    /** 集合类型参数的最大元素数量限制。 */
    private Integer maxItems;

    /** 未提供参数时的默认值。 */
    private Object defaultValue;

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

    public Integer getMinItems() {
        return minItems;
    }

    public void setMinItems(Integer minItems) {
        this.minItems = minItems;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
}
