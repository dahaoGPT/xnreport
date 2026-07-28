package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuleResult {

    private final String ruleId;
    private final List<DatasetRow> matchedRows;
    private final Map<String, RuleGroupResult> groups;
    private final String renderedText;
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
