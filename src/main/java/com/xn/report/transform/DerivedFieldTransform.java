package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.dataset.DatasetType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DerivedFieldTransform implements Transform {

    private final String targetField;
    private final String sourceField;
    private final ArithmeticOperator operator;
    private final BigDecimal operand;
    private final int scale;
    private final DivideByZeroStrategy divideByZeroStrategy;
    private final BigDecimal divideByZeroDefault;
    private final FieldConflictStrategy conflictStrategy;

    public DerivedFieldTransform(
            String targetField,
            String sourceField,
            ArithmeticOperator operator,
            BigDecimal operand,
            int scale) {
        this(
                targetField,
                sourceField,
                operator,
                operand,
                scale,
                DivideByZeroStrategy.FAIL,
                null,
                FieldConflictStrategy.FAIL);
    }

    public DerivedFieldTransform(
            String targetField,
            String sourceField,
            ArithmeticOperator operator,
            BigDecimal operand,
            int scale,
            DivideByZeroStrategy divideByZeroStrategy,
            BigDecimal divideByZeroDefault,
            FieldConflictStrategy conflictStrategy) {
        this.targetField = requireField(targetField, "Target");
        this.sourceField = requireField(sourceField, "Source");
        if (operator == null) {
            throw new IllegalArgumentException("Arithmetic operator must not be null");
        }
        if (operand == null) {
            throw new IllegalArgumentException("Arithmetic operand must not be null");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("Derived field scale must be non-negative");
        }
        if (divideByZeroStrategy == null) {
            throw new IllegalArgumentException("Divide-by-zero strategy must not be null");
        }
        if (divideByZeroStrategy == DivideByZeroStrategy.DEFAULT_VALUE
                && divideByZeroDefault == null) {
            throw new IllegalArgumentException(
                    "Divide-by-zero default value must not be null");
        }
        if (conflictStrategy == null) {
            throw new IllegalArgumentException("Field conflict strategy must not be null");
        }
        this.operator = operator;
        this.operand = operand;
        this.scale = scale;
        this.divideByZeroStrategy = divideByZeroStrategy;
        this.divideByZeroDefault = divideByZeroDefault;
        this.conflictStrategy = conflictStrategy;
    }

    @Override
    public DatasetResult apply(DatasetResult input) {
        if (input == null) {
            throw new IllegalArgumentException("Input dataset must not be null");
        }
        if (input.type() != DatasetType.LIST
                && input.type() != DatasetType.SINGLE) {
            throw new IllegalArgumentException(
                    "Derived fields require a LIST or SINGLE dataset");
        }
        if (!input.schema().containsField(sourceField)) {
            throw new IllegalArgumentException(
                    "Missing derived source field: " + sourceField);
        }
        boolean conflict = input.schema().containsField(targetField);
        if (conflict && conflictStrategy == FieldConflictStrategy.FAIL) {
            throw new IllegalArgumentException(
                    "Derived target field already exists: " + targetField);
        }

        List<DatasetRow> rows = new ArrayList<DatasetRow>();
        for (DatasetRow row : TransformSupport.rows(input)) {
            LinkedHashMap<String, Object> values =
                    new LinkedHashMap<String, Object>(row.asMap());
            String actualTarget = conflict ? existingField(values, targetField) : targetField;
            values.put(actualTarget, calculate(row.get(sourceField)));
            rows.add(DatasetRow.of(toPairs(values)));
        }

        LinkedHashMap<String, Class<?>> schemaFields =
                new LinkedHashMap<String, Class<?>>(input.schema().asMap());
        String actualTarget = conflict
                ? existingField(schemaFields, targetField) : targetField;
        schemaFields.put(actualTarget, BigDecimal.class);
        DatasetSchema schema = DatasetSchema.of(toPairs(schemaFields));
        return TransformSupport.rebuild(input, schema, rows);
    }

    private Object calculate(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(
                    "Derived source field must be numeric: " + sourceField);
        }
        BigDecimal left = value instanceof BigDecimal
                ? (BigDecimal) value : new BigDecimal(value.toString());
        BigDecimal result;
        switch (operator) {
            case ADD:
                result = left.add(operand);
                break;
            case SUBTRACT:
                result = left.subtract(operand);
                break;
            case MULTIPLY:
                result = left.multiply(operand);
                break;
            case DIVIDE:
                if (operand.compareTo(BigDecimal.ZERO) == 0) {
                    return divideByZero();
                }
                result = left.divide(operand, scale, RoundingMode.HALF_UP);
                break;
            default:
                throw new IllegalStateException(
                        "Unsupported arithmetic operator: " + operator);
        }
        return result.setScale(scale, RoundingMode.HALF_UP);
    }

    private Object divideByZero() {
        if (divideByZeroStrategy == DivideByZeroStrategy.NULL) {
            return null;
        }
        if (divideByZeroStrategy == DivideByZeroStrategy.DEFAULT_VALUE) {
            return divideByZeroDefault.setScale(scale, RoundingMode.HALF_UP);
        }
        throw new ArithmeticException("Division by zero for field: " + sourceField);
    }

    private static String existingField(Map<String, ?> values, String requested) {
        for (String field : values.keySet()) {
            if (field.equalsIgnoreCase(requested)) {
                return field;
            }
        }
        throw new IllegalStateException("Expected existing field: " + requested);
    }

    private static Object[] toPairs(Map<String, ?> values) {
        Object[] pairs = new Object[values.size() * 2];
        int index = 0;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            pairs[index++] = entry.getKey();
            pairs[index++] = entry.getValue();
        }
        return pairs;
    }

    private static String requireField(String field, String label) {
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    label + " derived field must not be blank");
        }
        return field;
    }
}
