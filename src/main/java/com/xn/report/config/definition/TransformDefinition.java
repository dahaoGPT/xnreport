package com.xn.report.config.definition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TransformDefinition {

    private String type;
    private String field;
    private List<String> fields = new ArrayList<String>();
    private String operator;
    private Object value;
    private String sourceField;
    private String targetField;
    private BigDecimal operand;
    private String direction;
    private String nullOrder;
    private Integer limit;
    private Integer scale;
    private String divideByZeroStrategy;
    private BigDecimal divideByZeroDefault;
    private String fieldConflictStrategy;

    public String getType() {
        return type;
    }

    public void setType(String type) {
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
        this.fields = fields == null ? new ArrayList<String>() : fields;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
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

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getNullOrder() {
        return nullOrder;
    }

    public void setNullOrder(String nullOrder) {
        this.nullOrder = nullOrder;
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

    public String getDivideByZeroStrategy() {
        return divideByZeroStrategy;
    }

    public void setDivideByZeroStrategy(String divideByZeroStrategy) {
        this.divideByZeroStrategy = divideByZeroStrategy;
    }

    public BigDecimal getDivideByZeroDefault() {
        return divideByZeroDefault;
    }

    public void setDivideByZeroDefault(BigDecimal divideByZeroDefault) {
        this.divideByZeroDefault = divideByZeroDefault;
    }

    public String getFieldConflictStrategy() {
        return fieldConflictStrategy;
    }

    public void setFieldConflictStrategy(String fieldConflictStrategy) {
        this.fieldConflictStrategy = fieldConflictStrategy;
    }
}
