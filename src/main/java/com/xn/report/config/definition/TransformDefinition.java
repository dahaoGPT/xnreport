package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xn.report.transform.DivideByZeroStrategy;
import com.xn.report.transform.FieldConflictStrategy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 内存数据集派生转换操作配置定义模型。
 * <p>
 * 声明在数据集加载完成后执行的内存转换操作（{@link TransformType}）：
 * <ul>
 *   <li><b>FILTER</b>：根据条件操作符（{@link TransformOperator}）和目标值（value）过滤数据行。</li>
 *   <li><b>SORT</b>：根据多字段排序规则（sortFields）重排数据行。</li>
 *   <li><b>DISTINCT</b>：根据指定字段集合（fields）去重。</li>
 *   <li><b>LIMIT</b>：截断保留前 N 行（limit）。</li>
 *   <li><b>DERIVED_FIELD</b>：基于源字段（sourceField）与操作数（operand）进行四则运算生成新字段（targetField），并支持精度（scale）、除零处理策略（divideByZeroStrategy）与同名覆盖冲突策略（fieldConflictStrategy）。</li>
 * </ul>
 * </p>
 */
public class TransformDefinition {

    /** 转换操作类型（FILTER, SORT, DISTINCT, LIMIT, DERIVED_FIELD）。 */
    private TransformType type;

    /** 单字段过滤或操作作用字段。 */
    private String field;

    /** 多字段去重时的字段列表。 */
    private List<String> fields;

    /** 多字段排序时的字段列表。 */
    private List<SortFieldDefinition> sortFields;

    /** 比较或算术操作符（EQ, GT, ADD, DIVIDE 等）。 */
    private TransformOperator operator;

    /** 过滤比较目标值。 */
    private Object value;

    /** 派生计算的源输入字段名。 */
    private String sourceField;

    /** 派生计算的新增目标字段名。 */
    private String targetField;

    /** 算术四则运算的第二个操作数（常数）。 */
    private BigDecimal operand;

    /** 截断最大行数。 */
    private Integer limit;

    /** 算术除法/乘法计算的小数精度保留位数。 */
    private Integer scale;

    /** 除以零（DivideByZero）时的容错策略。 */
    private DivideByZeroStrategy divideByZeroStrategy;

    /** 除以零时填充的替代默认值。 */
    private BigDecimal divideByZeroDefault;

    /** 派生字段与现有字段名称冲突时的处理策略。 */
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
