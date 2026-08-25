package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文本段落与叙述分析配置定义模型。
 * <p>
 * 声明 Word 报表正文中的动态段落生成逻辑：
 * <ul>
 *   <li><b>生成源类型（{@link SourceType}）</b>：FIXED_TEMPLATE（固定模板占位符渲染）或 RULE_GENERATED（规则计算生成）。</li>
 *   <li><b>分析器类型（{@link AnalyzerType}）</b>：TREND（环比/同比趋势分析）或 DISTRIBUTION（分布分箱分析）。</li>
 *   <li><b>模板与语句</b>：段落模板（template）、单句模式（sentence）及格式化样式（format）。</li>
 *   <li><b>空数据降级</b>：空数据处理策略（{@link EmptyStrategy}）与私有策略（policies）。</li>
 * </ul>
 * </p>
 */
public class NarrativeDefinition {

    /**
     * 段落来源类型枚举。
     */
    public enum SourceType {
        /** 基于固定模板结合占位符渲染。 */
        FIXED_TEMPLATE,
        /** 基于规则命中结果动态生成。 */
        RULE_GENERATED
    }

    /**
     * 空数据段落生成策略枚举。
     */
    public enum EmptyStrategy {
        /** 抛出异常失败。 */
        FAIL,
        /** 输出提示信息（如“暂无统计数据”）。 */
        OUTPUT_MESSAGE,
        /** 跳过该段落渲染。 */
        SKIP
    }

    /**
     * 自动分析器类型枚举。
     */
    public enum AnalyzerType {
        /** 趋势分析器（计算环比、增减幅度与趋势描述）。 */
        TREND,
        /** 分布分析器（计算分箱区间频数与占比）。 */
        DISTRIBUTION
    }

    /** 段落定义唯一标识。 */
    private String id;

    /** 生成来源类型。 */
    private SourceType sourceType;

    /** 模板文本内容（支持 ${var:format} 占位符）。 */
    private String template;

    /** 自定义分析器名称。 */
    private String analyzer;

    /** 内置分析器类型。 */
    private AnalyzerType analyzerType;

    /** 主数据集 ID。 */
    private String dataset;

    /** 基准/对比数据集 ID（用于趋势分析）。 */
    private String baseline;

    /** 默认格式化 pattern。 */
    private String format;

    /** 单句精简模板。 */
    private String sentence;

    /** 空数据处理策略（默认 OUTPUT_MESSAGE）。 */
    private EmptyStrategy emptyStrategy = EmptyStrategy.OUTPUT_MESSAGE;

    /** 传递给分析器的动态扩展参数映射。 */
    private Map<String, Object> parameters = new LinkedHashMap<String, Object>();

    /** 分布分析具体配置。 */
    private DistributionDefinition distribution = new DistributionDefinition();

    /** 趋势分析具体配置。 */
    private TrendDefinition trend;

    /** 段落级别异常降级策略。 */
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
