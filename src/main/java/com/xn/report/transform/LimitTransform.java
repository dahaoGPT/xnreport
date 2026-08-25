package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存数据集行数截断转换器。
 * <p>
 * 限制数据集的最大保留行数（保留前 N 行数据）。
 * </p>
 */
public final class LimitTransform implements Transform {

    /** 截断最大保留行数。 */
    private final int limit;

    /**
     * 构造截断转换器。
     *
     * @param limit 最大行数（非负整数）
     */
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
