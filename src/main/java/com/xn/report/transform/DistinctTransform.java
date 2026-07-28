package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DistinctTransform implements Transform {

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
        Set<List<Object>> encountered = new LinkedHashSet<List<Object>>();
        List<DatasetRow> distinct = new ArrayList<DatasetRow>();
        for (DatasetRow row : TransformSupport.rows(input)) {
            List<Object> key = new ArrayList<Object>(fields.size());
            for (String field : fields) {
                key.add(row.get(field));
            }
            if (encountered.add(key)) {
                distinct.add(row);
            }
        }
        return TransformSupport.rebuild(input, input.schema(), distinct);
    }
}
