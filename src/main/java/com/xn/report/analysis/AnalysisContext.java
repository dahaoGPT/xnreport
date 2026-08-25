package com.xn.report.analysis;

import com.xn.report.chart.ChartModel;
import com.xn.report.chart.RenderedChart;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.policy.ReportWarning;
import com.xn.report.rule.RuleResult;
import com.xn.report.text.NarrativeResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报表分析与计算阶段成果上下文不可变模型。
 * <p>
 * 聚合分析流水线（{@link AnalysisService}）产生的所有计算产物：
 * <ul>
 *   <li><b>查询快照</b>（querySnapshot）：SQL 刚执行完的原始数据集上下文。</li>
 *   <li><b>转换后数据集</b>（datasetContext）：经过 DerivedField、Filter、Sort、Limit 等清洗转换后的数据集上下文。</li>
 *   <li><b>规则求值结果</b>（ruleResults）：各级业务规则组的命中判定与严重级别。</li>
 *   <li><b>叙述文本分析结果</b>（narratives）：通过模板与分析器生成的最终陈述文本。</li>
 *   <li><b>图表逻辑模型</b>（chartModels）：已完成数据对齐与统计聚合的图表模型。</li>
 *   <li><b>已渲染图表图像</b>（renderedCharts）：离线渲染的高清 PNG 文件路径与规格。</li>
 *   <li><b>警告列表</b>（warnings）：空数据跳过、字段缺失等非致命告警。</li>
 * </ul>
 * </p>
 */
public final class AnalysisContext {

    private final DatasetContext querySnapshot;
    private final DatasetContext datasetContext;
    private final Map<String, RuleResult> ruleResults;
    private final Map<String, NarrativeResult> narratives;
    private final Map<String, ChartModel> chartModels;
    private final Map<String, RenderedChart> renderedCharts;
    private final List<ReportWarning> warnings;

    public AnalysisContext(
            DatasetContext querySnapshot,
            DatasetContext datasetContext,
            Map<String, RuleResult> ruleResults,
            Map<String, NarrativeResult> narratives,
            Map<String, ChartModel> chartModels,
            Map<String, RenderedChart> renderedCharts,
            List<ReportWarning> warnings) {
        this.querySnapshot =
                Objects.requireNonNull(querySnapshot, "querySnapshot");
        this.datasetContext =
                Objects.requireNonNull(datasetContext, "datasetContext");
        this.ruleResults = immutableMap(ruleResults);
        this.narratives = immutableMap(narratives);
        this.chartModels = immutableMap(chartModels);
        this.renderedCharts = immutableMap(renderedCharts);
        this.warnings = Collections.unmodifiableList(
                new ArrayList<ReportWarning>(
                        warnings == null
                                ? Collections.<ReportWarning>emptyList()
                                : warnings));
    }

    /**
     * 构建仅含原始数据集的空白分析上下文。
     *
     * @param datasets 原始数据集上下文
     * @return 空白 AnalysisContext
     */
    public static AnalysisContext empty(DatasetContext datasets) {
        return new AnalysisContext(
                datasets, datasets,
                Collections.<String, RuleResult>emptyMap(),
                Collections.<String, NarrativeResult>emptyMap(),
                Collections.<String, ChartModel>emptyMap(),
                Collections.<String, RenderedChart>emptyMap(),
                Collections.<ReportWarning>emptyList());
    }

    /**
     * 追加警告并返回新的分析上下文副本。
     *
     * @param action 告警动作
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     * @param message 警告信息
     * @return 新的 AnalysisContext 实例
     */
    public AnalysisContext withWarning(
            String action,
            String scopeType,
            String scopeId,
            String message) {
        List<ReportWarning> copy = new ArrayList<ReportWarning>(warnings);
        copy.add(new ReportWarning(action, scopeType, scopeId, message));
        return new AnalysisContext(
                querySnapshot, datasetContext, ruleResults, narratives,
                chartModels, renderedCharts, copy);
    }

    public DatasetContext getQuerySnapshot() {
        return querySnapshot;
    }

    public DatasetContext getDatasetContext() {
        return datasetContext;
    }

    public Map<String, RuleResult> getRuleResults() {
        return ruleResults;
    }

    public Map<String, NarrativeResult> getNarratives() {
        return narratives;
    }

    public Map<String, ChartModel> getChartModels() {
        return chartModels;
    }

    public Map<String, RenderedChart> getRenderedCharts() {
        return renderedCharts;
    }

    public List<ReportWarning> getWarnings() {
        return warnings;
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> values) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, T>(
                        values == null
                                ? Collections.<String, T>emptyMap()
                                : values));
    }
}
