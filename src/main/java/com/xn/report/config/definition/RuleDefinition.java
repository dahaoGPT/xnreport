package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 业务异常规则配置定义模型。
 * <p>
 * 声明针对指定数据集的行级或聚合级业务规则过滤与计算逻辑：
 * <ul>
 *   <li><b>数据源与条件树</b>：绑定数据集（dataset）、条件表达式树（{@link ConditionDefinition}）。</li>
 *   <li><b>结果后处理（{@link ResultDefinition}）</b>：去重字段（distinctFields）、排序规则（sort）、分组维度（groupByFields）、最大保留条数（maxItems）与聚合统计汇总（summaries）。</li>
 * </ul>
 * </p>
 */
public class RuleDefinition {

    /** 规则唯一标识。 */
    private String id;

    /** 规则作用的目标数据集 ID。 */
    private String dataset;

    /** 规则匹配条件表达式根节点。 */
    private ConditionDefinition condition;

    /** 命中规则后的结果提取与聚合配置。 */
    private ResultDefinition result = new ResultDefinition();

    /** 规则级别异常降级策略。 */
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

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        mark("dataset");
        this.dataset = dataset;
    }

    public ConditionDefinition getCondition() {
        return condition;
    }

    public void setCondition(ConditionDefinition condition) {
        mark("condition");
        this.condition = condition;
    }

    public ResultDefinition getResult() {
        return result;
    }

    public void setResult(ResultDefinition result) {
        mark("result");
        this.result = result;
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

    /**
     * 规则命中结果后处理配置模型。
     */
    public static class ResultDefinition {

        /** 结果去重字段列表。 */
        private List<String> distinctFields = new ArrayList<String>();

        /** 结果排序规则列表。 */
        private List<SortFieldDefinition> sort = new ArrayList<SortFieldDefinition>();

        /** 分组聚合维度字段列表。 */
        private List<String> groupByFields = new ArrayList<String>();

        /** 限制提取的最大条数。 */
        private Integer maxItems;

        /** 聚合度量统计配置列表（MAX, MIN, SUM, AVG）。 */
        private List<SummaryDefinition> summaries = new ArrayList<SummaryDefinition>();

        @JsonIgnore
        private final Set<String> presentProperties = new LinkedHashSet<String>();

        public List<String> getDistinctFields() {
            return distinctFields;
        }

        public void setDistinctFields(List<String> distinctFields) {
            mark("distinctFields");
            this.distinctFields = distinctFields;
        }

        public List<SortFieldDefinition> getSort() {
            return sort;
        }

        public void setSort(List<SortFieldDefinition> sort) {
            mark("sort");
            this.sort = sort;
        }

        public List<String> getGroupByFields() {
            return groupByFields;
        }

        public void setGroupByFields(List<String> groupByFields) {
            mark("groupByFields");
            this.groupByFields = groupByFields;
        }

        public Integer getMaxItems() {
            return maxItems;
        }

        public void setMaxItems(Integer maxItems) {
            mark("maxItems");
            this.maxItems = maxItems;
        }

        public List<SummaryDefinition> getSummaries() {
            return summaries;
        }

        public void setSummaries(List<SummaryDefinition> summaries) {
            mark("summaries");
            this.summaries = summaries;
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

    /**
     * 聚合统计度量定义模型。
     */
    public static class SummaryDefinition {

        /**
         * 聚合操作类型枚举。
         */
        public enum Operation {
            /** 最大值。 */
            MAX,
            /** 最小值。 */
            MIN,
            /** 求和。 */
            SUM,
            /** 平均值。 */
            AVG
        }

        /** 汇总度量输出别名。 */
        private String name;

        /** 待计算的目标字段名。 */
        private String field;

        /** 聚合计算操作符。 */
        private Operation operation;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public Operation getOperation() {
            return operation;
        }

        public void setOperation(Operation operation) {
            this.operation = operation;
        }
    }
}
