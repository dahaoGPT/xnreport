package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuleGroupResult {

    private final String key;
    private final List<DatasetRow> matchedRows;
    private final Map<String, Object> summaryValues;

    public RuleGroupResult(
            String key,
            List<DatasetRow> matchedRows,
            Map<String, Object> summaryValues) {
        if (key == null || matchedRows == null || summaryValues == null) {
            throw new IllegalArgumentException(
                    "Group key, matched rows and summary values are required");
        }
        this.key = key;
        this.matchedRows = Collections.unmodifiableList(
                new ArrayList<DatasetRow>(matchedRows));
        this.summaryValues = RuleValues.freezeMap(summaryValues);
    }

    public String getKey() {
        return key;
    }

    public List<DatasetRow> getMatchedRows() {
        return matchedRows;
    }

    public Map<String, Object> getSummaryValues() {
        return summaryValues;
    }
}
