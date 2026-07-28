package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SortTransform implements Transform {

    public static final class SortField {

        private final String field;
        private final Direction direction;
        private final NullOrder nullOrder;

        public SortField(String field, Direction direction, NullOrder nullOrder) {
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException("Sort field must not be blank");
            }
            if (direction == null) {
                throw new IllegalArgumentException("Sort direction must not be null");
            }
            if (nullOrder == null) {
                throw new IllegalArgumentException("Sort null order must not be null");
            }
            this.field = field;
            this.direction = direction;
            this.nullOrder = nullOrder;
        }
    }

    private final List<SortField> fields;

    public SortTransform(String field, Direction direction) {
        this(field, direction, NullOrder.LAST);
    }

    public SortTransform(String field, Direction direction, NullOrder nullOrder) {
        this(Collections.singletonList(new SortField(field, direction, nullOrder)));
    }

    public SortTransform(List<SortField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Sort fields must not be empty");
        }
        ArrayList<SortField> copy = new ArrayList<SortField>(fields.size());
        for (SortField field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("Sort fields must not contain null");
            }
            copy.add(field);
        }
        this.fields = Collections.unmodifiableList(copy);
    }

    @Override
    public DatasetResult apply(DatasetResult input) {
        for (SortField field : fields) {
            if (!input.schema().containsField(field.field)) {
                throw new IllegalArgumentException("Missing sort field: " + field.field);
            }
        }
        ArrayList<DatasetRow> sorted =
                new ArrayList<DatasetRow>(TransformSupport.rows(input));
        Collections.sort(sorted, new Comparator<DatasetRow>() {
            @Override
            public int compare(DatasetRow left, DatasetRow right) {
                for (SortField field : fields) {
                    Object leftValue = left.get(field.field);
                    Object rightValue = right.get(field.field);
                    if (leftValue == null || rightValue == null) {
                        int nullComparison =
                                compareNulls(leftValue, rightValue, field.nullOrder);
                        if (nullComparison != 0) {
                            return nullComparison;
                        }
                        continue;
                    }
                    int result = compareValues(
                            leftValue,
                            rightValue);
                    if (result != 0) {
                        return field.direction == Direction.ASC ? result : -result;
                    }
                }
                return 0;
            }
        });
        return TransformSupport.rebuild(input, input.schema(), sorted);
    }

    private static int compareNulls(Object left, Object right, NullOrder nullOrder) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return nullOrder == NullOrder.FIRST ? -1 : 1;
        }
        return nullOrder == NullOrder.FIRST ? 1 : -1;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Object left, Object right) {
        return TransformValueComparator.compare(left, right);
    }
}
