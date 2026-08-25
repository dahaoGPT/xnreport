package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分布统计分析定义模型。
 * <p>
 * 用于在文字段落生成（Narrative）或图表中对数值型字段执行分箱（Binning）区间频数与占比统计。
 * </p>
 */
public class DistributionDefinition {

    /**
     * 分布结果标签展示模式枚举。
     */
    public enum LabelMode {
        /** 仅显示频数计数（如 "32次"）。 */
        COUNT,
        /** 仅显示百分比占比（如 "45.5%"）。 */
        PERCENT,
        /** 同时显示频数与百分比（如 "32次 (45.5%)"）。 */
        COUNT_AND_PERCENT
    }

    /** 待统计分布的数值字段名称。 */
    private String field;

    /** 分箱区间定义列表。 */
    private List<BinDefinition> bins = new ArrayList<BinDefinition>();

    /** 标签展示模式（默认 COUNT_AND_PERCENT）。 */
    private LabelMode labelMode = LabelMode.COUNT_AND_PERCENT;

    /** 显式配置属性记录集合。 */
    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public String getField() {
        return field;
    }

    public void setField(String field) {
        mark("field");
        this.field = field;
    }

    public List<BinDefinition> getBins() {
        return bins;
    }

    public void setBins(List<BinDefinition> bins) {
        mark("bins");
        this.bins = bins;
    }

    public LabelMode getLabelMode() {
        return labelMode;
    }

    public void setLabelMode(LabelMode labelMode) {
        mark("labelMode");
        this.labelMode = labelMode;
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

    /**
     * 单个分箱区间定义模型。
     */
    public static class BinDefinition {

        /** 分箱区间唯一标识（如 "le_1d"）。 */
        private String id;

        /** 分箱文本展示标签（如 "1天之内"）。 */
        private String label;

        /** 区间下界最小值（null 表示负无穷）。 */
        private BigDecimal min;

        /** 下界是否为闭区间（包含最小值，默认 false）。 */
        private Boolean minInclusive = Boolean.FALSE;

        /** 区间上界最大值（null 表示正无穷）。 */
        private BigDecimal max;

        /** 上界是否为闭区间（包含最大值，默认 false）。 */
        private Boolean maxInclusive = Boolean.FALSE;

        @JsonIgnore
        private final Set<String> presentProperties =
                new LinkedHashSet<String>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            mark("id");
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            mark("label");
            this.label = label;
        }

        public BigDecimal getMin() {
            return min;
        }

        public void setMin(BigDecimal min) {
            mark("min");
            this.min = min;
        }

        public boolean isMinInclusive() {
            return Boolean.TRUE.equals(minInclusive);
        }

        public Boolean getMinInclusive() {
            return minInclusive;
        }

        public void setMinInclusive(Boolean minInclusive) {
            mark("minInclusive");
            this.minInclusive = minInclusive;
        }

        public BigDecimal getMax() {
            return max;
        }

        public void setMax(BigDecimal max) {
            mark("max");
            this.max = max;
        }

        public boolean isMaxInclusive() {
            return Boolean.TRUE.equals(maxInclusive);
        }

        public Boolean getMaxInclusive() {
            return maxInclusive;
        }

        public void setMaxInclusive(Boolean maxInclusive) {
            mark("maxInclusive");
            this.maxInclusive = maxInclusive;
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
}
