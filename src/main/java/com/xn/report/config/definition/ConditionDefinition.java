package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConditionDefinition {

    public enum Operator {
        AND,
        OR,
        EQ,
        NE,
        GT,
        GE,
        LT,
        LE,
        IN,
        NOT_IN,
        BETWEEN,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        IS_NULL,
        IS_NOT_NULL
    }

    private Operator operator;
    private List<ConditionDefinition> children;
    private ValueReferenceDefinition left;
    private ValueReferenceDefinition right;
    private Boolean ignoreCase;
    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        mark("operator");
        this.operator = operator;
    }

    public List<ConditionDefinition> getChildren() {
        return children;
    }

    public void setChildren(List<ConditionDefinition> children) {
        mark("children");
        this.children = children;
    }

    public ValueReferenceDefinition getLeft() {
        return left;
    }

    public void setLeft(ValueReferenceDefinition left) {
        mark("left");
        this.left = left;
    }

    public ValueReferenceDefinition getRight() {
        return right;
    }

    public void setRight(ValueReferenceDefinition right) {
        mark("right");
        this.right = right;
    }

    public Boolean getIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(Boolean ignoreCase) {
        mark("ignoreCase");
        this.ignoreCase = ignoreCase;
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
