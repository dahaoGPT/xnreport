package com.xn.report.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.ConditionDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.config.definition.ValueReferenceDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.support.TestFixtures;
import com.xn.report.transform.Direction;
import com.xn.report.transform.NullOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void matchesNestedAndOrAgainstDatasetStandard() {
        ConditionNode condition = TestFixtures.and(
                TestFixtures.compare(
                        TestFixtures.field("avgHours"),
                        ComparisonOperator.GT,
                        TestFixtures.datasetField("baseline", "standardHours")),
                TestFixtures.or(
                        TestFixtures.compare(
                                TestFixtures.field("onJob"),
                                ComparisonOperator.EQ,
                                TestFixtures.literal(true)),
                        TestFixtures.compare(
                                TestFixtures.field("groupCategory"),
                                ComparisonOperator.IN,
                                TestFixtures.literal(Arrays.asList("A", "B")))));

        RuleResult result = engine.evaluate(
                "approvalTimeout",
                TestFixtures.personAnnual(),
                condition,
                TestFixtures.contextWithBaseline("10.00"));

        assertThat(result.getMatchedRows())
                .extracting(row -> row.get("personName"))
                .containsExactly("张三");
        assertThat(result.getSummaryValues())
                .containsEntry("matchedCount", 1L)
                .containsEntry("totalCount", 2L)
                .containsEntry("matchedRatio", new BigDecimal("0.5"));
    }

    @Test
    void ordinaryComparisonWithNullDoesNotMatchButNullOperatorDoes() {
        DatasetRow row = DatasetRow.of("hours", null);
        RuleEvaluationContext context = emptyContext();

        assertThat(new ComparisonCondition(
                ValueReference.currentField("hours"),
                ComparisonOperator.GT,
                ValueReference.literal(BigDecimal.ZERO)).evaluate(context, row))
                .isFalse();
        assertThat(new ComparisonCondition(
                ValueReference.currentField("hours"),
                ComparisonOperator.IS_NULL,
                null).evaluate(context, row)).isTrue();
        assertThat(new ComparisonCondition(
                ValueReference.currentField("hours"),
                ComparisonOperator.NE,
                ValueReference.literal(BigDecimal.ZERO)).evaluate(context, row))
                .isFalse();
    }

    @Test
    void logicalConditionsShortCircuitAndRejectEmptyChildren() {
        AtomicInteger evaluations = new AtomicInteger();
        ConditionNode falseNode = (context, row) -> false;
        ConditionNode countingNode = (context, row) -> {
            evaluations.incrementAndGet();
            return true;
        };

        assertThat(LogicalCondition.and(Arrays.asList(falseNode, countingNode))
                .evaluate(emptyContext(), DatasetRow.empty())).isFalse();
        assertThat(evaluations).hasValue(0);
        assertThat(LogicalCondition.or(Arrays.asList(countingNode, falseNode))
                .evaluate(emptyContext(), DatasetRow.empty())).isTrue();
        assertThat(evaluations).hasValue(1);
        assertThatThrownBy(() -> LogicalCondition.and(Collections.<ConditionNode>emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("children");
    }

    @Test
    void supportsAllComparisonOperatorsAndTypedValues() {
        DatasetRow row = DatasetRow.of(
                "number", 10,
                "date", LocalDate.of(2026, 6, 1),
                "text", "AbcDef",
                "enabled", true);
        RuleEvaluationContext context = new RuleEvaluationContext(
                DatasetContext.builder().build(),
                Collections.<String, Object>singletonMap("minimum", 9L));

        assertMatches(row, context, "number", ComparisonOperator.EQ, new BigDecimal("10.0"));
        assertMatches(row, context, "number", ComparisonOperator.NE, 11);
        assertMatches(row, context, "number", ComparisonOperator.GT,
                ValueReference.runtimeParameter("minimum"));
        assertMatches(row, context, "number", ComparisonOperator.GE, 10);
        assertMatches(row, context, "number", ComparisonOperator.LT, 11);
        assertMatches(row, context, "number", ComparisonOperator.LE, 10);
        assertMatches(row, context, "number", ComparisonOperator.IN, Arrays.asList(8L, 10L));
        assertMatches(row, context, "number", ComparisonOperator.NOT_IN, Arrays.asList(8, 9));
        assertMatches(row, context, "number", ComparisonOperator.BETWEEN, Arrays.asList(10, 12));
        assertMatches(row, context, "text", ComparisonOperator.CONTAINS, "cD");
        assertMatches(row, context, "text", ComparisonOperator.STARTS_WITH, "Ab");
        assertMatches(row, context, "text", ComparisonOperator.ENDS_WITH, "Def");
        assertMatches(row, context, "date", ComparisonOperator.GE, LocalDate.of(2026, 6, 1));
        assertMatches(row, context, "enabled", ComparisonOperator.EQ, true);
        assertThat(new ComparisonCondition(
                ValueReference.currentField("text"),
                ComparisonOperator.EQ,
                ValueReference.literal("abcdef"),
                true).evaluate(context, row)).isTrue();
        assertThat(new ComparisonCondition(
                ValueReference.currentField("number"),
                ComparisonOperator.IS_NOT_NULL,
                null).evaluate(context, row)).isTrue();
    }

    @Test
    void rejectsInWithScalarAndCrossTypeOrdering() {
        DatasetRow row = DatasetRow.of("number", 10, "text", "10");

        assertThatThrownBy(() -> new ComparisonCondition(
                ValueReference.currentField("number"),
                ComparisonOperator.IN,
                ValueReference.literal(10)).evaluate(emptyContext(), row))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);
        assertThatThrownBy(() -> new ComparisonCondition(
                ValueReference.currentField("number"),
                ComparisonOperator.GT,
                ValueReference.currentField("text")).evaluate(emptyContext(), row))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);
    }

    @Test
    void datasetReferenceAllowsOnlyScalarOrSingleDatasets() {
        DatasetContext datasets = DatasetContext.builder()
                .put(DatasetResult.list("items",
                        Collections.singletonList(DatasetRow.of("value", 1))))
                .build();
        RuleEvaluationContext context = new RuleEvaluationContext(
                datasets, Collections.<String, Object>emptyMap());

        assertThatThrownBy(() -> ValueReference.datasetField("items", "value")
                .resolve(context, DatasetRow.empty()))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);
    }

    @Test
    void executesFixedResultPipelineAndReturnsDeeplyImmutableResults() {
        RuleDefinition definition = TestFixtures.pipelineRule();

        RuleResult result = engine.evaluate(
                definition, TestFixtures.pipelineRows(), emptyContext());

        assertThat(result.getMatchedRows())
                .extracting(row -> row.get("name"))
                .containsExactly("B", "A");
        assertThat(result.getGroups()).hasSize(2);
        assertThat(result.getGroups().values())
                .extracting(group -> group.getMatchedRows().get(0).get("group"))
                .containsExactly("Y", "X");
        assertThat(result.getGroups().values())
                .allSatisfy(group -> assertThat(group.getMatchedRows()).hasSize(1));
        assertThat(result.getSummaryValues())
                .containsEntry("matchedCount", 2L)
                .containsEntry("totalCount", 4L)
                .containsEntry("maxHours", new BigDecimal("12"));

        assertThatThrownBy(() -> result.getMatchedRows().add(DatasetRow.empty()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.getGroups().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.getSummaryValues().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void compilesStronglyTypedConfigurationBeforeEvaluating() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId("dynamic");
        definition.setDataset("personAnnual");
        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(ConditionDefinition.Operator.GT);
        condition.setLeft(currentFieldDefinition("avgHours"));
        condition.setRight(literalDefinition(new BigDecimal("10")));
        definition.setCondition(condition);

        RuleResult result = engine.evaluate(
                definition, TestFixtures.personAnnual(), emptyContext());

        assertThat(result.getMatchedRows()).hasSize(1);
    }

    @Test
    void invalidConfigurationIsRule001AndEvaluationNeverStarts() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId("invalid");
        definition.setDataset("personAnnual");
        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(ConditionDefinition.Operator.AND);
        condition.setChildren(Collections.<ConditionDefinition>emptyList());
        definition.setCondition(condition);

        assertThatThrownBy(() -> engine.evaluate(
                definition, TestFixtures.personAnnual(), emptyContext()))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_001);
    }

    @Test
    void invalidConfiguredListDatasetReferenceIsRule001BeforeRowEvaluation() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId("invalidReference");
        definition.setDataset("personAnnual");
        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(ConditionDefinition.Operator.GT);
        condition.setLeft(currentFieldDefinition("avgHours"));
        ValueReferenceDefinition right = new ValueReferenceDefinition();
        right.setSource(ValueReferenceDefinition.Source.DATASET_FIELD);
        right.setDataset("standards");
        right.setField("hours");
        condition.setRight(right);
        definition.setCondition(condition);
        DatasetContext datasets = DatasetContext.builder()
                .put(DatasetResult.list("standards",
                        Collections.singletonList(DatasetRow.of("hours", 10))))
                .build();

        assertThatThrownBy(() -> engine.evaluate(
                definition,
                TestFixtures.personAnnual(),
                new RuleEvaluationContext(
                        datasets, Collections.<String, Object>emptyMap())))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_001);
    }

    @Test
    void explicitNullResultIsRule001ButOmittedResultUsesDefaults() {
        RuleDefinition definition = basicConfiguredRule("resultPresence");
        definition.setResult(null);

        assertThatThrownBy(() -> engine.evaluate(
                definition, TestFixtures.personAnnual(), emptyContext()))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_001);

        RuleDefinition omitted = basicConfiguredRule("resultOmitted");
        assertThat(engine.evaluate(
                omitted, TestFixtures.personAnnual(), emptyContext())
                .getMatchedRows()).hasSize(1);
    }

    @Test
    void resultSnapshotsNestedMutableValuesAndRejectsCyclesAndUnknownTypes() {
        Date date = new Date(1_000L);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(2_000L);
        int[] array = new int[] {1, 2};
        List<Object> nested = new ArrayList<Object>();
        nested.add(date);
        nested.add(array);
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("nested", nested);
        summary.put("calendar", calendar);

        RuleResult result = new RuleResult(
                "immutable",
                Collections.<DatasetRow>emptyList(),
                Collections.<String, RuleGroupResult>emptyMap(),
                null,
                summary);

        date.setTime(9_000L);
        calendar.setTimeInMillis(9_000L);
        array[0] = 9;
        nested.clear();
        Map<String, Object> firstRead = result.getSummaryValues();
        @SuppressWarnings("unchecked")
        List<Object> frozenNested = (List<Object>) firstRead.get("nested");
        assertThat(((Date) frozenNested.get(0)).getTime()).isEqualTo(1_000L);
        assertThat(frozenNested.get(1)).isEqualTo(Arrays.asList(1, 2));
        assertThat(((Calendar) firstRead.get("calendar")).getTimeInMillis())
                .isEqualTo(2_000L);

        ((Date) frozenNested.get(0)).setTime(8_000L);
        assertThat(((Date) ((List<?>) result.getSummaryValues().get("nested"))
                .get(0)).getTime()).isEqualTo(1_000L);

        Date groupDate = new Date(3_000L);
        RuleGroupResult group = new RuleGroupResult(
                "group",
                Collections.<DatasetRow>emptyList(),
                Collections.<String, Object>singletonMap("date", groupDate));
        groupDate.setTime(7_000L);
        assertThat(((Date) group.getSummaryValues().get("date")).getTime())
                .isEqualTo(3_000L);
        ((Date) group.getSummaryValues().get("date")).setTime(8_000L);
        assertThat(((Date) group.getSummaryValues().get("date")).getTime())
                .isEqualTo(3_000L);

        List<Object> cycle = new ArrayList<Object>();
        cycle.add(cycle);
        assertThatThrownBy(() -> new RuleResult(
                "cycle",
                Collections.<DatasetRow>emptyList(),
                Collections.<String, RuleGroupResult>emptyMap(),
                null,
                Collections.<String, Object>singletonMap("cycle", cycle)))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_001);
        assertThatThrownBy(() -> new RuleResult(
                "unknown",
                Collections.<DatasetRow>emptyList(),
                Collections.<String, RuleGroupResult>emptyMap(),
                null,
                Collections.<String, Object>singletonMap(
                        "unknown", new StringBuilder("mutable"))))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_001);
    }

    @Test
    void resolvesAllExternalReferencesBeforeFilteringEvenWhenInputIsEmpty() {
        DatasetResult empty = DatasetResult.list(
                "empty", Collections.<DatasetRow>emptyList());

        RuleDefinition missingRuntime = ruleWithCondition(
                "missingRuntime",
                "empty",
                configuredComparison(
                        literalDefinition(1),
                        ConditionDefinition.Operator.EQ,
                        runtimeDefinition("threshold")));
        assertThatThrownBy(() -> engine.evaluate(
                missingRuntime, empty, emptyContext()))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);

        DatasetContext emptySingleContext = DatasetContext.builder()
                .put(DatasetResult.single(
                        "standard", Collections.<DatasetRow>emptyList()))
                .build();
        RuleDefinition emptySingle = ruleWithCondition(
                "emptySingle",
                "empty",
                configuredComparison(
                        datasetFieldDefinition("standard", "hours"),
                        ConditionDefinition.Operator.EQ,
                        literalDefinition(1)));
        assertThatThrownBy(() -> engine.evaluate(
                emptySingle,
                empty,
                new RuleEvaluationContext(
                        emptySingleContext,
                        Collections.<String, Object>emptyMap())))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);

        DatasetContext missingFieldContext = DatasetContext.builder()
                .put(DatasetResult.single(
                        "standard",
                        Collections.singletonList(DatasetRow.of("other", 1))))
                .build();
        assertThatThrownBy(() -> engine.evaluate(
                emptySingle,
                empty,
                new RuleEvaluationContext(
                        missingFieldContext,
                        Collections.<String, Object>emptyMap())))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);
    }

    @Test
    void descendingSortKeepsConfiguredNullOrder() {
        DatasetResult rows = DatasetResult.list("sortable", Arrays.asList(
                DatasetRow.of("name", "N", "value", null),
                DatasetRow.of("name", "O", "value", 1),
                DatasetRow.of("name", "T", "value", 2)));
        RuleDefinition rule = ruleWithCondition(
                "sort",
                "sortable",
                configuredComparison(
                        literalDefinition(true),
                        ConditionDefinition.Operator.EQ,
                        literalDefinition(true)));
        RuleDefinition.ResultDefinition result =
                new RuleDefinition.ResultDefinition();
        com.xn.report.config.definition.SortFieldDefinition sort =
                new com.xn.report.config.definition.SortFieldDefinition();
        sort.setField("value");
        sort.setDirection(Direction.DESC);
        sort.setNullOrder(NullOrder.FIRST);
        result.setSort(Collections.singletonList(sort));
        rule.setResult(result);

        assertThat(engine.evaluate(rule, rows, emptyContext()).getMatchedRows())
                .extracting(row -> row.get("name"))
                .containsExactly("N", "T", "O");

        sort.setNullOrder(NullOrder.LAST);
        assertThat(engine.evaluate(rule, rows, emptyContext()).getMatchedRows())
                .extracting(row -> row.get("name"))
                .containsExactly("T", "O", "N");
    }

    @Test
    void groupingUsesStructuredKeysWithoutDelimiterOrNullCollisions() {
        DatasetResult rows = DatasetResult.list("groups", Arrays.asList(
                DatasetRow.of("x", "a|b", "y", "c"),
                DatasetRow.of("x", "a", "y", "b|c"),
                DatasetRow.of("x", null, "y", "z"),
                DatasetRow.of("x", "null", "y", "z")));
        RuleDefinition rule = ruleWithCondition(
                "groups",
                "groups",
                configuredComparison(
                        literalDefinition(true),
                        ConditionDefinition.Operator.EQ,
                        literalDefinition(true)));
        RuleDefinition.ResultDefinition result =
                new RuleDefinition.ResultDefinition();
        result.setGroupByFields(Arrays.asList("x", "y"));
        rule.setResult(result);

        RuleResult first = engine.evaluate(rule, rows, emptyContext());
        RuleResult second = engine.evaluate(rule, rows, emptyContext());

        assertThat(first.getGroups()).hasSize(4);
        assertThat(first.getGroups().keySet())
                .containsExactlyElementsOf(second.getGroups().keySet());
        assertThat(first.getGroups().values())
                .allSatisfy(group -> assertThat(group.getMatchedRows()).hasSize(1));
    }

    @Test
    void runtimeParametersAndLiteralsAreFrozenAndReadInIsolation() {
        Date runtimeDate = new Date(1_000L);
        int[] runtimeArray = new int[] {1, 2};
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("date", runtimeDate);
        nested.put("array", runtimeArray);
        RuleEvaluationContext context = new RuleEvaluationContext(
                DatasetContext.builder().build(),
                Collections.<String, Object>singletonMap("value", nested));
        ValueReference runtime = ValueReference.runtimeParameter("value");
        runtimeDate.setTime(9_000L);
        runtimeArray[0] = 9;
        nested.clear();

        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeRead =
                (Map<String, Object>) runtime.resolve(context, DatasetRow.empty());
        assertThat(((Date) runtimeRead.get("date")).getTime()).isEqualTo(1_000L);
        assertThat(runtimeRead.get("array")).isEqualTo(Arrays.asList(1, 2));
        ((Date) runtimeRead.get("date")).setTime(8_000L);
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeReadAgain =
                (Map<String, Object>) runtime.resolve(context, DatasetRow.empty());
        assertThat(((Date) runtimeReadAgain.get("date")).getTime()).isEqualTo(1_000L);

        Date literalDate = new Date(2_000L);
        List<Object> literalSource = new ArrayList<Object>();
        literalSource.add(literalDate);
        ValueReference literal = ValueReference.literal(literalSource);
        literalDate.setTime(7_000L);
        literalSource.clear();
        @SuppressWarnings("unchecked")
        List<Object> literalRead =
                (List<Object>) literal.resolve(context, DatasetRow.empty());
        assertThat(((Date) literalRead.get(0)).getTime()).isEqualTo(2_000L);
        ((Date) literalRead.get(0)).setTime(6_000L);
        assertThat(((Date) ((List<?>) literal.resolve(
                context, DatasetRow.empty())).get(0)).getTime()).isEqualTo(2_000L);

        List<Object> cycle = new ArrayList<Object>();
        cycle.add(cycle);
        assertThatThrownBy(() -> new RuleEvaluationContext(
                DatasetContext.builder().build(),
                Collections.<String, Object>singletonMap("cycle", cycle)))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_001);
        assertThatThrownBy(() -> ValueReference.literal(
                new StringBuilder("mutable")))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_001);
    }

    @Test
    void validatesResultFieldsAgainstActualSchemaBeforeNoMatchFiltering() {
        DatasetResult rows = DatasetResult.list(
                "actualSchema",
                Collections.singletonList(DatasetRow.of(
                        "name", "A",
                        "hours", new BigDecimal("5"))));
        RuleDefinition rule = ruleWithCondition(
                "actualSchema",
                "actualSchema",
                configuredComparison(
                        currentFieldDefinition("hours"),
                        ConditionDefinition.Operator.GT,
                        literalDefinition(new BigDecimal("10"))));
        RuleDefinition.ResultDefinition result =
                new RuleDefinition.ResultDefinition();
        com.xn.report.config.definition.SortFieldDefinition sort =
                new com.xn.report.config.definition.SortFieldDefinition();
        sort.setField("hurs");
        sort.setDirection(Direction.ASC);
        sort.setNullOrder(NullOrder.LAST);
        result.setSort(Collections.singletonList(sort));
        rule.setResult(result);

        assertThatThrownBy(() -> engine.evaluate(rule, rows, emptyContext()))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);
    }

    @Test
    void validatesCurrentFieldAgainstExplicitSchemaForEmptyDataset() {
        DatasetResult empty = DatasetResult.list(
                "emptyKnown",
                DatasetSchema.of("hours", BigDecimal.class),
                Collections.<DatasetRow>emptyList());
        RuleDefinition rule = ruleWithCondition(
                "emptyKnown",
                "emptyKnown",
                configuredComparison(
                        currentFieldDefinition("hurs"),
                        ConditionDefinition.Operator.GT,
                        literalDefinition(new BigDecimal("10"))));

        assertThatThrownBy(() -> engine.evaluate(rule, empty, emptyContext()))
                .isInstanceOf(ReportException.class)
                .extracting("errorCode").isEqualTo(ReportErrorCode.RULE_002);
    }

    private void assertMatches(
            DatasetRow row,
            RuleEvaluationContext context,
            String field,
            ComparisonOperator operator,
            Object right) {
        ValueReference reference = right instanceof ValueReference
                ? (ValueReference) right : ValueReference.literal(right);
        assertThat(new ComparisonCondition(
                ValueReference.currentField(field), operator, reference)
                .evaluate(context, row)).isTrue();
    }

    private static RuleEvaluationContext emptyContext() {
        return new RuleEvaluationContext(
                DatasetContext.builder().build(),
                Collections.<String, Object>emptyMap());
    }

    private static RuleDefinition basicConfiguredRule(String id) {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(id);
        definition.setDataset("personAnnual");
        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(ConditionDefinition.Operator.GT);
        condition.setLeft(currentFieldDefinition("avgHours"));
        condition.setRight(literalDefinition(new BigDecimal("10")));
        definition.setCondition(condition);
        return definition;
    }

    private static ValueReferenceDefinition currentFieldDefinition(String field) {
        ValueReferenceDefinition value = new ValueReferenceDefinition();
        value.setSource(ValueReferenceDefinition.Source.CURRENT_FIELD);
        value.setField(field);
        return value;
    }

    private static ValueReferenceDefinition literalDefinition(Object literal) {
        ValueReferenceDefinition value = new ValueReferenceDefinition();
        value.setSource(ValueReferenceDefinition.Source.LITERAL);
        value.setValue(literal);
        return value;
    }

    private static ValueReferenceDefinition runtimeDefinition(String parameter) {
        ValueReferenceDefinition value = new ValueReferenceDefinition();
        value.setSource(ValueReferenceDefinition.Source.RUNTIME_PARAMETER);
        value.setParameter(parameter);
        return value;
    }

    private static ValueReferenceDefinition datasetFieldDefinition(
            String dataset, String field) {
        ValueReferenceDefinition value = new ValueReferenceDefinition();
        value.setSource(ValueReferenceDefinition.Source.DATASET_FIELD);
        value.setDataset(dataset);
        value.setField(field);
        return value;
    }

    private static ConditionDefinition configuredComparison(
            ValueReferenceDefinition left,
            ConditionDefinition.Operator operator,
            ValueReferenceDefinition right) {
        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(operator);
        condition.setLeft(left);
        condition.setRight(right);
        return condition;
    }

    private static RuleDefinition ruleWithCondition(
            String id, String dataset, ConditionDefinition condition) {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(id);
        rule.setDataset(dataset);
        rule.setCondition(condition);
        return rule;
    }
}
