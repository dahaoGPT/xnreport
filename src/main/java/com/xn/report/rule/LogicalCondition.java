package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则引擎复合逻辑运算条件节点（AND / OR）。
 * <p>
 * 组合多个子条件节点（{@link ConditionNode}），支持短路求值。
 * </p>
 */
public final class LogicalCondition implements ConditionNode {

    /**
     * 逻辑操作符枚举。
     */
    public enum Operator {
        /** 逻辑与（全真为真，遇假短路）。 */
        AND,
        /** 逻辑或（任一为真即真，遇真短路）。 */
        OR
    }

    /** 逻辑操作符。 */
    private final Operator operator;

    /** 子条件节点列表。 */
    private final List<ConditionNode> children;

    public LogicalCondition(Operator operator, List<ConditionNode> children) {
        if (operator == null) {
            throw new IllegalArgumentException("Logical operator is required");
        }
        if (children == null || children.isEmpty()) {
            throw new IllegalArgumentException("Logical condition requires non-empty children");
        }
        ArrayList<ConditionNode> copy = new ArrayList<ConditionNode>(children.size());
        for (ConditionNode child : children) {
            if (child == null) {
                throw new IllegalArgumentException(
                        "Logical condition children must not contain null");
            }
            copy.add(child);
        }
        this.operator = operator;
        this.children = Collections.unmodifiableList(copy);
    }

    /**
     * 创建 AND 逻辑复合条件。
     *
     * @param children 子条件列表
     * @return LogicalCondition 实例
     */
    public static LogicalCondition and(List<ConditionNode> children) {
        return new LogicalCondition(Operator.AND, children);
    }

    /**
     * 创建 OR 逻辑复合条件。
     *
     * @param children 子条件列表
     * @return LogicalCondition 实例
     */
    public static LogicalCondition or(List<ConditionNode> children) {
        return new LogicalCondition(Operator.OR, children);
    }

    @Override
    public boolean evaluate(RuleEvaluationContext context, DatasetRow row) {
        if (operator == Operator.AND) {
            for (ConditionNode child : children) {
                if (!child.evaluate(context, row)) {
                    return false;
                }
            }
            return true;
        }
        for (ConditionNode child : children) {
            if (child.evaluate(context, row)) {
                return true;
            }
        }
        return false;
    }

    public Operator getOperator() {
        return operator;
    }

    public List<ConditionNode> getChildren() {
        return children;
    }
}
