package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import java.util.List;

public final class TransformEngine {

    public DatasetResult apply(DatasetResult source, List<Transform> transforms) {
        if (source == null) {
            throw new IllegalArgumentException("Source dataset must not be null");
        }
        if (transforms == null) {
            throw new IllegalArgumentException("Transforms must not be null");
        }
        DatasetResult current = source;
        for (Transform transform : transforms) {
            if (transform == null) {
                throw new IllegalArgumentException("Transforms must not contain null");
            }
            current = transform.apply(current);
        }
        return current;
    }
}
