package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 规则条件操作数取值引用定义模型。
 * <p>
 * 声明规则比较操作（Condition）中左/右操作数的取值来源（{@link Source}）：
 * <ul>
 *   <li>{@link Source#LITERAL}：字面常量（如 100、"DONE"）。</li>
 *   <li>{@link Source#CURRENT_FIELD}：当前评估行的字段列值。</li>
 *   <li>{@link Source#DATASET_FIELD}：其他数据集中的字段（标量或列表）。</li>
 *   <li>{@link Source#RUNTIME_PARAMETER}：外部运行时参数。</li>
 * </ul>
 * </p>
 */
public class ValueReferenceDefinition {

    /**
     * 值引用数据源类型枚举。
     */
    public enum Source {
        /** 字面量。 */
        LITERAL,
        /** 当前行的字段。 */
        CURRENT_FIELD,
        /** 指定数据集的字段。 */
        DATASET_FIELD,
        /** 运行时入参。 */
        RUNTIME_PARAMETER
    }

    /** 取值来源类型。 */
    private Source source;

    /** 字面常量值。 */
    private Object value;

    /** 目标数据集 ID。 */
    private String dataset;

    /** 目标字段名称。 */
    private String field;

    /** 目标运行时入参名称。 */
    private String parameter;

    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        mark("source");
        this.source = source;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        mark("value");
        this.value = value;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        mark("dataset");
        this.dataset = dataset;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        mark("field");
        this.field = field;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        mark("parameter");
        this.parameter = parameter;
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

    private void mark(String property) {
        presentProperties.add(property);
    }
}
