package com.xn.report.rule;

import com.xn.report.dataset.DatasetRow;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 规则引擎比较条件叶子节点。
 * <p>
 * 对左操作数（left）与右操作数（right）利用比较操作符（{@link ComparisonOperator}）进行求值计算：
 * <ul>
 *   <li>支持等于（EQ）、不等于（NE）、大小比较（GT, GE, LT, LE）。</li>
 *   <li>支持集合归属（IN, NOT_IN）与区间闭合比对（BETWEEN）。</li>
 *   <li>支持字符串模糊匹配（CONTAINS, STARTS_WITH, ENDS_WITH）以及大小写忽略配置（ignoreCase）。</li>
 *   <li>支持空值断言（IS_NULL, IS_NOT_NULL）。</li>
 * </ul>
 * </p>
 */
public final class ComparisonCondition implements ConditionNode {

    /** 左操作数引用。 */
    private final ValueReference left;

    /** 比较操作符。 */
    private final ComparisonOperator operator;

    /** 右操作数引用（一元操作符时为 null）。 */
    private final ValueReference right;

    /** 字符串比较是否忽略大小写。 */
    private final boolean ignoreCase;

    public ComparisonCondition(
            ValueReference left,
            ComparisonOperator operator,
            ValueReference right) {
        this(left, operator, right, false);
    }

    public ComparisonCondition(
            ValueReference left,
            ComparisonOperator operator,
            ValueReference right,
            boolean ignoreCase) {
        if (left == null || operator == null) {
            throw new IllegalArgumentException("Comparison left and operator are required");
        }
        boolean unary = operator == ComparisonOperator.IS_NULL
                || operator == ComparisonOperator.IS_NOT_NULL;
        if (unary && right != null) {
            throw new IllegalArgumentException(operator + " does not allow right value");
        }
        if (!unary && right == null) {
            throw new IllegalArgumentException(operator + " requires right value");
        }
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.ignoreCase = ignoreCase;
    }

    @Override
    public boolean evaluate(RuleEvaluationContext context, DatasetRow row) {
        Object leftValue = left.resolve(context, row);
        if (operator == ComparisonOperator.IS_NULL) {
            return leftValue == null;
        }
        if (operator == ComparisonOperator.IS_NOT_NULL) {
            return leftValue != null;
        }
        Object rightValue = right.resolve(context, row);
        if (leftValue == null || rightValue == null) {
            return false;
        }
        switch (operator) {
            case EQ:
                return equalValues(leftValue, rightValue);
            case NE:
                return !equalValues(leftValue, rightValue);
            case GT:
                return compare(leftValue, rightValue) > 0;
            case GE:
                return compare(leftValue, rightValue) >= 0;
            case LT:
                return compare(leftValue, rightValue) < 0;
            case LE:
                return compare(leftValue, rightValue) <= 0;
            case IN:
                return contains(asList(rightValue, "IN"), leftValue);
            case NOT_IN:
                return !contains(asList(rightValue, "NOT_IN"), leftValue);
            case BETWEEN:
                List<Object> bounds = asList(rightValue, "BETWEEN");
                if (bounds.size() != 2 || bounds.get(0) == null || bounds.get(1) == null) {
                    throw RuleErrors.reference(
                            "BETWEEN requires exactly two non-null boundary values");
                }
                return compare(leftValue, bounds.get(0)) >= 0
                        && compare(leftValue, bounds.get(1)) <= 0;
            case CONTAINS:
                return string(leftValue, "CONTAINS")
                        .contains(string(rightValue, "CONTAINS"));
            case STARTS_WITH:
                return string(leftValue, "STARTS_WITH")
                        .startsWith(string(rightValue, "STARTS_WITH"));
            case ENDS_WITH:
                return string(leftValue, "ENDS_WITH")
                        .endsWith(string(rightValue, "ENDS_WITH"));
            default:
                throw RuleErrors.reference("Unsupported comparison operator: " + operator);
        }
    }

    private boolean contains(List<Object> candidates, Object actual) {
        for (Object candidate : candidates) {
            if (candidate != null && equalValues(actual, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean equalValues(Object leftValue, Object rightValue) {
        if (leftValue instanceof Number && rightValue instanceof Number) {
            return number(leftValue).compareTo(number(rightValue)) == 0;
        }
        if (leftValue instanceof String && rightValue instanceof String) {
            return ignoreCase
                    ? ((String) leftValue).equalsIgnoreCase((String) rightValue)
                    : leftValue.equals(rightValue);
        }
        if (isTemporal(leftValue) || isTemporal(rightValue)) {
            ensureSameComparableType(leftValue, rightValue);
        }
        if (!leftValue.getClass().equals(rightValue.getClass())) {
            throw RuleErrors.reference("Cannot compare values of types "
                    + leftValue.getClass().getName() + " and "
                    + rightValue.getClass().getName());
        }
        return leftValue.equals(rightValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compare(Object leftValue, Object rightValue) {
        if (leftValue instanceof Number && rightValue instanceof Number) {
            return number(leftValue).compareTo(number(rightValue));
        }
        if (leftValue instanceof String && rightValue instanceof String) {
            String leftText = normalize((String) leftValue);
            String rightText = normalize((String) rightValue);
            return leftText.compareTo(rightText);
        }
        ensureSameComparableType(leftValue, rightValue);
        if (!(leftValue instanceof Comparable)) {
            throw RuleErrors.reference(
                    "Values are not comparable: " + leftValue.getClass().getName());
        }
        return ((Comparable) leftValue).compareTo(rightValue);
    }

    private void ensureSameComparableType(Object leftValue, Object rightValue) {
        if (!leftValue.getClass().equals(rightValue.getClass())) {
            throw RuleErrors.reference("Cannot order values of types "
                    + leftValue.getClass().getName() + " and "
                    + rightValue.getClass().getName());
        }
        boolean supported = leftValue instanceof Date
                || leftValue instanceof Temporal;
        if (!supported) {
            throw RuleErrors.reference(
                    "Unsupported ordered value type: " + leftValue.getClass().getName());
        }
    }

    private String string(Object value, String operation) {
        if (!(value instanceof String)) {
            throw RuleErrors.reference(operation + " requires string values");
        }
        return normalize((String) value);
    }

    private String normalize(String value) {
        return ignoreCase ? value.toLowerCase(java.util.Locale.ROOT) : value;
    }

    private static BigDecimal number(Object value) {
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw RuleErrors.reference("Invalid numeric value: " + value);
        }
    }

    private static boolean isTemporal(Object value) {
        return value instanceof Date || value instanceof Temporal;
    }

    private static List<Object> asList(Object value, String operation) {
        ArrayList<Object> result = new ArrayList<Object>();
        if (value instanceof Collection<?>) {
            result.addAll((Collection<?>) value);
            return result;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            for (int index = 0; index < size; index++) {
                result.add(Array.get(value, index));
            }
            return result;
        }
        throw RuleErrors.reference(operation + " requires a collection or array");
    }
}
