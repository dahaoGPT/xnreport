package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;

/**
 * 规则条件抽象语法树（AST）节点求值接口。
 * <p>
 * 在给定的上下文与当前数据行上执行布尔逻辑判定。
 * </p>
 */
@FunctionalInterface
public interface ConditionNode {

    /**
     * 评估当前节点在指定数据行上的条件结果。
     *
     * @param context 规则执行上下文
     * @param row 当前正在评估的数据行
     * @return true 表示条件命中，false 表示未命中
     */
    boolean evaluate(RuleEvaluationContext context, DatasetRow row);
}
