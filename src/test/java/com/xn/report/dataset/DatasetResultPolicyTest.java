package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.policy.EmptyDataPolicy;
import com.xn.report.policy.MissingFieldPolicy;
import com.xn.report.policy.NullValuePolicy;
import com.xn.report.policy.PolicyExecutionBridge;
import com.xn.report.policy.PolicyResolver;
import com.xn.report.policy.ReportWarning;
import com.xn.report.policy.TypeMismatchPolicy;
import com.xn.report.sql.SqlQueryResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetResultPolicyTest {

    @Test
    void missingMetadataUsesTypedDefaultForEveryRowAndWarns() {
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        DatasetDefinition definition = definition(
                "hours", "DECIMAL", true, new BigDecimal("8.5"));
        definition.getPolicies().setMissingField(MissingFieldPolicy.USE_DEFAULT);

        DatasetResult result = validator(warnings).validate(
                definition,
                new SqlQueryResult(
                        DatasetSchema.of("name", String.class),
                        Arrays.asList(
                                DatasetRow.of("name", "A"),
                                DatasetRow.of("name", "B"))));

        assertThat(result.list()).extracting(row -> row.get("hours"))
                .containsExactly(new BigDecimal("8.5"), new BigDecimal("8.5"));
        assertThat(result.schema().typeOf("hours")).isEqualTo(BigDecimal.class);
        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("USE_DEFAULT");
    }

    @Test
    void missingMetadataWarnAndSkipSkipsWholeDataset() {
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        DatasetDefinition definition = definition(
                "hours", "DECIMAL", true, null);
        definition.getPolicies().setMissingField(
                MissingFieldPolicy.WARN_AND_SKIP);

        DatasetResult result = validator(warnings).validate(
                definition,
                new SqlQueryResult(
                        DatasetSchema.of("name", String.class),
                        Collections.singletonList(DatasetRow.of("name", "A"))));

        assertThat(result.list()).isEmpty();
        assertThat(result.schema().typeOf("hours"))
                .isEqualTo(BigDecimal.class);
        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("WARN_AND_SKIP");
    }

    @Test
    void scalarMissingMetadataWarnAndSkipReturnsExpectedSingleColumnSchema() {
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        DatasetDefinition definition = definition(
                "hours", "DECIMAL", true, null);
        definition.setResultType(DatasetType.SCALAR);
        definition.getPolicies().setMissingField(
                MissingFieldPolicy.WARN_AND_SKIP);

        DatasetResult result = validator(warnings).validate(
                definition,
                new SqlQueryResult(
                        DatasetSchema.of("legacy_hours", String.class),
                        Collections.singletonList(
                                DatasetRow.of("legacy_hours", "bad"))));

        assertThat(result.scalar()).isNull();
        assertThat(result.schema().fieldNames())
                .containsExactly("hours");
        assertThat(result.schema().typeOf("hours"))
                .isEqualTo(BigDecimal.class);
        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("WARN_AND_SKIP");
    }

    @Test
    void scalarMissingMetadataUseDefaultReplacesOriginalColumnAndValue() {
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        DatasetDefinition definition = definition(
                "hours", "DECIMAL", true, new BigDecimal("8.5"));
        definition.setResultType(DatasetType.SCALAR);
        definition.getPolicies().setMissingField(
                MissingFieldPolicy.USE_DEFAULT);

        DatasetResult result = validator(warnings).validate(
                definition,
                new SqlQueryResult(
                        DatasetSchema.of("legacy_hours", String.class),
                        Collections.singletonList(
                                DatasetRow.of("legacy_hours", "ignored"))));

        assertThat(result.scalar()).isEqualTo(new BigDecimal("8.5"));
        assertThat(result.schema().fieldNames())
                .containsExactly("hours");
        assertThat(result.schema().typeOf("hours"))
                .isEqualTo(BigDecimal.class);
        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("USE_DEFAULT");
    }

    @Test
    void metadataTypeWarnAndSkipKeepsExpectedSchemaForDownstreamBindings() {
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        DatasetDefinition definition = definition(
                "hours", "DECIMAL", true, null);
        definition.getPolicies().setTypeMismatch(
                TypeMismatchPolicy.WARN_AND_SKIP);

        DatasetResult result = validator(warnings).validate(
                definition,
                new SqlQueryResult(
                        DatasetSchema.of("hours", String.class),
                        Collections.singletonList(
                                DatasetRow.of("hours", "bad"))));

        assertThat(result.list()).isEmpty();
        assertThat(result.schema().typeOf("hours"))
                .isEqualTo(BigDecimal.class);
        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("WARN_AND_SKIP");
    }

    @Test
    void missingDefaultFailsInsteadOfInventingValue() {
        DatasetDefinition definition = definition(
                "hours", "DECIMAL", true, null);
        definition.getPolicies().setMissingField(MissingFieldPolicy.USE_DEFAULT);

        assertThatThrownBy(() -> validator(new ArrayList<ReportWarning>()).validate(
                definition,
                new SqlQueryResult(
                        DatasetSchema.of("name", String.class),
                        Collections.singletonList(DatasetRow.of("name", "A")))))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("defaultValue");
    }

    @Test
    void safeConvertCreatesNewRowsAndExpectedSchemaUsingWhitelist() {
        DatasetDefinition definition = definition(
                "amount", "DECIMAL", true, null);
        definition.getPolicies().setTypeMismatch(
                TypeMismatchPolicy.SAFE_CONVERT);
        DatasetRow source = DatasetRow.of("amount", "12.50");

        DatasetResult result = validator(new ArrayList<ReportWarning>()).validate(
                definition,
                new SqlQueryResult(
                        DatasetSchema.of("amount", String.class),
                        Collections.singletonList(source)));

        assertThat(result.list().get(0)).isNotSameAs(source);
        assertThat(result.list().get(0).get("amount"))
                .isEqualTo(new BigDecimal("12.50"));
        assertThat(source.get("amount")).isEqualTo("12.50");
        assertThat(result.schema().typeOf("amount")).isEqualTo(BigDecimal.class);
    }

    @Test
    void safeConvertSupportsDeterministicBooleanAndIsoDateTime() {
        DatasetDefinition definition = definition(
                "enabled", "BOOLEAN", true, null,
                "date", "DATE", true, null,
                "at", "DATETIME", true, null);
        definition.getPolicies().setTypeMismatch(
                TypeMismatchPolicy.SAFE_CONVERT);

        DatasetResult result = validator(new ArrayList<ReportWarning>()).validate(
                definition,
                Collections.singletonList(DatasetRow.of(
                        "enabled", "true",
                        "date", "2026-07-29",
                        "at", "2026-07-29T15:00:01")));

        assertThat(result.list().get(0).get("enabled")).isEqualTo(Boolean.TRUE);
        assertThat(result.list().get(0).get("date"))
                .isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(result.list().get(0).get("at"))
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 15, 0, 1));
    }

    @Test
    void safeConvertRejectsInvalidTextAndLossyLongNarrowing() {
        DatasetDefinition decimal = definition(
                "amount", "DECIMAL", true, null);
        decimal.getPolicies().setTypeMismatch(
                TypeMismatchPolicy.SAFE_CONVERT);
        DatasetDefinition integer = definition(
                "count", "LONG", true, null);
        integer.getPolicies().setTypeMismatch(
                TypeMismatchPolicy.SAFE_CONVERT);

        assertThatThrownBy(() -> validator(new ArrayList<ReportWarning>()).validate(
                decimal,
                Collections.singletonList(DatasetRow.of("amount", "not-number"))))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_003));
        assertThatThrownBy(() -> validator(new ArrayList<ReportWarning>()).validate(
                integer,
                Collections.singletonList(DatasetRow.of(
                        "count", new BigDecimal("1.5")))))
                .isInstanceOf(ReportException.class);
        assertThatThrownBy(() -> validator(new ArrayList<ReportWarning>()).validate(
                integer,
                Collections.singletonList(DatasetRow.of(
                        "count", BigInteger.valueOf(Long.MAX_VALUE)
                                .add(BigInteger.ONE)))))
                .isInstanceOf(ReportException.class);
    }

    @Test
    void typeWarnAndSkipRemovesOnlyBadRowsAndRecordsWarning() {
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        DatasetDefinition definition = definition(
                "hours", "DECIMAL", true, null);
        definition.getPolicies().setTypeMismatch(
                TypeMismatchPolicy.WARN_AND_SKIP);

        DatasetResult result = validator(warnings).validate(
                definition,
                Arrays.asList(
                        DatasetRow.of("hours", new BigDecimal("1")),
                        DatasetRow.of("hours", "bad"),
                        DatasetRow.of("hours", new BigDecimal("2"))));

        assertThat(result.list()).extracting(row -> row.get("hours"))
                .containsExactly(new BigDecimal("1"), new BigDecimal("2"));
        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("WARN_AND_SKIP");
    }

    @Test
    void nullPoliciesAllowFailAndUseTypedDefault() {
        DatasetDefinition allow = definition("value", "STRING", false, null);
        allow.getPolicies().setNullValue(NullValuePolicy.ALLOW);
        assertThat(validator(new ArrayList<ReportWarning>()).validate(
                allow, Collections.singletonList(DatasetRow.of("value", null)))
                .list().get(0).get("value")).isNull();

        DatasetDefinition fail = definition("value", "STRING", false, null);
        fail.getPolicies().setNullValue(NullValuePolicy.FAIL);
        assertThatThrownBy(() -> validator(new ArrayList<ReportWarning>()).validate(
                fail, Collections.singletonList(DatasetRow.of("value", null))))
                .isInstanceOf(ReportException.class);

        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        DatasetDefinition useDefault =
                definition("value", "STRING", true, "fallback");
        useDefault.getPolicies().setNullValue(NullValuePolicy.USE_DEFAULT);
        assertThat(validator(warnings).validate(
                useDefault,
                Collections.singletonList(DatasetRow.of("value", null)))
                .list().get(0).get("value")).isEqualTo("fallback");
        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("USE_DEFAULT");
    }

    @Test
    void defaultPoliciesKeepOptionalNullButRejectRequiredNull() {
        DatasetDefinition optional =
                definition("value", "STRING", false, null);
        assertThat(validator(new ArrayList<ReportWarning>()).validate(
                optional,
                Collections.singletonList(DatasetRow.of("value", null)))
                .list()).hasSize(1);

        DatasetDefinition required =
                definition("value", "STRING", true, null);
        assertThatThrownBy(() -> validator(new ArrayList<ReportWarning>()).validate(
                required,
                Collections.singletonList(DatasetRow.of("value", null))))
                .isInstanceOf(ReportException.class);
    }

    private static DatasetResultValidator validator(
            List<ReportWarning> warnings) {
        PolicyDefinition defaults = PolicyDefinition.systemDefaults();
        defaults.setEmptyData(EmptyDataPolicy.OUTPUT_MESSAGE);
        return new DatasetResultValidator(
                new PolicyExecutionBridge(
                        new PolicyResolver(defaults, warnings::add)),
                new PolicyDefinition());
    }

    private static DatasetDefinition definition(Object... fields) {
        DatasetDefinition definition = new DatasetDefinition();
        definition.setId("policyDataset");
        definition.setResultType(DatasetType.LIST);
        for (int i = 0; i < fields.length; i += 4) {
            FieldDefinition field = new FieldDefinition();
            field.setType((String) fields[i + 1]);
            field.setRequired((Boolean) fields[i + 2]);
            if (fields[i + 3] != null) {
                field.setDefaultValue(fields[i + 3]);
            }
            definition.getExpectedFields().put((String) fields[i], field);
        }
        return definition;
    }
}
