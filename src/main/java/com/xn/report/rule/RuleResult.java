package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单条业务规则执行产出综合结果模型。
 * <p>
 * 封装规则计算完毕后的全部数据产物：
 * <ul>
 *   <li><b>命中明细行（matchedRows）</b>：满足规则条件的行记录列表（已完成去重、排序与截断）。</li>
 *   <li><b>多维分组字典（groups）</b>：按 groupBy 字段拆分的分组子集与分组度量（{@link RuleGroupResult}）。</li>
 *   <li><b>全局汇总度量（summaryValues）</b>：包含 matchedCount, totalCount, matchedRatio 以及自定义 MAX, MIN, SUM, AVG 聚合指标。</li>
 *   <li><b>生成文本（renderedText）</b>：关联模板动态渲染生成的段落文案（可选）。</li>
 * </ul>
 * </p>
 */
public final class RuleResult {

    /** 规则唯一标识。 */
    private final String ruleId;

    /** 规则命中的明细数据行列表。 */
    private final List<DatasetRow> matchedRows;

    /** 分组计算结果字典。 */
    private final Map<String, RuleGroupResult> groups;

    /** 规则关联生成的动态文本（可选）。 */
    private final String renderedText;

    /** 全局聚合度量统计字典。 */
    private final Map<String, Object> summaryValues;

    public RuleResult(
            String ruleId,
            List<DatasetRow> matchedRows,
            Map<String, RuleGroupResult> groups,
            String renderedText,
            Map<String, Object> summaryValues) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule id must not be blank");
        }
        if (matchedRows == null || groups == null || summaryValues == null) {
            throw new IllegalArgumentException(
                    "Rule rows, groups and summary values are required");
        }
        this.ruleId = ruleId;
        this.matchedRows = Collections.unmodifiableList(
                new ArrayList<DatasetRow>(matchedRows));
        this.groups = Collections.unmodifiableMap(
                new LinkedHashMap<String, RuleGroupResult>(groups));
        this.renderedText = renderedText;
        this.summaryValues = RuleValues.freezeMap(summaryValues);
    }

    public String getRuleId() {
        return ruleId;
    }

    public List<DatasetRow> getMatchedRows() {
        return matchedRows;
    }

    public Map<String, RuleGroupResult> getGroups() {
        return groups;
    }

    public String getRenderedText() {
        return renderedText;
    }

    public Map<String, Object> getSummaryValues() {
        return RuleValues.copyMap(summaryValues);
    }
}
