package com.xn.report.transform;

import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformOperator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 内存转换算子构建工厂。
 * <p>
 * 将配置模型 {@link TransformDefinition} 解析、校验属性合法性并实例化为对应的 {@link Transform} 具体实现类：
 * <ul>
 *   <li>{@link FilterTransform}：行条件过滤。</li>
 *   <li>{@link SortTransform}：多字段复合排序。</li>
 *   <li>{@link DistinctTransform}：多字段联合去重。</li>
 *   <li>{@link LimitTransform}：行数截断限制。</li>
 *   <li>{@link DerivedFieldTransform}：派生列四则运算计算。</li>
 * </ul>
 * </p>
 */
public final class TransformFactory {

    /**
     * 批量创建转换算子列表。
     *
     * @param definitions 转换配置定义列表
     * @return Transform 实例列表
     */
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

    /**
     * 根据单个转换配置定义创建 Transform 算子。
     *
     * @param definition 转换配置定义，不可为 null
     * @return Transform 实例
     * @throws IllegalArgumentException 如果必填属性缺失、出现非法多余属性或参数不合法
     */
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

    /**
     * 校验当前转换类型所允许的属性与必需的属性集合。
     */
    private static void validateAttributes(TransformDefinition definition) {
        Set<String> allowed;
        Set<String> required;
        switch (definition.getType()) {
            case FILTER:
                allowed = names("field", "operator", "value");
                required = names("field", "operator");
                break;
            case SORT:
                allowed = names("sortFields");
                required = names("sortFields");
                break;
            case DISTINCT:
                allowed = names("fields");
                required = names("fields");
                break;
            case LIMIT:
                allowed = names("limit");
                required = names("limit");
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
                required = names(
                        "sourceField",
                        "targetField",
                        "operator",
                        "operand");
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
            if (!divide && (definition.hasProperty("divideByZeroStrategy")
                    || definition.hasProperty("divideByZeroDefault"))) {
                throw new IllegalArgumentException(
                        "Divide-by-zero settings require DIVIDE operator");
            }
            if (divide
                    && definition.getDivideByZeroStrategy()
                            == DivideByZeroStrategy.DEFAULT_VALUE
                    && (!definition.hasProperty("divideByZeroDefault")
                            || definition.getDivideByZeroDefault() == null)) {
                throw new IllegalArgumentException(
                        "DEFAULT_VALUE requires divideByZeroDefault");
            }
            if (divide
                    && definition.getDivideByZeroStrategy()
                            != DivideByZeroStrategy.DEFAULT_VALUE
                    && definition.hasProperty("divideByZeroDefault")) {
                throw new IllegalArgumentException(
                        "divideByZeroDefault requires DEFAULT_VALUE strategy");
            }
        }
        rejectMissingOrNull(definition, allowed, required);
    }

    private static void rejectMissingOrNull(
            TransformDefinition definition,
            Set<String> allowed,
            Set<String> required) {
        for (String property : required) {
            if (!definition.hasProperty(property)) {
                throw new IllegalArgumentException(
                        property + " is required for this transform type");
            }
        }
        for (String property : definition.getPresentProperties()) {
            if (!"type".equals(property)
                    && allowed.contains(property)
                    && transformPropertyValue(definition, property) == null) {
                throw new IllegalArgumentException(
                        property + " must not be null");
            }
        }
    }

    private static Object transformPropertyValue(
            TransformDefinition definition, String property) {
        switch (property) {
            case "field":
                return definition.getField();
            case "fields":
                return definition.getFields();
            case "sortFields":
                return definition.getSortFields();
            case "operator":
                return definition.getOperator();
            case "value":
                return definition.getValue();
            case "sourceField":
                return definition.getSourceField();
            case "targetField":
                return definition.getTargetField();
            case "operand":
                return definition.getOperand();
            case "limit":
                return definition.getLimit();
            case "scale":
                return definition.getScale();
            case "divideByZeroStrategy":
                return definition.getDivideByZeroStrategy();
            case "divideByZeroDefault":
                return definition.getDivideByZeroDefault();
            case "fieldConflictStrategy":
                return definition.getFieldConflictStrategy();
            default:
                throw new IllegalArgumentException(
                        "Unknown transform property: " + property);
        }
    }

    private static void rejectUnexpected(
            TransformDefinition definition, Set<String> allowed) {
        for (String property : definition.getPresentProperties()) {
            if (!"type".equals(property) && !allowed.contains(property)) {
                throw new IllegalArgumentException(
                        property + " is not allowed for this transform type");
            }
        }
    }

    private static Set<String> names(String... names) {
        return new HashSet<String>(Arrays.asList(names));
    }
}
