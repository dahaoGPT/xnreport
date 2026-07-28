package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.ConditionDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformOperator;
import com.xn.report.config.definition.TransformType;
import com.xn.report.config.definition.ValueReferenceDefinition;
import com.xn.report.dataset.DatasetType;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleConfigurationTest {

    @TempDir
    Path temp;

    @Test
    void loadsAndValidatesNestedRuleFromYaml() throws Exception {
        Path config = temp.resolve("rule.yml");
        Files.write(config, validYaml().getBytes(StandardCharsets.UTF_8));

        ReportDefinition definition =
                ReportDefinitionLoader.createDefault().load(config);
        ValidationResult result =
                new ReportDefinitionValidator().validate(definition);

        assertThat(result.isValid()).as(result.issues().toString()).isTrue();
        assertThat(definition.getRules()).hasSize(1);
        assertThat(definition.getRules().get(0).getCondition().getChildren())
                .hasSize(2);
    }

    @Test
    void validatorRejectsExplicitNullAndCrossVariantPropertiesAsRule001() {
        ReportDefinition report = TestFixtures.report(
                TestFixtures.dataset("people"));
        RuleDefinition rule = new RuleDefinition();
        rule.setId("bad");
        rule.setDataset("people");
        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(ConditionDefinition.Operator.AND);
        condition.setChildren(null);
        rule.setCondition(condition);
        report.setRules(Collections.singletonList(rule));

        ValidationResult result = new ReportDefinitionValidator().validate(report);

        assertThat(result.issues())
                .extracting(ValidationIssue::getCode)
                .contains("RULE-001");

        RuleDefinition.ResultDefinition resultDefinition =
                new RuleDefinition.ResultDefinition();
        resultDefinition.setSort(null);
        rule.setResult(resultDefinition);
        result = new ReportDefinitionValidator().validate(report);
        assertThat(result.issues())
                .extracting(ValidationIssue::getCode)
                .contains("RULE-001");

        ValueReferenceDefinition literal = new ValueReferenceDefinition();
        literal.setSource(ValueReferenceDefinition.Source.LITERAL);
        literal.setValue(1);
        literal.setField("notAllowed");
        condition.setOperator(ConditionDefinition.Operator.EQ);
        condition.setLeft(literal);
        condition.setRight(literalValue(1));

        result = new ReportDefinitionValidator().validate(report);
        assertThat(result.issues())
                .extracting(ValidationIssue::getCode)
                .contains("RULE-001");
    }

    @Test
    void rejectsExplicitNullRulesCollectionAndLiteralValue() throws Exception {
        Path config = temp.resolve("null-rules.yml");
        Files.write(config, ("schemaVersion: '1.0'\n"
                + "report: {code: demo, name: Demo}\n"
                + "datasets:\n"
                + "  - {id: people, sheetName: People, sql: 'select 1'}\n"
                + "rules: null\n").getBytes(StandardCharsets.UTF_8));
        ReportDefinition definition =
                ReportDefinitionLoader.createDefault().load(config);

        assertThat(new ReportDefinitionValidator().validate(definition).issues())
                .extracting(ValidationIssue::getCode)
                .contains("RULE-001");

        ValueReferenceDefinition nullLiteral = literalValue(null);
        RuleDefinition rule = rule("nullLiteral", "people",
                comparison(
                        currentField("hours"),
                        nullLiteral));
        definition.setRules(Collections.singletonList(rule));
        assertThat(new ReportDefinitionValidator().validate(definition).issues())
                .extracting(ValidationIssue::getCode)
                .contains("RULE-001");
    }

    @Test
    void validatorRejectsDuplicateRuleUnknownDatasetAndListDatasetReference() {
        ReportDefinition report = TestFixtures.report(
                TestFixtures.dataset("people"),
                TestFixtures.dataset("baseline"));
        RuleDefinition first = rule("same", "missing",
                comparison(datasetField("people", "hours"), literalValue(1)));
        RuleDefinition second = rule("same", "people",
                comparison(datasetField("baseline", "hours"), literalValue(1)));
        report.setRules(Arrays.asList(first, second));

        ValidationResult result = new ReportDefinitionValidator().validate(report);

        assertThat(result.issues())
                .extracting(ValidationIssue::getCode)
                .contains("RULE-001");
        assertThat(result.issues())
                .extracting(ValidationIssue::getMessage)
                .anyMatch(message -> message.contains("Duplicate rule"))
                .anyMatch(message -> message.contains("Unknown rule dataset"))
                .anyMatch(message -> message.contains("SCALAR or SINGLE"));
    }

    @Test
    void jsonSchemaMatchesLoaderAndValidatorRuleShapes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schema;
        try (java.io.InputStream input = getClass().getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            schema = mapper.readTree(input);
        }
        JsonSchemaContract contract = new JsonSchemaContract(schema);
        JsonNode valid = mapper.readTree(validJson());
        JsonNode explicitNull = mapper.readTree(validJson()
                .replace("\"children\":[", "\"children\":null,\"unused\":["));
        JsonNode crossVariant = mapper.readTree(validJson()
                .replace("\"source\":\"LITERAL\",\"value\":10",
                        "\"source\":\"LITERAL\",\"value\":10,\"field\":\"x\""));

        assertThat(contract.validate(valid)).isEmpty();
        assertThat(contract.validate(explicitNull)).isNotEmpty();
        assertThat(contract.validate(crossVariant)).isNotEmpty();
    }

    @Test
    void validatorRejectsMisspelledCurrentDatasetAndRuntimeReferences() {
        DatasetDefinition people = TestFixtures.dataset("people");
        people.setExpectedFields(fields("avgHours"));
        DatasetDefinition baseline = TestFixtures.dataset("baseline");
        baseline.setResultType(DatasetType.SINGLE);
        baseline.setExpectedFields(fields("standardHours"));
        ReportDefinition report = TestFixtures.report(people, baseline);
        report.setParameters(Collections.singletonMap(
                "period", new ParameterDefinition()));

        RuleDefinition currentTypo = rule("currentTypo", "people",
                comparison(currentField("avgHour"), literalValue(10)));
        RuleDefinition datasetTypo = rule("datasetTypo", "people",
                comparison(
                        currentField("avgHours"),
                        datasetField("baseline", "standardHour")));
        RuleDefinition runtimeTypo = rule("runtimeTypo", "people",
                comparison(
                        currentField("avgHours"),
                        runtimeParameter("peroid")));
        report.setRules(Arrays.asList(currentTypo, datasetTypo, runtimeTypo));

        ValidationResult result = new ReportDefinitionValidator().validate(report);

        assertThat(result.issues())
                .filteredOn(issue -> "RULE-001".equals(issue.getCode()))
                .extracting(ValidationIssue::getMessage)
                .anyMatch(message -> message.contains("avgHour"))
                .anyMatch(message -> message.contains("standardHour"))
                .anyMatch(message -> message.contains("peroid"));
    }

    @Test
    void validatorAcceptsCurrentFieldProducedByDerivedTransform() {
        DatasetDefinition people = TestFixtures.dataset("people");
        people.setExpectedFields(fields("avgHours"));
        TransformDefinition transform = new TransformDefinition();
        transform.setType(TransformType.DERIVED_FIELD);
        transform.setSourceField("avgHours");
        transform.setTargetField("normalizedHours");
        transform.setOperator(TransformOperator.MULTIPLY);
        transform.setOperand(new java.math.BigDecimal("1"));
        people.setTransforms(Collections.singletonList(transform));
        ReportDefinition report = TestFixtures.report(people);
        report.setRules(Collections.singletonList(rule(
                "derived", "people",
                comparison(currentField("normalizedHours"), literalValue(10)))));

        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .noneMatch(issue -> "RULE-001".equals(issue.getCode()));
    }

    private static RuleDefinition rule(
            String id, String dataset, ConditionDefinition condition) {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(id);
        rule.setDataset(dataset);
        rule.setCondition(condition);
        return rule;
    }

    private static ConditionDefinition comparison(
            ValueReferenceDefinition left, ValueReferenceDefinition right) {
        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(ConditionDefinition.Operator.GT);
        condition.setLeft(left);
        condition.setRight(right);
        return condition;
    }

    private static ValueReferenceDefinition datasetField(
            String dataset, String field) {
        ValueReferenceDefinition reference = new ValueReferenceDefinition();
        reference.setSource(ValueReferenceDefinition.Source.DATASET_FIELD);
        reference.setDataset(dataset);
        reference.setField(field);
        return reference;
    }

    private static ValueReferenceDefinition currentField(String field) {
        ValueReferenceDefinition reference = new ValueReferenceDefinition();
        reference.setSource(ValueReferenceDefinition.Source.CURRENT_FIELD);
        reference.setField(field);
        return reference;
    }

    private static ValueReferenceDefinition runtimeParameter(String parameter) {
        ValueReferenceDefinition reference = new ValueReferenceDefinition();
        reference.setSource(ValueReferenceDefinition.Source.RUNTIME_PARAMETER);
        reference.setParameter(parameter);
        return reference;
    }

    private static Map<String, FieldDefinition> fields(String... names) {
        Map<String, FieldDefinition> fields =
                new LinkedHashMap<String, FieldDefinition>();
        for (String name : names) {
            fields.put(name, new FieldDefinition());
        }
        return fields;
    }

    private static ValueReferenceDefinition literalValue(Object value) {
        ValueReferenceDefinition reference = new ValueReferenceDefinition();
        reference.setSource(ValueReferenceDefinition.Source.LITERAL);
        reference.setValue(value);
        return reference;
    }

    private static String validYaml() {
        return "schemaVersion: '1.0'\n"
                + "report: {code: demo, name: Demo}\n"
                + "datasets:\n"
                + "  - id: people\n"
                + "    sheetName: People\n"
                + "    sql: 'select 1'\n"
                + "    resultType: LIST\n"
                + "    expectedFields: {hours: {type: DECIMAL}, name: {type: STRING}}\n"
                + "rules:\n"
                + "  - id: timeout\n"
                + "    dataset: people\n"
                + "    condition:\n"
                + "      operator: AND\n"
                + "      children:\n"
                + "        - operator: GT\n"
                + "          left: {source: CURRENT_FIELD, field: hours}\n"
                + "          right: {source: LITERAL, value: 10}\n"
                + "        - operator: IS_NOT_NULL\n"
                + "          left: {source: CURRENT_FIELD, field: name}\n";
    }

    private static String validJson() {
        return "{\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"demo\",\"name\":\"Demo\"},"
                + "\"datasets\":[{\"id\":\"people\",\"sheetName\":\"People\","
                + "\"sql\":\"select 1\",\"resultType\":\"LIST\","
                + "\"expectedFields\":{\"hours\":{\"type\":\"DECIMAL\"},"
                + "\"name\":{\"type\":\"STRING\"}}}],"
                + "\"rules\":[{\"id\":\"timeout\",\"dataset\":\"people\","
                + "\"condition\":{\"operator\":\"AND\",\"children\":["
                + "{\"operator\":\"GT\","
                + "\"left\":{\"source\":\"CURRENT_FIELD\",\"field\":\"hours\"},"
                + "\"right\":{\"source\":\"LITERAL\",\"value\":10}},"
                + "{\"operator\":\"IS_NOT_NULL\","
                + "\"left\":{\"source\":\"CURRENT_FIELD\",\"field\":\"name\"}}]}}]}";
    }
}
