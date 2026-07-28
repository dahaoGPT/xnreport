package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.dataset.DatasetType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TransformSupport {

    private TransformSupport() {
    }

    static List<DatasetRow> rows(DatasetResult input) {
        requireInput(input);
        if (input.type() == DatasetType.LIST) {
            return input.list();
        }
        if (input.type() == DatasetType.SINGLE) {
            DatasetRow row = input.single();
            return row == null
                    ? Collections.<DatasetRow>emptyList()
                    : Collections.singletonList(row);
        }
        Object value = input.scalar();
        if (value == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                DatasetRow.of(input.schema().fieldNames().get(0), value));
    }

    static DatasetResult rebuild(
            DatasetResult input,
            DatasetSchema schema,
            List<DatasetRow> rows) {
        requireInput(input);
        List<DatasetRow> copy = new ArrayList<DatasetRow>(rows);
        if (input.type() == DatasetType.LIST) {
            return DatasetResult.list(input.id(), schema, copy);
        }
        if (input.type() == DatasetType.SINGLE) {
            return DatasetResult.single(input.id(), schema, copy);
        }
        return DatasetResult.scalar(input.id(), schema, copy);
    }

    private static void requireInput(DatasetResult input) {
        if (input == null) {
            throw new IllegalArgumentException("Input dataset must not be null");
        }
    }
}
