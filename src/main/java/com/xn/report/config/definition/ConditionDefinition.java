package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则引擎条件节点配置定义模型。
 * <p>
 * 支持构建嵌套布尔抽象语法树（AST），既可以作为复合逻辑条件节点（AND, OR），
 * 也可以作为叶子节点比较条件（EQ, NE, GT, GE, LT, LE, IN, NOT_IN, BETWEEN, CONTAINS 等）。
 * </p>
 */
public class ConditionDefinition {

    /**
     * 条件比较与逻辑操作符枚举。
     */
    public enum Operator {
        /** 逻辑与（所有子条件均为真）。 */
        AND,
        /** 逻辑或（任一子条件为真）。 */
        OR,
        /** 等于。 */
        EQ,
        /** 不等于。 */
        NE,
        /** 大于。 */
        GT,
        /** 大于等于。 */
        GE,
        /** 小于。 */
        LT,
        /** 小于等于。 */
        LE,
        /** 包含于指定集合。 */
        IN,
        /** 不包含于指定集合。 */
        NOT_IN,
        /** 处于指定闭区间 [min, max]。 */
        BETWEEN,
        /** 字符串包含子串。 */
        CONTAINS,
        /** 字符串以前缀开始。 */
        STARTS_WITH,
        /** 字符串以后缀结束。 */
        ENDS_WITH,
        /** 值为 NULL。 */
        IS_NULL,
        /** 值不为 NULL。 */
        IS_NOT_NULL
    }

    /** 当前条件的操作符。 */
    private Operator operator;

    /** 当 operator 为 AND/OR 时的子条件列表。 */
    private List<ConditionDefinition> children;

    /** 比较操作的左操作数引用。 */
    private ValueReferenceDefinition left;

    /** 比较操作的右操作数引用。 */
    private ValueReferenceDefinition right;

    /** 字符串比较时是否忽略大小写。 */
    private Boolean ignoreCase;

    /** 显式出现的配置属性记录集合。 */
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
