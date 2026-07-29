package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class NarrativeDefinition {

    public enum SourceType {
        FIXED_TEMPLATE,
        RULE_GENERATED
    }

    public enum EmptyStrategy {
        FAIL,
        OUTPUT_MESSAGE,
        SKIP
    }

    public enum AnalyzerType {
        TREND,
        DISTRIBUTION
    }

    private String id;
    private SourceType sourceType;
    private String template;
    private String analyzer;
    private AnalyzerType analyzerType;
    private String dataset;
    private String baseline;
    private String format;
    private String sentence;
    private EmptyStrategy emptyStrategy = EmptyStrategy.OUTPUT_MESSAGE;
    private Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    private DistributionDefinition distribution = new DistributionDefinition();
    private TrendDefinition trend;
    private PolicyDefinition policies = new PolicyDefinition();
    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        mark("id");
        this.id = id;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        mark("sourceType");
        this.sourceType = sourceType;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        mark("template");
        this.template = template;
    }

    public String getAnalyzer() {
        return analyzer;
    }

    public void setAnalyzer(String analyzer) {
        mark("analyzer");
        this.analyzer = analyzer;
    }

    public AnalyzerType getAnalyzerType() {
        return analyzerType;
    }

    public void setAnalyzerType(AnalyzerType analyzerType) {
        mark("analyzerType");
        this.analyzerType = analyzerType;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        mark("dataset");
        this.dataset = dataset;
    }

    public String getBaseline() {
        return baseline;
    }

    public void setBaseline(String baseline) {
        mark("baseline");
        this.baseline = baseline;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        mark("format");
        this.format = format;
    }

    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        mark("sentence");
        this.sentence = sentence;
    }

    public EmptyStrategy getEmptyStrategy() {
        return emptyStrategy;
    }

    public void setEmptyStrategy(EmptyStrategy emptyStrategy) {
        mark("emptyStrategy");
        this.emptyStrategy = emptyStrategy;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        mark("parameters");
        this.parameters = parameters;
    }

    public DistributionDefinition getDistribution() {
        return distribution;
    }

    public void setDistribution(DistributionDefinition distribution) {
        mark("distribution");
        this.distribution = distribution;
    }

    public TrendDefinition getTrend() {
        return trend;
    }

    public void setTrend(TrendDefinition trend) {
        mark("trend");
        this.trend = trend;
    }

    public PolicyDefinition getPolicies() {
        return policies;
    }

    public void setPolicies(PolicyDefinition policies) {
        mark("policies");
        this.policies = policies == null ? new PolicyDefinition() : policies;
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
