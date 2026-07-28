package com.xn.report.config.definition;

import java.util.LinkedHashMap;
import java.util.Map;

public class NarrativeDefinition {

    private String id;
    private String sourceType;
    private String template;
    private String analyzer;
    private String dataset;
    private String baseline;
    private String format;
    private String sentence;
    private String emptyStrategy;
    private Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    private DistributionDefinition distribution = new DistributionDefinition();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getAnalyzer() {
        return analyzer;
    }

    public void setAnalyzer(String analyzer) {
        this.analyzer = analyzer;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getBaseline() {
        return baseline;
    }

    public void setBaseline(String baseline) {
        this.baseline = baseline;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    public String getEmptyStrategy() {
        return emptyStrategy;
    }

    public void setEmptyStrategy(String emptyStrategy) {
        this.emptyStrategy = emptyStrategy;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters == null
                ? new LinkedHashMap<String, Object>() : parameters;
    }

    public DistributionDefinition getDistribution() {
        return distribution;
    }

    public void setDistribution(DistributionDefinition distribution) {
        this.distribution = distribution == null
                ? new DistributionDefinition() : distribution;
    }
}
