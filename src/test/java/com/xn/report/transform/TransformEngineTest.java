package com.xn.report.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.dataset.DatasetType;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
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
        assertThatThrownBy(() -> new LimitTransform(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
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
    void keepsTransformDefinitionsInYamlDeclarationOrder() throws Exception {
        String yaml = "id: people\n"
                + "sheetName: People\n"
                + "sqlFile: people.sql\n"
                + "resultType: LIST\n"
                + "transforms:\n"
                + "  - type: FILTER\n"
                + "    field: avgHours\n"
                + "    operator: GREATER_THAN\n"
                + "    value: 5\n"
                + "  - type: DISTINCT\n"
                + "    fields: [personName]\n"
                + "  - type: LIMIT\n"
                + "    limit: 10\n";

        DatasetDefinition definition = new ObjectMapper(new YAMLFactory())
                .readValue(yaml, DatasetDefinition.class);

        assertThat(definition.getTransforms())
                .extracting(transform -> transform.getType())
                .containsExactly("FILTER", "DISTINCT", "LIMIT");
        assertThat(definition.getTransforms().get(0).getField()).isEqualTo("avgHours");
        assertThat(definition.getTransforms().get(1).getFields())
                .containsExactly("personName");
        assertThat(definition.getTransforms().get(2).getLimit()).isEqualTo(10);
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
