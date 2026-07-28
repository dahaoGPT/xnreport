package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.sql.SqlQueryResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatasetResultValidatorTest {

    private final DatasetResultValidator validator = new DatasetResultValidator();

    @Test
    void validatesAliasesAndTypesFromMetadataWhenListHasNoRows() {
        DatasetDefinition definition = definition(
                DatasetType.LIST,
                field("centerName", "STRING", true),
                field("avgHours", "DECIMAL", false));
        SqlQueryResult missingAlias = new SqlQueryResult(
                DatasetSchema.of("centerName", String.class),
                Collections.<DatasetRow>emptyList());
        SqlQueryResult wrongType = new SqlQueryResult(
                DatasetSchema.of(
                        "centerName", String.class,
                        "avgHours", String.class),
                Collections.<DatasetRow>emptyList());

        assertThatThrownBy(() -> validator.validate(definition, missingAlias))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_002))
                .hasMessageContaining("avgHours");
        assertThatThrownBy(() -> validator.validate(definition, wrongType))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_003))
                .hasMessageContaining("avgHours")
                .hasMessageContaining("DECIMAL");
    }

    @Test
    void preservesJdbcMetadataSchemaOnValidatedEmptyResult() {
        DatasetDefinition definition = definition(
                DatasetType.LIST,
                field("centerName", "STRING", true),
                field("avgHours", "DECIMAL", false));
        DatasetSchema metadataSchema = DatasetSchema.of(
                "centerName", String.class,
                "avgHours", BigDecimal.class);

        DatasetResult result = validator.validate(
                definition,
                new SqlQueryResult(
                        metadataSchema, Collections.<DatasetRow>emptyList()));

        assertThat(result.list()).isEmpty();
        assertThat(result.schema().fieldNames())
                .containsExactly("centerName", "avgHours");
        assertThat(result.schema().typeOf("avgHours"))
                .isEqualTo(BigDecimal.class);
    }

    @Test
    void validatesRequiredAliasesTypesAndOptionalNulls() {
        DatasetDefinition definition = definition(
                DatasetType.LIST,
                field("centerName", "STRING", true),
                field("avgHours", "DECIMAL", false),
                field("statDate", "DATE", true));
        List<DatasetRow> rows = Collections.singletonList(DatasetRow.of(
                "centerName", "开发一中心",
                "avgHours", null,
                "statDate", LocalDate.of(2026, 7, 28)));

        DatasetResult result = validator.validate(definition, rows);

        assertThat(result.type()).isEqualTo(DatasetType.LIST);
        assertThat(result.list()).containsExactlyElementsOf(rows);
    }

    @Test
    void failsClearlyWhenExpectedAliasIsMissing() {
        DatasetDefinition definition = definition(
                DatasetType.LIST,
                field("centerName", "STRING", true),
                field("avgHours", "DECIMAL", true));

        assertThatThrownBy(() -> validator.validate(
                definition,
                Collections.singletonList(
                        DatasetRow.of("centerName", "开发一中心"))))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_002))
                .hasMessageContaining("centerMonthly")
                .hasMessageContaining("avgHours");
    }

    @Test
    void rejectsNullRequiredValuesAndWrongTypes() {
        DatasetDefinition requiredDefinition = definition(
                DatasetType.LIST, field("centerName", "STRING", true));
        DatasetDefinition decimalDefinition = definition(
                DatasetType.LIST, field("avgHours", "DECIMAL", false));

        assertThatThrownBy(() -> validator.validate(
                requiredDefinition,
                Collections.singletonList(DatasetRow.of("centerName", null))))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_002))
                .hasMessageContaining("centerName")
                .hasMessageContaining("null");
        assertThatThrownBy(() -> validator.validate(
                decimalDefinition,
                Collections.singletonList(DatasetRow.of("avgHours", "25.27"))))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_003))
                .hasMessageContaining("avgHours")
                .hasMessageContaining("DECIMAL");
    }

    @Test
    void rejectsScalarAndSingleShapeViolations() {
        DatasetDefinition scalar = definition(
                DatasetType.SCALAR, field("count", "INTEGER", false));
        DatasetDefinition single = definition(
                DatasetType.SINGLE, field("centerName", "STRING", false));

        assertThatThrownBy(() -> validator.validate(
                scalar,
                Collections.singletonList(DatasetRow.of(
                        "count", 1L, "extra", "not scalar"))))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_001));
        assertThatThrownBy(() -> validator.validate(
                single,
                Arrays.asList(
                        DatasetRow.of("centerName", "A"),
                        DatasetRow.of("centerName", "B"))))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_001));
    }

    @Test
    void rejectsUnknownExpectedTypeInsteadOfSilentlyAcceptingIt() {
        DatasetDefinition definition = definition(
                DatasetType.LIST, field("value", "MONEY", false));

        assertThatThrownBy(() -> validator.validate(
                definition,
                Collections.singletonList(
                        DatasetRow.of("value", new BigDecimal("1.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MONEY");
    }

    @SafeVarargs
    private static DatasetDefinition definition(
            DatasetType type, Map.Entry<String, FieldDefinition>... fields) {
        DatasetDefinition definition = new DatasetDefinition();
        definition.setId("centerMonthly");
        definition.setResultType(type);
        Map<String, FieldDefinition> expected =
                new LinkedHashMap<String, FieldDefinition>();
        for (Map.Entry<String, FieldDefinition> field : fields) {
            expected.put(field.getKey(), field.getValue());
        }
        definition.setExpectedFields(expected);
        return definition;
    }

    private static Map.Entry<String, FieldDefinition> field(
            String name, String type, boolean required) {
        FieldDefinition definition = new FieldDefinition();
        definition.setType(type);
        definition.setRequired(required);
        return new java.util.AbstractMap.SimpleImmutableEntry<
                String, FieldDefinition>(name, definition);
    }
}
