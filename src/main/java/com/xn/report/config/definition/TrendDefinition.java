package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class TrendDefinition {

    public enum ComparisonSource {
        PREVIOUS_YEAR,
        ANNUAL_BASELINE,
        LITERAL,
        DATASET_FIELD,
        RUNTIME_PARAMETER
    }

    private String periodField;
    private String valueField;
    private ComparisonSource comparisonSource;
    private String comparisonDataset;
    private String comparisonField;
    private String comparisonParameter;
    private BigDecimal comparisonValue;
    private BigDecimal flatTolerance = BigDecimal.ZERO;
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
