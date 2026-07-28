package com.xn.report.config.definition;

import com.xn.report.transform.DivideByZeroStrategy;
import com.xn.report.transform.FieldConflictStrategy;
import java.math.BigDecimal;
import java.util.List;

public class TransformDefinition {

    private TransformType type;
    private String field;
    private List<String> fields;
    private List<SortFieldDefinition> sortFields;
    private TransformOperator operator;
    private Object value;
    private boolean valueConfigured;
    private String sourceField;
    private String targetField;
    private BigDecimal operand;
    private Integer limit;
    private Integer scale;
    private DivideByZeroStrategy divideByZeroStrategy;
    private BigDecimal divideByZeroDefault;
    private FieldConflictStrategy fieldConflictStrategy;

    public TransformType getType() {
        return type;
    }

    public void setType(TransformType type) {
        this.type = type;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public List<SortFieldDefinition> getSortFields() {
        return sortFields;
    }

    public void setSortFields(List<SortFieldDefinition> sortFields) {
        this.sortFields = sortFields;
    }

    public TransformOperator getOperator() {
        return operator;
    }

    public void setOperator(TransformOperator operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
        this.valueConfigured = true;
    }

    public boolean hasValue() {
        return valueConfigured;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public BigDecimal getOperand() {
        return operand;
    }

    public void setOperand(BigDecimal operand) {
        this.operand = operand;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public DivideByZeroStrategy getDivideByZeroStrategy() {
        return divideByZeroStrategy;
    }

    public void setDivideByZeroStrategy(
            DivideByZeroStrategy divideByZeroStrategy) {
        this.divideByZeroStrategy = divideByZeroStrategy;
    }

    public BigDecimal getDivideByZeroDefault() {
        return divideByZeroDefault;
    }

    public void setDivideByZeroDefault(BigDecimal divideByZeroDefault) {
        this.divideByZeroDefault = divideByZeroDefault;
    }

    public FieldConflictStrategy getFieldConflictStrategy() {
        return fieldConflictStrategy;
    }

    public void setFieldConflictStrategy(
            FieldConflictStrategy fieldConflictStrategy) {
        this.fieldConflictStrategy = fieldConflictStrategy;
    }
}
