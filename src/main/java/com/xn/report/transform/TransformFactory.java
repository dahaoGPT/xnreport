package com.xn.report.transform;

import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformOperator;
import java.util.ArrayList;
import java.util.List;

public final class TransformFactory {

    public List<Transform> createAll(List<TransformDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException(
                    "Transform definitions must not be null");
        }
        List<Transform> transforms = new ArrayList<Transform>(definitions.size());
        for (TransformDefinition definition : definitions) {
            transforms.add(create(definition));
        }
        return transforms;
    }

    public Transform create(TransformDefinition definition) {
        if (definition == null || definition.getType() == null) {
            throw new IllegalArgumentException(
                    "Transform definition type must not be null");
        }
        switch (definition.getType()) {
            case FILTER:
                return new FilterTransform(
                        definition.getField(),
                        filterOperator(definition.getOperator()),
                        definition.getValue());
            case SORT:
                return new SortTransform(sortFields(definition.getSortFields()));
            case DISTINCT:
                return new DistinctTransform(definition.getFields());
            case LIMIT:
                if (definition.getLimit() == null) {
                    throw new IllegalArgumentException("Transform limit is required");
                }
                return new LimitTransform(definition.getLimit().intValue());
            case DERIVED_FIELD:
                return new DerivedFieldTransform(
                        definition.getTargetField(),
                        definition.getSourceField(),
                        arithmeticOperator(definition.getOperator()),
                        definition.getOperand(),
                        definition.getScale() == null
                                ? 2 : definition.getScale().intValue(),
                        definition.getDivideByZeroStrategy() == null
                                ? DivideByZeroStrategy.FAIL
                                : definition.getDivideByZeroStrategy(),
                        definition.getDivideByZeroDefault(),
                        definition.getFieldConflictStrategy() == null
                                ? FieldConflictStrategy.FAIL
                                : definition.getFieldConflictStrategy());
            default:
                throw new IllegalArgumentException(
                        "Unsupported transform type: " + definition.getType());
        }
    }

    private static List<SortTransform.SortField> sortFields(
            List<SortFieldDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("Sort fields must not be null");
        }
        List<SortTransform.SortField> fields =
                new ArrayList<SortTransform.SortField>(definitions.size());
        for (SortFieldDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException(
                        "Sort fields must not contain null");
            }
            fields.add(new SortTransform.SortField(
                    definition.getField(),
                    definition.getDirection(),
                    definition.getNullOrder()));
        }
        return fields;
    }

    private static FilterTransform.Operator filterOperator(
            TransformOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Filter operator is required");
        }
        try {
            return FilterTransform.Operator.valueOf(operator.name());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Not a filter operator: " + operator, exception);
        }
    }

    private static ArithmeticOperator arithmeticOperator(
            TransformOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Arithmetic operator is required");
        }
        try {
            return ArithmeticOperator.valueOf(operator.name());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Not an arithmetic operator: " + operator, exception);
        }
    }
}
