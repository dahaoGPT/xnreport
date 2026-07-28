package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xn.report.transform.DivideByZeroStrategy;
import com.xn.report.transform.FieldConflictStrategy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TransformDefinition {

    private TransformType type;
    private String field;
    private List<String> fields;
    private List<SortFieldDefinition> sortFields;
    private TransformOperator operator;
    private Object value;
    private String sourceField;
    private String targetField;
    private BigDecimal operand;
    private Integer limit;
    private Integer scale;
    private DivideByZeroStrategy divideByZeroStrategy;
    private BigDecimal divideByZeroDefault;
    private FieldConflictStrategy fieldConflictStrategy;
    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public TransformType getType() {
        return type;
    }

    public void setType(TransformType type) {
        markPresent("type");
        this.type = type;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        markPresent("field");
        this.field = field;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        markPresent("fields");
        this.fields = fields;
    }

    public List<SortFieldDefinition> getSortFields() {
        return sortFields;
    }

    public void setSortFields(List<SortFieldDefinition> sortFields) {
        markPresent("sortFields");
        this.sortFields = sortFields;
    }

    public TransformOperator getOperator() {
        return operator;
    }

    public void setOperator(TransformOperator operator) {
        markPresent("operator");
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        markPresent("value");
        this.value = value;
    }

    @JsonIgnore
    public boolean hasValue() {
        return hasProperty("value");
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        markPresent("sourceField");
        this.sourceField = sourceField;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        markPresent("targetField");
        this.targetField = targetField;
    }

    public BigDecimal getOperand() {
        return operand;
    }

    public void setOperand(BigDecimal operand) {
        markPresent("operand");
        this.operand = operand;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        markPresent("limit");
        this.limit = limit;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        markPresent("scale");
        this.scale = scale;
    }

    public DivideByZeroStrategy getDivideByZeroStrategy() {
        return divideByZeroStrategy;
    }

    public void setDivideByZeroStrategy(
            DivideByZeroStrategy divideByZeroStrategy) {
        markPresent("divideByZeroStrategy");
        this.divideByZeroStrategy = divideByZeroStrategy;
    }

    public BigDecimal getDivideByZeroDefault() {
        return divideByZeroDefault;
    }

    public void setDivideByZeroDefault(BigDecimal divideByZeroDefault) {
        markPresent("divideByZeroDefault");
        this.divideByZeroDefault = divideByZeroDefault;
    }

    public FieldConflictStrategy getFieldConflictStrategy() {
        return fieldConflictStrategy;
    }

    public void setFieldConflictStrategy(
            FieldConflictStrategy fieldConflictStrategy) {
        markPresent("fieldConflictStrategy");
        this.fieldConflictStrategy = fieldConflictStrategy;
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

    private void markPresent(String property) {
        presentProperties.add(property);
    }
}
