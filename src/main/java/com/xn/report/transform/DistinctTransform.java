package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 内存数据集多字段联合去重转换器。
 * <p>
 * 根据配置的一组或多组去重字段（fields），利用 {@link TransformDeepValue} 对字段值进行跨类型深度比对，
 * 剔除重复数据行并严格保留首个出现的行记录。
 * </p>
 */
public final class DistinctTransform implements Transform {

    /**
     * 深度复合键值封装类。
     */
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

    /** 参与去重判定的字段名称列表。 */
    private final List<String> fields;

    /**
     * 构造去重转换器。
     *
     * @param fields 去重字段列表，不可为空
     */
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
