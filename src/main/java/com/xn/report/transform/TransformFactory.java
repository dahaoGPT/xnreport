package com.xn.report.transform;

import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformOperator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        validateAttributes(definition);
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

    private static void validateAttributes(TransformDefinition definition) {
        Set<String> allowed;
        switch (definition.getType()) {
            case FILTER:
                allowed = names("field", "operator", "value");
                break;
            case SORT:
                allowed = names("sortFields");
                break;
            case DISTINCT:
                allowed = names("fields");
                break;
            case LIMIT:
                allowed = names("limit");
                break;
            case DERIVED_FIELD:
                allowed = names(
                        "sourceField",
                        "targetField",
                        "operator",
                        "operand",
                        "scale",
                        "divideByZeroStrategy",
                        "divideByZeroDefault",
                        "fieldConflictStrategy");
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported transform type: " + definition.getType());
        }

        rejectUnexpected(definition, allowed);
        if (definition.getType()
                == com.xn.report.config.definition.TransformType.FILTER) {
            boolean nullOperator =
                    definition.getOperator() == TransformOperator.IS_NULL
                    || definition.getOperator() == TransformOperator.IS_NOT_NULL;
            if (nullOperator && definition.hasValue()) {
                throw new IllegalArgumentException(
                        definition.getOperator() + " does not allow value");
            }
            if (!nullOperator
                    && (!definition.hasValue() || definition.getValue() == null)) {
                throw new IllegalArgumentException(
                        "Filter comparison value is required");
            }
        }
        if (definition.getType()
                == com.xn.report.config.definition.TransformType.DERIVED_FIELD) {
            boolean divide =
                    definition.getOperator() == TransformOperator.DIVIDE;
            if (!divide && (definition.getDivideByZeroStrategy() != null
                    || definition.getDivideByZeroDefault() != null)) {
                throw new IllegalArgumentException(
                        "Divide-by-zero settings require DIVIDE operator");
            }
            if (divide
                    && definition.getDivideByZeroStrategy()
                            == DivideByZeroStrategy.DEFAULT_VALUE
                    && definition.getDivideByZeroDefault() == null) {
                throw new IllegalArgumentException(
                        "DEFAULT_VALUE requires divideByZeroDefault");
            }
            if (divide
                    && definition.getDivideByZeroStrategy()
                            != DivideByZeroStrategy.DEFAULT_VALUE
                    && definition.getDivideByZeroDefault() != null) {
                throw new IllegalArgumentException(
                        "divideByZeroDefault requires DEFAULT_VALUE strategy");
            }
        }
    }

    private static void rejectUnexpected(
            TransformDefinition definition, Set<String> allowed) {
        reject(definition.getField(), "field", allowed);
        reject(definition.getFields(), "fields", allowed);
        reject(definition.getSortFields(), "sortFields", allowed);
        reject(definition.getOperator(), "operator", allowed);
        if (definition.hasValue()) {
            reject(Boolean.TRUE, "value", allowed);
        }
        reject(definition.getSourceField(), "sourceField", allowed);
        reject(definition.getTargetField(), "targetField", allowed);
        reject(definition.getOperand(), "operand", allowed);
        reject(definition.getLimit(), "limit", allowed);
        reject(definition.getScale(), "scale", allowed);
        reject(definition.getDivideByZeroStrategy(),
                "divideByZeroStrategy", allowed);
        reject(definition.getDivideByZeroDefault(),
                "divideByZeroDefault", allowed);
        reject(definition.getFieldConflictStrategy(),
                "fieldConflictStrategy", allowed);
    }

    private static void reject(
            Object value, String name, Set<String> allowed) {
        if (value != null && !allowed.contains(name)) {
            throw new IllegalArgumentException(
                    name + " is not allowed for this transform type");
        }
    }

    private static Set<String> names(String... names) {
        return new HashSet<String>(Arrays.asList(names));
    }
}
