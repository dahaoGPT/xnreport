package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;

/**
 * 内存数据集派生转换器统一接口。
 * <p>
 * 接收输入的 {@link DatasetResult}，对其数据行与 Schema 实施变换处理后返回新的不可变 DatasetResult。
 * </p>
 */
public interface Transform {

    /**
     * 对输入数据集执行转换处理。
     *
     * @param input 输入数据集结果对象，不可为 null
     * @return 转换后的全新不可变数据集结果对象
     */
    DatasetResult apply(DatasetResult input);
}
