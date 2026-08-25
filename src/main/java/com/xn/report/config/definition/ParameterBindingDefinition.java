package com.xn.report.config.definition;

/**
 * SQL 命名参数绑定定义模型。
 * <p>
 * 声明 SQL 查询中所需命名参数的来源与映射方式：
 * <ul>
 *   <li><b>参数来源（{@link #getFrom()}）</b>：{@link ParameterSource#RUNTIME}（来自外部运行时入参）、{@link ParameterSource#CONSTANT}（字面常量）、{@link ParameterSource#DATASET}（来自前置数据集）。</li>
 *   <li><b>取值配置</b>：运行时变量 key、常量 value、或前置数据集 ID（dataset）及对应字段列名（field）。</li>
 * </ul>
 * </p>
 */
public class ParameterBindingDefinition {

    /** 参数值来源渠道（RUNTIME, CONSTANT, DATASET）。 */
    private ParameterSource from;

    /** 运行时入参名称。 */
    private String key;

    /** 常量字面值。 */
    private Object value;

    /** 前置依赖数据集 ID。 */
    private String dataset;

    /** 前置数据集中提取的字段列名。 */
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
