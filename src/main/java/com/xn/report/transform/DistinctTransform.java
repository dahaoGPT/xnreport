package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DistinctTransform implements Transform {

    private static final class DeepKey {

        private final TransformDeepValue[] values;

        private DeepKey(List<Object> values) {
            this.values = new TransformDeepValue[values.size()];
            for (int index = 0; index < values.size(); index++) {
                this.values[index] = TransformDeepValue.of(values.get(index));
            }
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof DeepKey
                    && java.util.Arrays.equals(values, ((DeepKey) object).values);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(values);
        }
    }

    private final List<String> fields;

    public DistinctTransform(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Distinct fields must not be empty");
        }
        ArrayList<String> copy = new ArrayList<String>(fields.size());
        for (String field : fields) {
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Distinct fields must not contain blank values");
            }
            copy.add(field);
        }
        this.fields = Collections.unmodifiableList(copy);
    }

    @Override
    public DatasetResult apply(DatasetResult input) {
        for (String field : fields) {
            if (!input.schema().containsField(field)) {
                throw new IllegalArgumentException("Missing distinct field: " + field);
            }
        }
        Set<DeepKey> encountered = new LinkedHashSet<DeepKey>();
        List<DatasetRow> distinct = new ArrayList<DatasetRow>();
        for (DatasetRow row : TransformSupport.rows(input)) {
            List<Object> key = new ArrayList<Object>(fields.size());
            for (String field : fields) {
                key.add(row.get(field));
            }
            if (encountered.add(new DeepKey(key))) {
                distinct.add(row);
            }
        }
        return TransformSupport.rebuild(input, input.schema(), distinct);
    }
}
