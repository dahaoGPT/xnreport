package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import java.util.List;

/**
 * 内存数据转换流水线驱动执行引擎。
 * <p>
 * 按照声明顺序依次应用执行一组 {@link Transform} 转换算子，链式产出最终的数据集计算结果。
 * </p>
 */
public final class TransformEngine {

    /**
     * 链式执行一组内存转换算子。
     *
     * @param source 输入源数据集结果，不可为 null
     * @param transforms 转换算子列表，不可为 null
     * @return 最终转换结果 DatasetResult
     */
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
