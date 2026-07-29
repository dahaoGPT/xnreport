package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RuleDefinition {

    private String id;
    private String dataset;
    private ConditionDefinition condition;
    private ResultDefinition result = new ResultDefinition();
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

    public static class ResultDefinition {

        private List<String> distinctFields = new ArrayList<String>();
        private List<SortFieldDefinition> sort = new ArrayList<SortFieldDefinition>();
        private List<String> groupByFields = new ArrayList<String>();
        private Integer maxItems;
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

    public static class SummaryDefinition {

        public enum Operation {
            MAX,
            MIN,
            SUM,
            AVG
        }

        private String name;
        private String field;
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
