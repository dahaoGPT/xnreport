package com.xn.report.rule;

import com.xn.report.config.definition.ConditionDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.config.definition.RuleDefinition.ResultDefinition;
import com.xn.report.config.definition.RuleDefinition.SummaryDefinition;
import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.ValueReferenceDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import com.xn.report.error.ReportException;
import com.xn.report.transform.Direction;
import com.xn.report.transform.NullOrder;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuleEngine {

    public RuleResult evaluate(
            String ruleId,
            DatasetResult dataset,
            ConditionNode condition,
            RuleEvaluationContext context) {
        requireDataset(dataset);
        if (condition == null || context == null) {
            throw new IllegalArgumentException("Rule condition and context are required");
        }
        List<DatasetRow> input = rows(dataset);
        List<DatasetRow> matched = filter(input, condition, context);
        return result(ruleId, matched, Collections.<String, RuleGroupResult>emptyMap(),
                summary(matched, input.size(), Collections.<SummaryDefinition>emptyList()));
    }

    public RuleResult evaluate(
            RuleDefinition definition,
            DatasetResult dataset,
            RuleEvaluationContext context) {
        if (definition == null) {
            throw RuleErrors.invalid("Rule definition is required");
        }
        final ConditionNode condition;
        final ResultDefinition resultDefinition;
        try {
            validateRule(definition, dataset);
            if (context == null) {
                throw RuleErrors.invalid("Rule evaluation context is required");
            }
            condition = compile(definition.getCondition());
            resultDefinition = definition.getResult() == null
                    ? new ResultDefinition() : definition.getResult();
            validateResult(resultDefinition);
            validateReferenceDatasets(definition.getCondition(), context);
        } catch (ReportException exception) {
            throw exception.getErrorCode() == com.xn.report.error.ReportErrorCode.RULE_001
                    ? exception : RuleErrors.invalid(
                            "Invalid rule " + definition.getId() + ": "
                                    + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw RuleErrors.invalid(
                    "Invalid rule " + definition.getId() + ": "
                            + exception.getMessage(), exception);
        }

        List<DatasetRow> input = rows(dataset);
        List<DatasetRow> current = filter(input, condition, context);
        current = distinct(current, safe(resultDefinition.getDistinctFields()));
        current = sort(current, safe(resultDefinition.getSort()));
        Map<String, RuleGroupResult> groups =
                group(current, safe(resultDefinition.getGroupByFields()),
                        safe(resultDefinition.getSummaries()));
        current = limit(current, resultDefinition.getMaxItems());
        Map<String, Object> summary =
                summary(current, input.size(), safe(resultDefinition.getSummaries()));
        return result(definition.getId(), current, groups, summary);
    }

    private static void validateReferenceDatasets(
            ConditionDefinition condition, RuleEvaluationContext context) {
        if (condition == null) {
            return;
        }
        validateReferenceDataset(condition.getLeft(), context);
        validateReferenceDataset(condition.getRight(), context);
        if (condition.getChildren() != null) {
            for (ConditionDefinition child : condition.getChildren()) {
                validateReferenceDatasets(child, context);
            }
        }
    }

    private static void validateReferenceDataset(
            ValueReferenceDefinition reference, RuleEvaluationContext context) {
        if (reference == null
                || reference.getSource()
                        != ValueReferenceDefinition.Source.DATASET_FIELD) {
            return;
        }
        if (!context.getDatasets().contains(reference.getDataset())) {
            throw RuleErrors.invalid(
                    "Unknown referenced dataset: " + reference.getDataset());
        }
        DatasetType type =
                context.getDatasets().get(reference.getDataset()).type();
        if (type != DatasetType.SCALAR && type != DatasetType.SINGLE) {
            throw RuleErrors.invalid(
                    "DATASET_FIELD requires SCALAR or SINGLE dataset: "
                            + reference.getDataset());
        }
    }

    public ConditionNode compile(ConditionDefinition definition) {
        if (definition == null || definition.getOperator() == null) {
            throw RuleErrors.invalid("Condition operator is required");
        }
        ConditionDefinition.Operator operator = definition.getOperator();
        if (operator == ConditionDefinition.Operator.AND
                || operator == ConditionDefinition.Operator.OR) {
            rejectPresent(definition,
                    set("operator", "children"));
            if (!definition.hasProperty("children")
                    || definition.getChildren() == null
                    || definition.getChildren().isEmpty()) {
                throw RuleErrors.invalid(
                        "Logical condition requires non-empty children");
            }
            ArrayList<ConditionNode> children = new ArrayList<ConditionNode>();
            for (ConditionDefinition child : definition.getChildren()) {
                children.add(compile(child));
            }
            return new LogicalCondition(
                    operator == ConditionDefinition.Operator.AND
                            ? LogicalCondition.Operator.AND
                            : LogicalCondition.Operator.OR,
                    children);
        }

        boolean unary = operator == ConditionDefinition.Operator.IS_NULL
                || operator == ConditionDefinition.Operator.IS_NOT_NULL;
        rejectPresent(definition, unary
                ? set("operator", "left")
                : set("operator", "left", "right", "ignoreCase"));
        if (!definition.hasProperty("left") || definition.getLeft() == null) {
            throw RuleErrors.invalid("Comparison left reference is required");
        }
        if (unary && definition.hasProperty("right")) {
            throw RuleErrors.invalid(operator + " must not configure right");
        }
        if (!unary
                && (!definition.hasProperty("right") || definition.getRight() == null)) {
            throw RuleErrors.invalid(operator + " requires right reference");
        }
        if (definition.hasProperty("ignoreCase")
                && definition.getIgnoreCase() == null) {
            throw RuleErrors.invalid("ignoreCase must not be null");
        }
        return new ComparisonCondition(
                compile(definition.getLeft()),
                ComparisonOperator.valueOf(operator.name()),
                unary ? null : compile(definition.getRight()),
                Boolean.TRUE.equals(definition.getIgnoreCase()));
    }

    public ValueReference compile(ValueReferenceDefinition definition) {
        if (definition == null || definition.getSource() == null) {
            throw RuleErrors.invalid("Value reference source is required");
        }
        switch (definition.getSource()) {
            case LITERAL:
                rejectPresent(definition, set("source", "value"));
                if (!definition.hasProperty("value") || definition.getValue() == null) {
                    throw RuleErrors.invalid("LITERAL requires non-null value property");
                }
                return ValueReference.literal(definition.getValue());
            case CURRENT_FIELD:
                rejectPresent(definition, set("source", "field"));
                return ValueReference.currentField(requirePresentText(
                        definition, "field", definition.getField()));
            case DATASET_FIELD:
                rejectPresent(definition, set("source", "dataset", "field"));
                return ValueReference.datasetField(
                        requirePresentText(
                                definition, "dataset", definition.getDataset()),
                        requirePresentText(
                                definition, "field", definition.getField()));
            case RUNTIME_PARAMETER:
                rejectPresent(definition, set("source", "parameter"));
                return ValueReference.runtimeParameter(requirePresentText(
                        definition, "parameter", definition.getParameter()));
            default:
                throw RuleErrors.invalid(
                        "Unsupported value reference source: " + definition.getSource());
        }
    }

    private static List<DatasetRow> filter(
            List<DatasetRow> rows,
            ConditionNode condition,
            RuleEvaluationContext context) {
        ArrayList<DatasetRow> matched = new ArrayList<DatasetRow>();
        for (DatasetRow row : rows) {
            if (condition.evaluate(context, row)) {
                matched.add(row);
            }
        }
        return matched;
    }

    private static List<DatasetRow> distinct(
            List<DatasetRow> rows, List<String> fields) {
        if (fields.isEmpty()) {
            return rows;
        }
        Set<Object> seen = new LinkedHashSet<Object>();
        ArrayList<DatasetRow> result = new ArrayList<DatasetRow>();
        for (DatasetRow row : rows) {
            ArrayList<Object> key = new ArrayList<Object>(fields.size());
            for (String field : fields) {
                requireField(row, field);
                key.add(RuleValues.deepKey(row.get(field)));
            }
            if (seen.add(key)) {
                result.add(row);
            }
        }
        return result;
    }

    private static List<DatasetRow> sort(
            List<DatasetRow> rows, List<SortFieldDefinition> fields) {
        if (fields.isEmpty()) {
            return rows;
        }
        ArrayList<DatasetRow> result = new ArrayList<DatasetRow>(rows);
        result.sort(new Comparator<DatasetRow>() {
            @Override
            public int compare(DatasetRow left, DatasetRow right) {
                for (SortFieldDefinition field : fields) {
                    Object leftValue = requireField(left, field.getField());
                    Object rightValue = requireField(right, field.getField());
                    int compared = compareSortValues(
                            leftValue, rightValue, field.getNullOrder());
                    if (field.getDirection() == Direction.DESC) {
                        compared = -compared;
                    }
                    if (compared != 0) {
                        return compared;
                    }
                }
                return 0;
            }
        });
        return result;
    }

    private static int compareSortValues(
            Object left, Object right, NullOrder nullOrder) {
        if (left == null || right == null) {
            if (left == right) {
                return 0;
            }
            boolean leftFirst = nullOrder == NullOrder.FIRST;
            return left == null ? (leftFirst ? -1 : 1) : (leftFirst ? 1 : -1);
        }
        if (left instanceof Number && right instanceof Number) {
            return decimal(left).compareTo(decimal(right));
        }
        if (!left.getClass().equals(right.getClass())
                || !(left instanceof Comparable<?>)) {
            throw RuleErrors.reference("Rule sort values are not type compatible");
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        int result = ((Comparable) left).compareTo(right);
        return result;
    }

    private static Map<String, RuleGroupResult> group(
            List<DatasetRow> rows,
            List<String> fields,
            List<SummaryDefinition> summaries) {
        if (fields.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, List<DatasetRow>> grouped =
                new LinkedHashMap<String, List<DatasetRow>>();
        for (DatasetRow row : rows) {
            ArrayList<String> parts = new ArrayList<String>();
            for (String field : fields) {
                parts.add(String.valueOf(requireField(row, field)));
            }
            String key = String.join("|", parts);
            grouped.computeIfAbsent(key, unused -> new ArrayList<DatasetRow>()).add(row);
        }
        LinkedHashMap<String, RuleGroupResult> result =
                new LinkedHashMap<String, RuleGroupResult>();
        for (Map.Entry<String, List<DatasetRow>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), new RuleGroupResult(
                    entry.getKey(),
                    entry.getValue(),
                    summary(entry.getValue(), entry.getValue().size(), summaries)));
        }
        return result;
    }

    private static List<DatasetRow> limit(List<DatasetRow> rows, Integer maxItems) {
        if (maxItems == null || rows.size() <= maxItems.intValue()) {
            return rows;
        }
        return new ArrayList<DatasetRow>(rows.subList(0, maxItems.intValue()));
    }

    private static Map<String, Object> summary(
            List<DatasetRow> rows,
            int totalCount,
            List<SummaryDefinition> definitions) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("matchedCount", Long.valueOf(rows.size()));
        values.put("totalCount", Long.valueOf(totalCount));
        values.put("matchedRatio", totalCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(rows.size())
                        .divide(BigDecimal.valueOf(totalCount), MathContext.DECIMAL128)
                        .stripTrailingZeros());
        for (SummaryDefinition definition : definitions) {
            values.put(definition.getName(), aggregate(rows, definition));
        }
        return values;
    }

    private static BigDecimal aggregate(
            List<DatasetRow> rows, SummaryDefinition definition) {
        BigDecimal result = null;
        int count = 0;
        for (DatasetRow row : rows) {
            Object value = requireField(row, definition.getField());
            if (value == null) {
                continue;
            }
            if (!(value instanceof Number)) {
                throw RuleErrors.reference(
                        "Summary field is not numeric: " + definition.getField());
            }
            BigDecimal current = decimal(value);
            count++;
            if (result == null) {
                result = current;
            } else {
                switch (definition.getOperation()) {
                    case MAX:
                        result = result.max(current);
                        break;
                    case MIN:
                        result = result.min(current);
                        break;
                    case SUM:
                    case AVG:
                        result = result.add(current);
                        break;
                    default:
                        throw RuleErrors.invalid("Unsupported summary operation");
                }
            }
        }
        if (definition.getOperation() == SummaryDefinition.Operation.AVG
                && result != null) {
            return result.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128);
        }
        return result;
    }

    private static void validateRule(
            RuleDefinition definition, DatasetResult dataset) {
        requireText(definition.getId(), "Rule id");
        requireText(definition.getDataset(), "Rule dataset");
        requireDataset(dataset);
        if (!definition.getDataset().equals(dataset.id())) {
            throw RuleErrors.invalid("Rule dataset does not match input dataset");
        }
        if (definition.getCondition() == null) {
            throw RuleErrors.invalid("Rule condition is required");
        }
    }

    private static void validateResult(ResultDefinition result) {
        for (String property : result.getPresentProperties()) {
            if (ruleResultProperty(result, property) == null) {
                throw RuleErrors.invalid(property + " must not be null");
            }
        }
        if (result.getMaxItems() != null && result.getMaxItems().intValue() < 0) {
            throw RuleErrors.invalid("Rule maxItems must be non-negative");
        }
        for (String field : safe(result.getDistinctFields())) {
            requireText(field, "Distinct field");
        }
        for (String field : safe(result.getGroupByFields())) {
            requireText(field, "Group field");
        }
        for (SortFieldDefinition sort : safe(result.getSort())) {
            if (sort == null
                    || sort.getDirection() == null
                    || sort.getNullOrder() == null) {
                throw RuleErrors.invalid(
                        "Rule sort requires field, direction and nullOrder");
            }
            requireText(sort.getField(), "Sort field");
        }
        for (SummaryDefinition summary : safe(result.getSummaries())) {
            if (summary == null || summary.getOperation() == null) {
                throw RuleErrors.invalid(
                        "Rule summary requires name, field and operation");
            }
            requireText(summary.getName(), "Summary name");
            requireText(summary.getField(), "Summary field");
        }
    }

    private static Object ruleResultProperty(
            ResultDefinition definition, String property) {
        switch (property) {
            case "distinctFields":
                return definition.getDistinctFields();
            case "sort":
                return definition.getSort();
            case "groupByFields":
                return definition.getGroupByFields();
            case "maxItems":
                return definition.getMaxItems();
            case "summaries":
                return definition.getSummaries();
            default:
                throw RuleErrors.invalid("Unknown rule result property: " + property);
        }
    }

    private static Object requireField(DatasetRow row, String field) {
        requireText(field, "Rule field");
        if (!row.containsField(field)) {
            throw RuleErrors.reference("Missing rule field: " + field);
        }
        return row.get(field);
    }

    private static void rejectPresent(
            ConditionDefinition definition, Set<String> allowed) {
        for (String property : definition.getPresentProperties()) {
            if (!allowed.contains(property)) {
                throw RuleErrors.invalid(property + " is not allowed for "
                        + definition.getOperator());
            }
            if (!"right".equals(property)
                    && conditionProperty(definition, property) == null) {
                throw RuleErrors.invalid(property + " must not be null");
            }
        }
    }

    private static Object conditionProperty(
            ConditionDefinition definition, String property) {
        switch (property) {
            case "operator":
                return definition.getOperator();
            case "children":
                return definition.getChildren();
            case "left":
                return definition.getLeft();
            case "right":
                return definition.getRight();
            case "ignoreCase":
                return definition.getIgnoreCase();
            default:
                return null;
        }
    }

    private static void rejectPresent(
            ValueReferenceDefinition definition, Set<String> allowed) {
        for (String property : definition.getPresentProperties()) {
            if (!allowed.contains(property)) {
                throw RuleErrors.invalid(property + " is not allowed for "
                        + definition.getSource());
            }
            if (!"value".equals(property)
                    && referenceProperty(definition, property) == null) {
                throw RuleErrors.invalid(property + " must not be null");
            }
        }
    }

    private static Object referenceProperty(
            ValueReferenceDefinition definition, String property) {
        switch (property) {
            case "source":
                return definition.getSource();
            case "value":
                return definition.getValue();
            case "dataset":
                return definition.getDataset();
            case "field":
                return definition.getField();
            case "parameter":
                return definition.getParameter();
            default:
                return null;
        }
    }

    private static String requirePresentText(
            ValueReferenceDefinition definition, String property, String value) {
        if (!definition.hasProperty(property)) {
            throw RuleErrors.invalid(property + " is required for "
                    + definition.getSource());
        }
        return requireText(value, property);
    }

    private static RuleResult result(
            String id,
            List<DatasetRow> rows,
            Map<String, RuleGroupResult> groups,
            Map<String, Object> summary) {
        return new RuleResult(id, rows, groups, null, summary);
    }

    private static void requireDataset(DatasetResult dataset) {
        if (dataset == null || dataset.type() != DatasetType.LIST) {
            throw RuleErrors.invalid("Rules require a LIST dataset");
        }
    }

    private static List<DatasetRow> rows(DatasetResult dataset) {
        return dataset.list();
    }

    private static BigDecimal decimal(Object value) {
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw RuleErrors.reference("Invalid numeric value: " + value);
        }
    }

    private static String requireText(String text, String label) {
        if (text == null || text.trim().isEmpty()) {
            throw RuleErrors.invalid(label + " must not be blank");
        }
        return text;
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
    }
}
