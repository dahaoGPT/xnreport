package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.support.JsonSchemaContract;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NarrativeConfigurationTest {

    @TempDir
    Path temp;

    @Test
    void loadsStronglyTypedRuleGeneratedNarrative() throws Exception {
        Path path = temp.resolve("narrative.yml");
        Files.write(path, validYaml().getBytes(StandardCharsets.UTF_8));

        ReportDefinition definition =
                ReportDefinitionLoader.createDefault().load(path);
        NarrativeDefinition narrative = definition.getNarratives().get(0);

        assertThat(narrative.getSourceType())
                .isEqualTo(NarrativeDefinition.SourceType.RULE_GENERATED);
        assertThat(narrative.getEmptyStrategy())
                .isEqualTo(NarrativeDefinition.EmptyStrategy.OUTPUT_MESSAGE);
        assertThat(narrative.getDistribution().getLabelMode())
                .isEqualTo(DistributionDefinition.LabelMode.COUNT_AND_PERCENT);
        assertThat(new ReportDefinitionValidator().validate(definition).isValid())
                .isTrue();
    }

    @Test
    void validatorRejectsExplicitNullAndCrossVariantProperties() throws Exception {
        Path nullPath = temp.resolve("null.yml");
        Files.write(nullPath, validYaml()
                .replace("labelMode: COUNT_AND_PERCENT",
                        "labelMode: null")
                .getBytes(StandardCharsets.UTF_8));
        ReportDefinition explicitNull =
                ReportDefinitionLoader.createDefault().load(nullPath);

        assertThat(explicitNull.getNarratives().get(0)
                .getDistribution().hasProperty("labelMode")).isTrue();
        assertThat(new ReportDefinitionValidator().validate(explicitNull).issues())
                .extracting(ValidationIssue::getCode)
                .contains("TEXT-001");

        Path crossPath = temp.resolve("cross.yml");
        Files.write(crossPath, ("schemaVersion: '1.0'\n"
                + "report: {code: demo, name: Demo}\n"
                + "datasets:\n"
                + "  - {id: monthly, sheetName: Monthly, sql: 'select 1'}\n"
                + "narratives:\n"
                + "  - id: fixed\n"
                + "    sourceType: FIXED_TEMPLATE\n"
                + "    template: 固定文字\n"
                + "    analyzer: forbidden\n"
                + "    distribution: {field: hours, bins: []}\n")
                .getBytes(StandardCharsets.UTF_8));
        ReportDefinition cross =
                ReportDefinitionLoader.createDefault().load(crossPath);

        assertThat(new ReportDefinitionValidator().validate(cross).issues())
                .filteredOn(issue -> "TEXT-001".equals(issue.getCode()))
                .extracting(ValidationIssue::getMessage)
                .anyMatch(message -> message.contains("not allowed"));

        Path missingPath = temp.resolve("missing-label-mode.yml");
        Files.write(missingPath, validYaml()
                .replace("      labelMode: COUNT_AND_PERCENT\n", "")
                .getBytes(StandardCharsets.UTF_8));
        ReportDefinition missing =
                ReportDefinitionLoader.createDefault().load(missingPath);
        assertThat(new ReportDefinitionValidator().validate(missing).issues())
                .filteredOn(issue -> "TEXT-001".equals(issue.getCode()))
                .extracting(ValidationIssue::getMessage)
                .anyMatch(message -> message.contains("labelMode"));
    }

    @Test
    void schemaAndRuntimeValidatorAgreeOnNullAndVariantShapes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schema;
        try (java.io.InputStream input = getClass().getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            schema = mapper.readTree(input);
        }
        JsonSchemaContract contract = new JsonSchemaContract(schema);
        String valid = validJson();
        String nullLabel = valid.replace(
                "\"labelMode\":\"COUNT_AND_PERCENT\"",
                "\"labelMode\":null");
        String crossVariant = valid.replace(
                "\"sentence\":\"${summary.total}\"",
                "\"sentence\":\"${summary.total}\",\"template\":\"forbidden\"");

        assertThat(contract.validate(mapper.readTree(valid))).isEmpty();
        assertThat(contract.validate(mapper.readTree(nullLabel))).isNotEmpty();
        assertThat(contract.validate(mapper.readTree(crossVariant))).isNotEmpty();
    }

    private static String validYaml() {
        return "schemaVersion: '1.0'\n"
                + "report: {code: demo, name: Demo}\n"
                + "datasets:\n"
                + "  - {id: monthly, sheetName: Monthly, sql: 'select 1'}\n"
                + "narratives:\n"
                + "  - id: distribution\n"
                + "    sourceType: RULE_GENERATED\n"
                + "    analyzer: approvalDistribution\n"
                + "    dataset: monthly\n"
                + "    sentence: '${summary.total}'\n"
                + "    emptyStrategy: OUTPUT_MESSAGE\n"
                + "    distribution:\n"
                + "      field: hours\n"
                + "      bins:\n"
                + "        - {id: low, label: Low, max: 24, maxInclusive: true}\n"
                + "        - {id: high, label: High, min: 24, minInclusive: false}\n"
                + "      labelMode: COUNT_AND_PERCENT\n";
    }

    private static String validJson() {
        return "{\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"demo\",\"name\":\"Demo\"},"
                + "\"datasets\":[{\"id\":\"monthly\",\"sheetName\":\"Monthly\","
                + "\"sql\":\"select 1\"}],"
                + "\"narratives\":[{\"id\":\"distribution\","
                + "\"sourceType\":\"RULE_GENERATED\","
                + "\"analyzer\":\"approvalDistribution\","
                + "\"dataset\":\"monthly\","
                + "\"sentence\":\"${summary.total}\","
                + "\"emptyStrategy\":\"OUTPUT_MESSAGE\","
                + "\"distribution\":{\"field\":\"hours\",\"bins\":["
                + "{\"id\":\"low\",\"label\":\"Low\",\"max\":24,"
                + "\"maxInclusive\":true},"
                + "{\"id\":\"high\",\"label\":\"High\",\"min\":24,"
                + "\"minInclusive\":false}],"
                + "\"labelMode\":\"COUNT_AND_PERCENT\"}}]}";
    }
}
