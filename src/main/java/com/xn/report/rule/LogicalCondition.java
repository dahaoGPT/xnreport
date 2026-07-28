package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LogicalCondition implements ConditionNode {

    public enum Operator {
        AND,
        OR
    }

    private final Operator operator;
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

    public static LogicalCondition and(List<ConditionNode> children) {
        return new LogicalCondition(Operator.AND, children);
    }

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
