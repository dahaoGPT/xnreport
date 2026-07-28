package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DistributionDefinition {

    public enum LabelMode {
        COUNT,
        PERCENT,
        COUNT_AND_PERCENT
    }

    private String field;
    private List<BinDefinition> bins = new ArrayList<BinDefinition>();
    private LabelMode labelMode = LabelMode.COUNT_AND_PERCENT;
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

    public static class BinDefinition {

        private String id;
        private String label;
        private BigDecimal min;
        private Boolean minInclusive = Boolean.FALSE;
        private BigDecimal max;
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
