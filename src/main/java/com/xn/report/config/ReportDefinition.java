package com.xn.report.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.config.definition.WordDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportDefinition {

    private String schemaVersion;
    private ReportMetadata report = new ReportMetadata();
    private Map<String, ParameterDefinition> parameters =
            new LinkedHashMap<String, ParameterDefinition>();
    private List<DatasetDefinition> datasets = new ArrayList<DatasetDefinition>();
    private List<ChartDefinition> charts = new ArrayList<ChartDefinition>();
    @JsonIgnore
    private boolean chartsExplicitNull;
    private List<NarrativeDefinition> narratives = new ArrayList<NarrativeDefinition>();
    @JsonIgnore
    private boolean narrativesExplicitNull;
    private List<RuleDefinition> rules = new ArrayList<RuleDefinition>();
    @JsonIgnore
    private boolean rulesExplicitNull;
    private WordDefinition word = new WordDefinition();
    private PolicyDefinition policies = new PolicyDefinition();

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public ReportMetadata getReport() {
        return report;
    }

    public void setReport(ReportMetadata report) {
        this.report = report == null ? new ReportMetadata() : report;
    }

    public Map<String, ParameterDefinition> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, ParameterDefinition> parameters) {
        this.parameters = parameters == null
                ? new LinkedHashMap<String, ParameterDefinition>() : parameters;
    }

    public List<DatasetDefinition> getDatasets() {
        return datasets;
    }

    public void setDatasets(List<DatasetDefinition> datasets) {
        this.datasets = datasets == null ? new ArrayList<DatasetDefinition>() : datasets;
    }

    public List<NarrativeDefinition> getNarratives() {
        return narratives;
    }

    public List<ChartDefinition> getCharts() {
        return charts;
    }

    public void setCharts(List<ChartDefinition> charts) {
        this.chartsExplicitNull = charts == null;
        this.charts = charts == null
                ? new ArrayList<ChartDefinition>() : charts;
    }

    @JsonIgnore
    public boolean isChartsExplicitNull() {
        return chartsExplicitNull;
    }

    @JsonIgnore
    public boolean isNarrativesExplicitNull() {
        return narrativesExplicitNull;
    }

    public List<RuleDefinition> getRules() {
        return rules;
    }

    public void setRules(List<RuleDefinition> rules) {
        this.rulesExplicitNull = rules == null;
        this.rules = rules == null ? new ArrayList<RuleDefinition>() : rules;
    }

    @JsonIgnore
    public boolean isRulesExplicitNull() {
        return rulesExplicitNull;
    }

    public void setNarratives(List<NarrativeDefinition> narratives) {
        this.narrativesExplicitNull = narratives == null;
        this.narratives = narratives == null
                ? new ArrayList<NarrativeDefinition>() : narratives;
    }

    public WordDefinition getWord() {
        return word;
    }

    public void setWord(WordDefinition word) {
        this.word = word == null ? new WordDefinition() : word;
    }

    public PolicyDefinition getPolicies() {
        return policies;
    }

    public void setPolicies(PolicyDefinition policies) {
        this.policies = policies == null ? new PolicyDefinition() : policies;
    }
}
