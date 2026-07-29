package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.policy.EmptyDataPolicy;
import com.xn.report.policy.PolicyResolver;
import com.xn.report.support.JsonSchemaContract;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyConfigurationTest {

    @TempDir
    Path temp;

    @Test
    void loadsAllPolicyScopesAndResolvesComponentThenRuleDatasetReportDefault()
            throws Exception {
        Path yaml = temp.resolve("policies.yml");
        Files.write(yaml, ("schemaVersion: '1.0'\n"
                + "report:\n"
                + "  code: report\n"
                + "  name: Report\n"
                + "datasets:\n"
                + "  - id: monthly\n"
                + "    sheetName: Monthly\n"
                + "    sql: SELECT 1\n"
                + "    resultType: LIST\n"
                + "    policies: { emptyData: SKIP }\n"
                + "rules:\n"
                + "  - id: slow\n"
                + "    dataset: monthly\n"
                + "    condition:\n"
                + "      operator: IS_NULL\n"
                + "      left: { source: CURRENT_FIELD, field: hours }\n"
                + "    policies: { emptyData: FAIL }\n"
                + "narratives:\n"
                + "  - id: note\n"
                + "    sourceType: FIXED_TEMPLATE\n"
                + "    template: no data\n"
                + "    policies: { emptyData: SKIP }\n"
                + "charts:\n"
                + "  - id: trend\n"
                + "    dataset: monthly\n"
                + "    categoryField: month\n"
                + "    series:\n"
                + "      - { name: Hours, field: hours, type: LINE }\n"
                + "    policies: { emptyData: OUTPUT_MESSAGE }\n"
                + "policies: { emptyData: OUTPUT_MESSAGE }\n"
                + "word:\n"
                + "  sections:\n"
                + "    - id: section\n"
                + "      title: Section\n"
                + "      level: 1\n"
                + "      components:\n"
                + "        - type: TABLE\n"
                + "          dataset: monthly\n"
                + "          policies: { emptyData: USE_DEFAULT }\n")
                .getBytes(StandardCharsets.UTF_8));
        ReportDefinition loaded = ReportDefinitionLoader.createDefault().load(yaml);
        PolicyDefinition component = loaded.getWord().getSections().get(0)
                .getComponents().get(0).getPolicies();
        PolicyDefinition rule = loaded.getRules().get(0).getPolicies();
        PolicyDefinition dataset = loaded.getDatasets().get(0).getPolicies();
        PolicyResolver resolver = new PolicyResolver(PolicyDefinition.systemDefaults());

        assertThat(resolver.resolveEmptyData(
                component, rule, dataset, loaded.getPolicies()))
                .isEqualTo(EmptyDataPolicy.USE_DEFAULT);
        assertThat(resolver.resolveEmptyData(
                null, rule, dataset, loaded.getPolicies()))
                .isEqualTo(EmptyDataPolicy.FAIL);
        assertThat(resolver.resolveEmptyData(
                null, null, dataset, loaded.getPolicies()))
                .isEqualTo(EmptyDataPolicy.SKIP);
        assertThat(resolver.resolveEmptyData(
                null, null, null, loaded.getPolicies()))
                .isEqualTo(EmptyDataPolicy.OUTPUT_MESSAGE);
        assertThat(resolver.resolveEmptyData(null, null, null, null))
                .isEqualTo(EmptyDataPolicy.OUTPUT_MESSAGE);
        assertThat(loaded.getCharts().get(0).getPolicies().getEmptyData())
                .isEqualTo(EmptyDataPolicy.OUTPUT_MESSAGE);
        assertThat(loaded.getNarratives().get(0).getPolicies().getEmptyData())
                .isEqualTo(EmptyDataPolicy.SKIP);
    }

    @Test
    void schemaAcceptsScopedPoliciesAndRejectsUnsupportedValues() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaContract schema;
        try (InputStream input = getClass().getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            schema = new JsonSchemaContract(mapper.readTree(input));
        }
        JsonNode valid = mapper.readTree(reportJson("USE_DEFAULT"));
        JsonNode invalid = mapper.readTree(reportJson("MAKE_IT_UP"));

        assertThat(schema.validate(valid)).isEmpty();
        assertThat(schema.validate(invalid)).isNotEmpty();
    }

    private static String reportJson(String componentPolicy) {
        return "{"
                + "\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"r\",\"name\":\"R\"},"
                + "\"datasets\":[{\"id\":\"d\",\"sheetName\":\"D\","
                + "\"sql\":\"SELECT 1\",\"resultType\":\"LIST\","
                + "\"policies\":{\"emptyData\":\"SKIP\"}}],"
                + "\"rules\":[{\"id\":\"rule\",\"dataset\":\"d\","
                + "\"condition\":{\"operator\":\"IS_NULL\","
                + "\"left\":{\"source\":\"CURRENT_FIELD\",\"field\":\"x\"}},"
                + "\"policies\":{\"missingField\":\"WARN_AND_SKIP\"}}],"
                + "\"narratives\":[{\"id\":\"n\","
                + "\"sourceType\":\"FIXED_TEMPLATE\",\"template\":\"none\","
                + "\"policies\":{\"emptyData\":\"SKIP\"}}],"
                + "\"charts\":[{\"id\":\"c\",\"dataset\":\"d\","
                + "\"categoryField\":\"x\",\"series\":[{\"name\":\"X\","
                + "\"field\":\"x\",\"type\":\"LINE\"}],"
                + "\"policies\":{\"emptyData\":\"OUTPUT_MESSAGE\"}}],"
                + "\"policies\":{\"nullValue\":\"RULE_NOT_MATCHED\"},"
                + "\"word\":{\"sections\":[{\"id\":\"s\",\"title\":\"S\","
                + "\"level\":1,\"components\":[{\"type\":\"TABLE\","
                + "\"dataset\":\"d\",\"policies\":{\"emptyData\":\""
                + componentPolicy + "\"}}]}]}}";
    }
}
