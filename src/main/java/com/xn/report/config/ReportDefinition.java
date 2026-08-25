package com.xn.report.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.config.definition.WordDefinition;
import com.xn.report.config.definition.ExcelDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表根配置定义模型。
 * <p>
 * 对应整个报表 YAML/JSON 配置文件的根对象树结构。涵盖了报表 Schema 版本、
 * 报表元数据（{@link ReportMetadata}）、入参声明（{@link ParameterDefinition}）、
 * 数据集列表（{@link DatasetDefinition}）、图表定义列表（{@link ChartDefinition}）、
 * 文字段落定义列表（{@link NarrativeDefinition}）、规则定义列表（{@link RuleDefinition}）、
 * Word 文档渲染配置（{@link WordDefinition}）、Excel 工作簿绑定配置（{@link ExcelDefinition}）
 * 以及全局降级策略（{@link PolicyDefinition}）。
 * </p>
 */
public class ReportDefinition {

    /** 配置文件 Schema 版本（如 "1.0"）。 */
    private String schemaVersion;

    /** 报表核心元数据配置。 */
    private ReportMetadata report = new ReportMetadata();

    /** 运行时入参规格映射（key 为参数名）。 */
    private Map<String, ParameterDefinition> parameters =
            new LinkedHashMap<String, ParameterDefinition>();

    /** 数据集（SQL/内存派生）配置列表。 */
    private List<DatasetDefinition> datasets = new ArrayList<DatasetDefinition>();

    /** 图表渲染配置列表。 */
    private List<ChartDefinition> charts = new ArrayList<ChartDefinition>();

    /** 反序列化时 charts 节点是否显式配置了 null。 */
    @JsonIgnore
    private boolean chartsExplicitNull;

    /** 文字段落与叙述分析配置列表。 */
    private List<NarrativeDefinition> narratives = new ArrayList<NarrativeDefinition>();

    /** 反序列化时 narratives 节点是否显式配置了 null。 */
    @JsonIgnore
    private boolean narrativesExplicitNull;

    /** 异常规则与条件计算配置列表。 */
    private List<RuleDefinition> rules = new ArrayList<RuleDefinition>();

    /** 反序列化时 rules 节点是否显式配置了 null。 */
    @JsonIgnore
    private boolean rulesExplicitNull;

    /** Word 模板渲染与章节绑定配置。 */
    private WordDefinition word = new WordDefinition();

    /** Excel 工作表与单元格/表格绑定配置。 */
    private ExcelDefinition excel = new ExcelDefinition();

    /** 反序列化时 excel 节点是否显式配置了 null。 */
    @JsonIgnore
    private boolean excelExplicitNull;

    /** 全局策略与异常降级配置。 */
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

    public ExcelDefinition getExcel() {
        return excel;
    }

    public void setExcel(ExcelDefinition excel) {
        this.excelExplicitNull = excel == null;
        this.excel = excel == null ? new ExcelDefinition() : excel;
    }

    @JsonIgnore
    public boolean isExcelExplicitNull() {
        return excelExplicitNull;
    }

    public void setPolicies(PolicyDefinition policies) {
        this.policies = policies == null ? new PolicyDefinition() : policies;
    }
}
