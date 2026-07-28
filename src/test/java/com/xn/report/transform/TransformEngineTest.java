package com.xn.report.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformOperator;
import com.xn.report.config.definition.TransformType;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.dataset.DatasetType;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class TransformEngineTest {

    private final TransformEngine engine = new TransformEngine();

    @Test
    void appliesTransformsInDeclaredOrderWithoutMutatingSource() {
        DatasetResult source = DatasetResult.list(
                "people",
                DatasetSchema.of(
                        "personName", String.class,
                        "avgHours", BigDecimal.class),
                Arrays.asList(
                        TestFixtures.person("A", "8.00"),
                        TestFixtures.person("A", "8.00"),
                        TestFixtures.person("B", "12.00")));

        DatasetResult result = engine.apply(source, Arrays.<Transform>asList(
                new DistinctTransform(Arrays.asList("personName")),
                new DerivedFieldTransform(
                        "overHours",
                        "avgHours",
                        ArithmeticOperator.SUBTRACT,
                        new BigDecimal("5.00"),
                        2),
                new SortTransform("avgHours", Direction.DESC, NullOrder.LAST),
                new LimitTransform(1)));

        assertThat(result.id()).isEqualTo("people");
        assertThat(result.type()).isEqualTo(DatasetType.LIST);
        assertThat(result.schema().fieldNames())
                .containsExactly("personName", "avgHours", "overHours");
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).get("personName")).isEqualTo("B");
        assertThat(result.list().get(0).get("overHours"))
                .isEqualTo(new BigDecimal("7.00"));
        assertThat(source.schema().fieldNames())
                .containsExactly("personName", "avgHours");
        assertThat(source.list()).hasSize(3);
        assertThat(source.list().get(0).containsField("overHours")).isFalse();
    }

    @Test
    void filtersWithWhitelistedComparisonAndTreatsNullOrderingAsNotMatched() {
        DatasetResult source = TestFixtures.people(
                TestFixtures.person("high", "10.00"),
                DatasetRow.of("personName", "empty", "avgHours", null),
                TestFixtures.person("low", "4.00"));

        DatasetResult result = new FilterTransform(
                "avgHours",
                FilterTransform.Operator.GREATER_THAN,
                new BigDecimal("5.00")).apply(source);

        assertThat(result.list())
                .extracting(row -> row.get("personName"))
                .containsExactly("high");
        assertThat(result.schema()).isSameAs(source.schema());

        DatasetResult notEqual = new FilterTransform(
                "avgHours",
                FilterTransform.Operator.NOT_EQUAL,
                new BigDecimal("5.00")).apply(source);
        assertThat(notEqual.list())
                .extracting(row -> row.get("personName"))
                .containsExactly("high", "low");
    }

    @Test
    void sortsStablyByMultipleFieldsWithExplicitNullOrder() {
        DatasetResult source = TestFixtures.people(
                DatasetRow.of("team", "B", "score", 1, "sequence", 0),
                DatasetRow.of("team", "A", "score", 2, "sequence", 1),
                DatasetRow.of("team", "A", "score", 2, "sequence", 2),
                DatasetRow.of("team", "A", "score", null, "sequence", 3),
                DatasetRow.of("team", "A", "score", 1, "sequence", 4));

        DatasetResult result = new SortTransform(Arrays.asList(
                new SortTransform.SortField("team", Direction.ASC, NullOrder.FIRST),
                new SortTransform.SortField("score", Direction.DESC, NullOrder.LAST)))
                .apply(source);

        assertThat(result.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(1, 2, 4, 3, 0);
        assertThat(source.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(result.schema()).isSameAs(source.schema());
    }

    @Test
    void distinctUsesCompositeKeysAndKeepsFirstEncounteredRows() {
        DatasetResult source = TestFixtures.people(
                DatasetRow.of("name", "A", "month", null, "sequence", 1),
                DatasetRow.of("name", "A", "month", null, "sequence", 2),
                DatasetRow.of("name", "A", "month", "01", "sequence", 3),
                DatasetRow.of("name", "B", "month", null, "sequence", 4));

        DatasetResult result = new DistinctTransform(Arrays.asList("name", "month"))
                .apply(source);

        assertThat(result.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(1, 3, 4);
        assertThat(result.schema()).isSameAs(source.schema());
    }

    @Test
    void limitAcceptsZeroAndRejectsNegativeValues() {
        DatasetResult source = TestFixtures.people(TestFixtures.person("A", "8.00"));

        assertThat(new LimitTransform(0).apply(source).list()).isEmpty();
        assertThat(new LimitTransform(5).apply(source).list())
                .containsExactlyElementsOf(source.list());
        assertThatThrownBy(() -> new LimitTransform(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void filterExposesNoArbitraryCallbackAndHandlesNullOperators() {
        assertThat(Arrays.stream(FilterTransform.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .noneMatch(Predicate.class::isAssignableFrom);

        DatasetResult source = DatasetResult.list(
                "values",
                Arrays.asList(
                        DatasetRow.of("value", null, "sequence", 1),
                        DatasetRow.of("value", "x", "sequence", 2)));

        assertThat(new FilterTransform(
                "value", FilterTransform.Operator.IS_NULL, null)
                .apply(source).list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(1);
        assertThat(new FilterTransform(
                "value", FilterTransform.Operator.IS_NOT_NULL, null)
                .apply(source).list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(2);

        FilterTransform incompatible = new FilterTransform(
                "value", FilterTransform.Operator.GREATER_THAN, Integer.valueOf(1));
        assertThatThrownBy(() -> incompatible.apply(source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safely comparable");
    }

    @Test
    void comparesDateAndTimestampSymmetricallyForSortAndFilter() {
        Date laterDate = new Date(2_000L);
        Timestamp earlierTimestamp = new Timestamp(1_000L);

        assertThat(TransformValueComparator.compare(
                laterDate, earlierTimestamp))
                .isEqualTo(-TransformValueComparator.compare(
                        earlierTimestamp, laterDate))
                .isPositive();

        DatasetResult source = DatasetResult.list(
                "dates",
                Arrays.asList(
                        DatasetRow.of("when", laterDate, "sequence", 1),
                        DatasetRow.of("when", earlierTimestamp, "sequence", 2)));
        DatasetResult sorted = new SortTransform(
                "when", Direction.ASC, NullOrder.LAST).apply(source);
        assertThat(sorted.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(2, 1);

        DatasetResult filtered = new FilterTransform(
                "when",
                FilterTransform.Operator.GREATER_THAN,
                earlierTimestamp).apply(source);
        assertThat(filtered.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(1);

        assertThatThrownBy(() -> TransformValueComparator.compare(
                new java.sql.Date(1_000L),
                java.time.LocalDate.of(1970, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safely comparable");
    }

    @Test
    void filterDefensivelyFreezesExpectedValuesAndRejectsUnknownMutableTypes() {
        byte[] bytes = new byte[]{1, 2};
        List<Object> expected = new java.util.ArrayList<Object>();
        expected.add(bytes);
        FilterTransform transform = new FilterTransform(
                "value", FilterTransform.Operator.EQUAL, expected);

        bytes[0] = 9;
        expected.clear();

        DatasetResult source = DatasetResult.list(
                "values",
                Arrays.asList(
                        DatasetRow.of(
                                "value",
                                Collections.singletonList(new byte[]{1, 2}),
                                "sequence",
                                1),
                        DatasetRow.of(
                                "value",
                                Collections.singletonList(new byte[]{9, 2}),
                                "sequence",
                                2)));
        assertThat(transform.apply(source).list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(1);

        assertThatThrownBy(() -> new FilterTransform(
                "value",
                FilterTransform.Operator.EQUAL,
                new StringBuilder("mutable")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported mutable");

        List<Object> cyclic = new java.util.ArrayList<Object>();
        cyclic.add(cyclic);
        assertThatThrownBy(() -> new FilterTransform(
                "value", FilterTransform.Operator.EQUAL, cyclic))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    void derivedFieldUsesBigDecimalArithmeticAndHalfUpScale() {
        DatasetResult source = TestFixtures.people(TestFixtures.person("A", "10.00"));

        assertThat(derived(source, ArithmeticOperator.ADD, "3.00", 2))
                .isEqualTo(new BigDecimal("13.00"));
        assertThat(derived(source, ArithmeticOperator.SUBTRACT, "3.00", 2))
                .isEqualTo(new BigDecimal("7.00"));
        assertThat(derived(source, ArithmeticOperator.MULTIPLY, "3.00", 2))
                .isEqualTo(new BigDecimal("30.00"));
        assertThat(derived(source, ArithmeticOperator.DIVIDE, "6.00", 2))
                .isEqualTo(new BigDecimal("1.67"));
        assertThat(derived(
                TestFixtures.people(TestFixtures.person("A", "1.50")),
                ArithmeticOperator.ADD,
                "0.00",
                0)).isEqualTo(new BigDecimal("2"));
        assertThatThrownBy(() -> new DerivedFieldTransform(
                "result",
                "avgHours",
                ArithmeticOperator.ADD,
                BigDecimal.ONE,
                -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void derivedFieldPropagatesNullAndRejectsNonNumericInput() {
        DerivedFieldTransform transform = new DerivedFieldTransform(
                "calculated",
                "value",
                ArithmeticOperator.ADD,
                BigDecimal.ONE,
                2);

        DatasetResult nullResult = transform.apply(DatasetResult.list(
                "values", Collections.singletonList(DatasetRow.of("value", null))));
        assertThat(nullResult.list().get(0).get("calculated")).isNull();

        DatasetResult text = DatasetResult.list(
                "values", Collections.singletonList(DatasetRow.of("value", "10.00")));
        assertThatThrownBy(() -> transform.apply(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void derivedFieldDivideByZeroFailsByDefaultAndSupportsExplicitPolicies() {
        DatasetResult source = DatasetResult.list(
                "values",
                Collections.singletonList(
                        DatasetRow.of("value", new BigDecimal("5.00"))));

        DerivedFieldTransform failing = new DerivedFieldTransform(
                "result", "value", ArithmeticOperator.DIVIDE, BigDecimal.ZERO, 2);
        assertThatThrownBy(() -> failing.apply(source))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("zero");

        DerivedFieldTransform nulling = new DerivedFieldTransform(
                "result",
                "value",
                ArithmeticOperator.DIVIDE,
                BigDecimal.ZERO,
                2,
                DivideByZeroStrategy.NULL,
                null,
                FieldConflictStrategy.FAIL);
        assertThat(nulling.apply(source).list().get(0).get("result")).isNull();

        DerivedFieldTransform defaulting = new DerivedFieldTransform(
                "result",
                "value",
                ArithmeticOperator.DIVIDE,
                BigDecimal.ZERO,
                2,
                DivideByZeroStrategy.DEFAULT_VALUE,
                new BigDecimal("9.876"),
                FieldConflictStrategy.FAIL);
        assertThat(defaulting.apply(source).list().get(0).get("result"))
                .isEqualTo(new BigDecimal("9.88"));
    }

    @Test
    void derivedFieldConflictFailsByDefaultAndCanExplicitlyReplace() {
        DatasetResult source = DatasetResult.list(
                "values",
                DatasetSchema.of("value", BigDecimal.class, "result", String.class),
                Collections.singletonList(DatasetRow.of(
                        "value", new BigDecimal("2.00"), "result", "old")));

        DerivedFieldTransform failing = new DerivedFieldTransform(
                "result", "value", ArithmeticOperator.MULTIPLY, BigDecimal.TEN, 2);
        assertThatThrownBy(() -> failing.apply(source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        DerivedFieldTransform replacing = new DerivedFieldTransform(
                "result",
                "value",
                ArithmeticOperator.MULTIPLY,
                BigDecimal.TEN,
                2,
                DivideByZeroStrategy.FAIL,
                null,
                FieldConflictStrategy.REPLACE);
        DatasetResult result = replacing.apply(source);

        assertThat(result.list().get(0).get("result"))
                .isEqualTo(new BigDecimal("20.00"));
        assertThat(result.schema().fieldNames()).containsExactly("value", "result");
        assertThat(result.schema().typeOf("result")).isEqualTo(BigDecimal.class);
        assertThat(source.list().get(0).get("result")).isEqualTo("old");
    }

    @Test
    void derivedFieldSupportsSingleAndEmptyRowsButRejectsScalarBeforeExecution() {
        DerivedFieldTransform transform = new DerivedFieldTransform(
                "result", "value", ArithmeticOperator.ADD, BigDecimal.ONE, 2);
        DatasetSchema sourceSchema = DatasetSchema.of("value", BigDecimal.class);

        DatasetResult single = DatasetResult.single(
                "single",
                sourceSchema,
                Collections.singletonList(
                        DatasetRow.of("value", new BigDecimal("2.00"))));
        DatasetResult singleResult = transform.apply(single);
        assertThat(singleResult.type()).isEqualTo(DatasetType.SINGLE);
        assertThat(singleResult.single().get("result"))
                .isEqualTo(new BigDecimal("3.00"));

        DatasetResult emptySingle = DatasetResult.single(
                "emptySingle", sourceSchema, Collections.<DatasetRow>emptyList());
        DatasetResult emptySingleResult = transform.apply(emptySingle);
        assertThat(emptySingleResult.single()).isNull();
        assertThat(emptySingleResult.schema().fieldNames())
                .containsExactly("value", "result");

        DatasetResult emptyList = DatasetResult.list(
                "emptyList", sourceSchema, Collections.<DatasetRow>emptyList());
        DatasetResult emptyListResult = transform.apply(emptyList);
        assertThat(emptyListResult.list()).isEmpty();
        assertThat(emptyListResult.schema().fieldNames())
                .containsExactly("value", "result");

        DatasetResult scalar = DatasetResult.scalar(
                "scalar",
                sourceSchema,
                Collections.singletonList(
                        DatasetRow.of("value", new BigDecimal("2.00"))));
        assertThatThrownBy(() -> transform.apply(scalar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIST or SINGLE");
    }

    @Test
    void derivedFieldReplacesCaseInsensitiveTargetWithoutDuplicatingSchema() {
        DatasetResult source = DatasetResult.list(
                "values",
                DatasetSchema.of("value", BigDecimal.class, "Result", String.class),
                Collections.singletonList(DatasetRow.of(
                        "value", new BigDecimal("2.00"), "Result", "old")));
        DerivedFieldTransform transform = new DerivedFieldTransform(
                "result",
                "value",
                ArithmeticOperator.MULTIPLY,
                BigDecimal.TEN,
                2,
                DivideByZeroStrategy.FAIL,
                null,
                FieldConflictStrategy.REPLACE);

        DatasetResult result = transform.apply(source);

        assertThat(result.schema().fieldNames()).containsExactly("value", "Result");
        assertThat(result.list().get(0).fieldNames()).containsExactly("value", "Result");
        assertThat(result.list().get(0).get("RESULT"))
                .isEqualTo(new BigDecimal("20.00"));
    }

    @Test
    void distinctUsesDeepArrayContentInCompositeKeys() {
        DatasetResult source = DatasetResult.list(
                "binary",
                Arrays.asList(
                        DatasetRow.of(
                                "key", new byte[]{1, 2},
                                "nested", new Object[]{new int[]{3, 4}, "x"},
                                "sequence", 1),
                        DatasetRow.of(
                                "key", new byte[]{1, 2},
                                "nested", new Object[]{new int[]{3, 4}, "x"},
                                "sequence", 2),
                        DatasetRow.of(
                                "key", new byte[]{2, 1},
                                "nested", new Object[]{new int[]{3, 4}, "x"},
                                "sequence", 3)));

        DatasetResult result =
                new DistinctTransform(Arrays.asList("key", "nested")).apply(source);

        assertThat(result.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(1, 3);
    }

    @Test
    void distinctDeeplyMatchesArraysNestedInCollections() {
        List<byte[]> firstList =
                Collections.singletonList(new byte[]{1, 2});
        List<byte[]> secondList =
                Collections.singletonList(new byte[]{1, 2});
        Map<String, int[]> firstMap = new LinkedHashMap<String, int[]>();
        firstMap.put("numbers", new int[]{3, 4});
        Map<String, int[]> secondMap = new LinkedHashMap<String, int[]>();
        secondMap.put("numbers", new int[]{3, 4});
        Set<byte[]> firstSet = new LinkedHashSet<byte[]>();
        firstSet.add(new byte[]{5, 6});
        Set<byte[]> secondSet = new LinkedHashSet<byte[]>();
        secondSet.add(new byte[]{5, 6});

        DatasetResult source = DatasetResult.list(
                "nested",
                Arrays.asList(
                        DatasetRow.of(
                                "list", firstList,
                                "map", firstMap,
                                "set", firstSet,
                                "sequence", 1),
                        DatasetRow.of(
                                "list", secondList,
                                "map", secondMap,
                                "set", secondSet,
                                "sequence", 2),
                        DatasetRow.of(
                                "list", Collections.singletonList(new byte[]{9}),
                                "map", secondMap,
                                "set", secondSet,
                                "sequence", 3)));

        DatasetResult result = new DistinctTransform(
                Arrays.asList("list", "map", "set")).apply(source);

        assertThat(result.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(1, 3);
    }

    @Test
    void parsesOrderedSortFieldsAndBuildsRuntimeTransforms() throws Exception {
        String yaml = "id: people\n"
                + "sheetName: People\n"
                + "sqlFile: people.sql\n"
                + "resultType: LIST\n"
                + "transforms:\n"
                + "  - type: SORT\n"
                + "    sortFields:\n"
                + "      - field: team\n"
                + "        direction: ASC\n"
                + "        nullOrder: FIRST\n"
                + "      - field: score\n"
                + "        direction: DESC\n"
                + "        nullOrder: LAST\n"
                + "  - type: LIMIT\n"
                + "    limit: 3\n";

        DatasetDefinition definition = new ObjectMapper(new YAMLFactory())
                .readValue(yaml, DatasetDefinition.class);

        assertThat(definition.getTransforms())
                .extracting(transform -> transform.getType())
                .containsExactly(TransformType.SORT, TransformType.LIMIT);
        List<SortFieldDefinition> sortFields =
                definition.getTransforms().get(0).getSortFields();
        assertThat(sortFields)
                .extracting(SortFieldDefinition::getField)
                .containsExactly("team", "score");
        assertThat(sortFields)
                .extracting(SortFieldDefinition::getDirection)
                .containsExactly(Direction.ASC, Direction.DESC);
        assertThat(sortFields)
                .extracting(SortFieldDefinition::getNullOrder)
                .containsExactly(NullOrder.FIRST, NullOrder.LAST);

        DatasetResult source = DatasetResult.list(
                "people",
                Arrays.asList(
                        DatasetRow.of("team", "B", "score", 1, "sequence", 0),
                        DatasetRow.of("team", "A", "score", null, "sequence", 1),
                        DatasetRow.of("team", "A", "score", 1, "sequence", 2),
                        DatasetRow.of("team", "A", "score", 2, "sequence", 3)));
        DatasetResult result = engine.apply(
                source,
                new TransformFactory().createAll(definition.getTransforms()));
        assertThat(result.list())
                .extracting(row -> row.get("sequence"))
                .containsExactly(3, 2, 1);
    }

    @Test
    void rejectsUnknownStronglyTypedTransformConfiguration() {
        String yaml = "id: people\n"
                + "sheetName: People\n"
                + "sqlFile: people.sql\n"
                + "resultType: LIST\n"
                + "transforms:\n"
                + "  - type: UNKNOWN\n";

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        assertThatThrownBy(() -> mapper.readValue(yaml, DatasetDefinition.class))
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void factoryAppliesDefaultsToNullableDerivedConfiguration() {
        TransformDefinition definition = new TransformDefinition();
        definition.setType(TransformType.DERIVED_FIELD);
        definition.setTargetField("ratio");
        definition.setSourceField("value");
        definition.setOperator(TransformOperator.DIVIDE);
        definition.setOperand(new BigDecimal("3"));

        assertThat(definition.getFields()).isNull();
        assertThat(definition.getSortFields()).isNull();

        DatasetResult source = DatasetResult.list(
                "values",
                Collections.singletonList(
                        DatasetRow.of("value", new BigDecimal("10"))));
        DatasetResult result = new TransformFactory().create(definition).apply(source);

        assertThat(result.list().get(0).get("ratio"))
                .isEqualTo(new BigDecimal("3.33"));
    }

    @Test
    void factoryRejectsCrossTypeAndOperatorSpecificAttributes() {
        TransformDefinition filter = new TransformDefinition();
        filter.setType(TransformType.FILTER);
        filter.setField("value");
        filter.setOperator(TransformOperator.EQUAL);
        filter.setValue(1);
        filter.setLimit(2);

        assertThatThrownBy(() -> new TransformFactory().create(filter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");

        TransformDefinition nonDivide = new TransformDefinition();
        nonDivide.setType(TransformType.DERIVED_FIELD);
        nonDivide.setTargetField("result");
        nonDivide.setSourceField("value");
        nonDivide.setOperator(TransformOperator.ADD);
        nonDivide.setOperand(BigDecimal.ONE);
        nonDivide.setDivideByZeroStrategy(DivideByZeroStrategy.FAIL);

        assertThatThrownBy(() -> new TransformFactory().create(nonDivide))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DIVIDE");
    }

    private Object derived(
            DatasetResult source,
            ArithmeticOperator operator,
            String operand,
            int scale) {
        return new DerivedFieldTransform(
                "result",
                "avgHours",
                operator,
                new BigDecimal(operand),
                scale)
                .apply(source)
                .list()
                .get(0)
                .get("result");
    }
}
