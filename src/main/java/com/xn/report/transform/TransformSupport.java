package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.dataset.DatasetType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存转换内部辅助工具类。
 * <p>
 * 提供从不同形态（LIST, SINGLE, SCALAR）的 {@link DatasetResult} 中统一解包行列表，
 * 以及转换完成后根据原形态与新 Schema/行列表重新构建 DatasetResult 的工具方法。
 * </p>
 */
final class TransformSupport {

    private TransformSupport() {
    }

    /**
     * 从任意形态的数据集中安全提取行列表。
     *
     * @param input 数据集结果对象
     * @return 数据行列表
     */
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

    /**
     * 根据输入形态、新 Schema 与新行列表重新组装构建 DatasetResult。
     *
     * @param input 原输入结果（用于继承 ID 与形态）
     * @param schema 新 Schema 契约
     * @param rows 新数据行列表
     * @return 重构后的 DatasetResult
     */
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
