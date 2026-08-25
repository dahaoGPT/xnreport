package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 规则分组聚合统计结果值对象。
 * <p>
 * 封装按一组 groupByFields 划分出的单个分组维度的业务数据：
 * 包含分组业务键（key）、归属该分组的匹配数据行（matchedRows）以及分组内部的聚合汇总度量（summaryValues）。
 * </p>
 */
public final class RuleGroupResult {

    /** 分组业务标识键。 */
    private final String key;

    /** 归属该分组的匹配数据行列表。 */
    private final List<DatasetRow> matchedRows;

    /** 该分组内部的聚合度量字典。 */
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
        return RuleValues.copyMap(summaryValues);
    }
}
