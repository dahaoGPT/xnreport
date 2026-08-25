package com.xn.report.rule;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;

/**
 * 规则引擎值引用模型与求值解析器。
 * <p>
 * 支持从多种数据源渠道解析操作数值：
 * <ul>
 *   <li>{@link Source#LITERAL}：常量字面值。</li>
 *   <li>{@link Source#CURRENT_FIELD}：当前正在评估行（{@link DatasetRow}）的字段列值。</li>
 *   <li>{@link Source#DATASET_FIELD}：从外部数据集（SCALAR 或 SINGLE 形态）中提取指定字段值。</li>
 *   <li>{@link Source#RUNTIME_PARAMETER}：从 {@link RuleEvaluationContext} 中提取运行时动态入参。</li>
 * </ul>
 * </p>
 */
public final class ValueReference {

    /**
     * 值引用源类型枚举。
     */
    public enum Source {
        /** 常量字面量。 */
        LITERAL,
        /** 当前评估行字段。 */
        CURRENT_FIELD,
        /** 指定前置数据集字段。 */
        DATASET_FIELD,
        /** 运行时入参。 */
        RUNTIME_PARAMETER
    }

    /** 引用源类型。 */
    private final Source source;

    /** 常量字面值。 */
    private final Object literal;

    /** 目标外部数据集 ID。 */
    private final String dataset;

    /** 目标字段名。 */
    private final String field;

    /** 目标运行时入参名。 */
    private final String parameter;

    private ValueReference(
            Source source,
            Object literal,
            String dataset,
            String field,
            String parameter) {
        this.source = source;
        this.literal = source == Source.LITERAL
                ? RuleValues.freezeValue(literal) : literal;
        this.dataset = dataset;
        this.field = field;
        this.parameter = parameter;
    }

    public static ValueReference literal(Object value) {
        return new ValueReference(Source.LITERAL, value, null, null, null);
    }

    public static ValueReference currentField(String field) {
        return new ValueReference(
                Source.CURRENT_FIELD, null, null, requireText(field, "field"), null);
    }

    public static ValueReference datasetField(String dataset, String field) {
        return new ValueReference(
                Source.DATASET_FIELD,
                null,
                requireText(dataset, "dataset"),
                requireText(field, "field"),
                null);
    }

    public static ValueReference runtimeParameter(String parameter) {
        return new ValueReference(
                Source.RUNTIME_PARAMETER,
                null,
                null,
                null,
                requireText(parameter, "parameter"));
    }

    /**
     * 根据上下文与当前数据行解析获取引用的实际值。
     *
     * @param context 规则执行环境上下文
     * @param row 当前评估的数据行
     * @return 实际解析出的数据值
     * @throws ReportException 如果字段缺失、数据集不存在或类型不匹配
     */
    public Object resolve(RuleEvaluationContext context, DatasetRow row) {
        if (context == null || row == null) {
            throw new IllegalArgumentException("Rule context and current row are required");
        }
        switch (source) {
            case LITERAL:
                return RuleValues.copyValue(literal);
            case CURRENT_FIELD:
                if (!row.containsField(field)) {
                    throw RuleErrors.reference("Missing current field: " + field);
                }
                return row.get(field);
            case RUNTIME_PARAMETER:
                return context.getRuntimeParameter(parameter);
            case DATASET_FIELD:
                return resolveDatasetField(context);
            default:
                throw RuleErrors.reference("Unsupported value source: " + source);
        }
    }

    private Object resolveDatasetField(RuleEvaluationContext context) {
        final DatasetResult result;
        try {
            result = context.getDatasets().get(dataset);
        } catch (IllegalArgumentException exception) {
            throw RuleErrors.reference("Missing referenced dataset: " + dataset);
        }
        DatasetRow sourceRow;
        if (result.type() == DatasetType.SINGLE) {
            sourceRow = result.single();
        } else if (result.type() == DatasetType.SCALAR) {
            if (result.schema().fieldNames().size() != 1
                    || !result.schema().fieldNames().get(0).equalsIgnoreCase(field)) {
                throw RuleErrors.reference(
                        "Scalar dataset field does not exist: " + dataset + "." + field);
            }
            return result.scalar();
        } else {
            throw RuleErrors.reference(
                    "DATASET_FIELD requires SCALAR or SINGLE dataset: " + dataset);
        }
        if (sourceRow == null || !sourceRow.containsField(field)) {
            throw RuleErrors.reference(
                    "Missing referenced dataset field: " + dataset + "." + field);
        }
        return sourceRow.get(field);
    }

    public Source getSource() {
        return source;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
