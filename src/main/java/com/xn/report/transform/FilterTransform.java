package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.List;

public final class FilterTransform implements Transform {

    public enum Operator {
        EQUAL,
        EQ,
        NOT_EQUAL,
        NE,
        GREATER_THAN,
        GT,
        GREATER_THAN_OR_EQUAL,
        GTE,
        LESS_THAN,
        LT,
        LESS_THAN_OR_EQUAL,
        LTE,
        IS_NULL,
        IS_NOT_NULL
    }

    private final String field;
    private final Operator operator;
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
