package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;

@FunctionalInterface
public interface ConditionNode {

    boolean evaluate(RuleEvaluationContext context, DatasetRow row);
}
