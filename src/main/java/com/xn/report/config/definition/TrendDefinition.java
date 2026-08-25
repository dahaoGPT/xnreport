package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 趋势分析配置定义模型。
 * <p>
 * 声明在文字段落生成中自动计算数值走势（如上升、下降、持平、异常波动）的规则与基准源：
 * <ul>
 *   <li><b>基准源（{@link ComparisonSource}）</b>：PREVIOUS_YEAR（同比去年）、ANNUAL_BASELINE（基线）、LITERAL（常数）、DATASET_FIELD（对比数据集）、RUNTIME_PARAMETER（入参）。</li>
 *   <li><b>阈值与容差</b>：持平容差范围（flatTolerance）、异常剧烈波动告警阈值（abnormalThreshold）。</li>
 * </ul>
 * </p>
 */
public class TrendDefinition {

    /**
     * 趋势对比基准数据源类型枚举。
     */
    public enum ComparisonSource {
        /** 同比去年同期数据。 */
        PREVIOUS_YEAR,
        /** 全年基线数据。 */
        ANNUAL_BASELINE,
        /** 固定常数。 */
        LITERAL,
        /** 另一数据集字段。 */
        DATASET_FIELD,
        /** 外部运行时参数。 */
        RUNTIME_PARAMETER
    }

    /** 时间周期维度字段名。 */
    private String periodField;

    /** 待分析的度量数值字段名。 */
    private String valueField;

    /** 对比基准来源类型。 */
    private ComparisonSource comparisonSource;

    /** 对比数据集 ID。 */
    private String comparisonDataset;

    /** 对比字段名。 */
    private String comparisonField;

    /** 对比入参名称。 */
    private String comparisonParameter;

    /** 对比常量值。 */
    private BigDecimal comparisonValue;

    /** 判定为“基本持平”的容差阈值（默认 0）。 */
    private BigDecimal flatTolerance = BigDecimal.ZERO;

    /** 判定为“剧烈异常”的波动率阈值（如 0.5 表示变动超过 50% 视为异常）。 */
    private BigDecimal abnormalThreshold;

    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public String getPeriodField() {
        return periodField;
    }

    public void setPeriodField(String periodField) {
        mark("periodField");
        this.periodField = periodField;
    }

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        mark("valueField");
        this.valueField = valueField;
    }

    public ComparisonSource getComparisonSource() {
        return comparisonSource;
    }

    public void setComparisonSource(ComparisonSource comparisonSource) {
        mark("comparisonSource");
        this.comparisonSource = comparisonSource;
    }

    public String getComparisonDataset() {
        return comparisonDataset;
    }

    public void setComparisonDataset(String comparisonDataset) {
        mark("comparisonDataset");
        this.comparisonDataset = comparisonDataset;
    }

    public String getComparisonField() {
        return comparisonField;
    }

    public void setComparisonField(String comparisonField) {
        mark("comparisonField");
        this.comparisonField = comparisonField;
    }

    public String getComparisonParameter() {
        return comparisonParameter;
    }

    public void setComparisonParameter(String comparisonParameter) {
        mark("comparisonParameter");
        this.comparisonParameter = comparisonParameter;
    }

    public BigDecimal getComparisonValue() {
        return comparisonValue;
    }

    public void setComparisonValue(BigDecimal comparisonValue) {
        mark("comparisonValue");
        this.comparisonValue = comparisonValue;
    }

    public BigDecimal getFlatTolerance() {
        return flatTolerance;
    }

    public void setFlatTolerance(BigDecimal flatTolerance) {
        mark("flatTolerance");
        this.flatTolerance = flatTolerance;
    }

    public BigDecimal getAbnormalThreshold() {
        return abnormalThreshold;
    }

    public void setAbnormalThreshold(BigDecimal abnormalThreshold) {
        mark("abnormalThreshold");
        this.abnormalThreshold = abnormalThreshold;
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
