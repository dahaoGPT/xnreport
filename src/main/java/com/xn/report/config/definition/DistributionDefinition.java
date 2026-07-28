package com.xn.report.config.definition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DistributionDefinition {

    private String field;
    private List<BinDefinition> bins = new ArrayList<BinDefinition>();
    private String labelMode;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public List<BinDefinition> getBins() {
        return bins;
    }

    public void setBins(List<BinDefinition> bins) {
        this.bins = bins == null ? new ArrayList<BinDefinition>() : bins;
    }

    public String getLabelMode() {
        return labelMode;
    }

    public void setLabelMode(String labelMode) {
        this.labelMode = labelMode;
    }

    public static class BinDefinition {

        private String id;
        private String label;
        private BigDecimal min;
        private boolean minInclusive;
        private BigDecimal max;
        private boolean maxInclusive;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public BigDecimal getMin() {
            return min;
        }

        public void setMin(BigDecimal min) {
            this.min = min;
        }

        public boolean isMinInclusive() {
            return minInclusive;
        }

        public void setMinInclusive(boolean minInclusive) {
            this.minInclusive = minInclusive;
        }

        public BigDecimal getMax() {
            return max;
        }

        public void setMax(BigDecimal max) {
            this.max = max;
        }

        public boolean isMaxInclusive() {
            return maxInclusive;
        }

        public void setMaxInclusive(boolean maxInclusive) {
            this.maxInclusive = maxInclusive;
        }
    }
}
