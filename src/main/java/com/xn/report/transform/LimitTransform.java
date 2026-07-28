package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.List;

public final class LimitTransform implements Transform {

    private final int limit;

    public LimitTransform(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit must be non-negative");
        }
        this.limit = limit;
    }

    @Override
    public DatasetResult apply(DatasetResult input) {
        List<DatasetRow> source = TransformSupport.rows(input);
        int end = Math.min(limit, source.size());
        return TransformSupport.rebuild(
                input,
                input.schema(),
                new ArrayList<DatasetRow>(source.subList(0, end)));
    }
}
