package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存数据集行级条件过滤转换器。
 * <p>
 * 根据指定的字段（field）、比较操作符（{@link Operator}）与目标期望值（expected），
 * 逐行评估数据记录并筛选出满足条件的行子集。
 * </p>
 */
public final class FilterTransform implements Transform {

    /**
     * 过滤比较操作符枚举。
     */
    public enum Operator {
        /** 等于。 */
        EQUAL,
        /** 等于（简写）。 */
        EQ,
        /** 不等于。 */
        NOT_EQUAL,
        /** 不等于（简写）。 */
        NE,
        /** 大于。 */
        GREATER_THAN,
        /** 大于（简写）。 */
        GT,
        /** 大于等于。 */
        GREATER_THAN_OR_EQUAL,
        /** 大于等于（简写）。 */
        GTE,
        /** 小于。 */
        LESS_THAN,
        /** 小于（简写）。 */
        LT,
        /** 小于等于。 */
        LESS_THAN_OR_EQUAL,
        /** 小于等于（简写）。 */
        LTE,
        /** 值为 NULL。 */
        IS_NULL,
        /** 值不为 NULL。 */
        IS_NOT_NULL
    }

    /** 目标比对字段。 */
    private final String field;

    /** 比较操作符。 */
    private final Operator operator;

    /** 预期目标值快照。 */
    private final Object expected;

    public FilterTransform(String field, Operator operator, Object expected) {
        this.field = requireField(field);
        if (operator == null) {
            throw new IllegalArgumentException("Filter operator must not be null");
        }
        this.operator = operator;
        this.expected = TransformValueSnapshot.freeze(expected);
    }

    @Override
    public DatasetResult apply(DatasetResult input) {
        List<DatasetRow> source = TransformSupport.rows(input);
        if (!input.schema().containsField(field)) {
            throw new IllegalArgumentException("Missing filter field: " + field);
        }
        List<DatasetRow> selected = new ArrayList<DatasetRow>();
        for (DatasetRow row : source) {
            if (matches(row)) {
                selected.add(row);
            }
        }
        return TransformSupport.rebuild(input, input.schema(), selected);
    }

    /**
     * 评估单行数据是否命中过滤条件。
     */
    private boolean matches(DatasetRow row) {
        Object actual = row.get(field);
        if (operator == Operator.IS_NULL) {
            return actual == null;
        }
        if (operator == Operator.IS_NOT_NULL) {
            return actual != null;
        }
        if (actual == null || expected == null) {
            return false;
        }
        switch (operator) {
            case EQUAL:
            case EQ:
                return valuesEqual(actual, expected);
            case NOT_EQUAL:
            case NE:
                return !valuesEqual(actual, expected);
            default:
                int comparison = TransformValueComparator.compare(actual, expected);
                switch (operator) {
                    case GREATER_THAN:
                    case GT:
                        return comparison > 0;
                    case GREATER_THAN_OR_EQUAL:
                    case GTE:
                        return comparison >= 0;
                    case LESS_THAN:
                    case LT:
                        return comparison < 0;
                    case LESS_THAN_OR_EQUAL:
                    case LTE:
                        return comparison <= 0;
                    default:
                        throw new IllegalStateException(
                                "Unsupported filter operator: " + operator);
                }
        }
    }

    private static boolean valuesEqual(Object left, Object right) {
        return TransformValueComparator.equal(left, right);
    }

    private static String requireField(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException("Filter field must not be blank");
        }
        return field;
    }
}
