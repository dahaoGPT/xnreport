package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.TrendDefinition;
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
        assertThat(narrative.getAnalyzerType())
                .isEqualTo(NarrativeDefinition.AnalyzerType.DISTRIBUTION);
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
        String nullInclusivity = valid.replace(
                "\"maxInclusive\":true", "\"maxInclusive\":null");
        String nullDistribution = valid.replace(
                "\"distribution\":{\"field\":\"hours\"",
                "\"distribution\":null,\"unused\":{\"field\":\"hours\"");

        assertThat(contract.validate(mapper.readTree(valid))).isEmpty();
        assertThat(contract.validate(mapper.readTree(nullLabel))).isNotEmpty();
        assertThat(contract.validate(mapper.readTree(crossVariant))).isNotEmpty();
        assertThat(contract.validate(mapper.readTree(nullInclusivity))).isNotEmpty();
        assertThat(contract.validate(mapper.readTree(nullDistribution))).isNotEmpty();

        JsonNode validNode = mapper.readTree(valid);
        java.util.List<ObjectNode> invalidShapes =
                new java.util.ArrayList<ObjectNode>();
        ObjectNode nullId = validNode.deepCopy();
        ((ObjectNode) nullId.path("narratives").get(0)).putNull("id");
        invalidShapes.add(nullId);
        ObjectNode crossBaseline = validNode.deepCopy();
        ((ObjectNode) crossBaseline.path("narratives").get(0))
                .put("baseline", "forbidden");
        invalidShapes.add(crossBaseline);
        ObjectNode nullFormat = validNode.deepCopy();
        ((ObjectNode) nullFormat.path("narratives").get(0))
                .putNull("format");
        invalidShapes.add(nullFormat);
        ObjectNode missingAnalyzerType = validNode.deepCopy();
        ((ObjectNode) missingAnalyzerType.path("narratives").get(0))
                .remove("analyzerType");
        invalidShapes.add(missingAnalyzerType);
        ObjectNode nullRuntimeDistribution = validNode.deepCopy();
        ((ObjectNode) nullRuntimeDistribution.path("narratives").get(0))
                .putNull("distribution");
        invalidShapes.add(nullRuntimeDistribution);
        ObjectNode nullBoundary = validNode.deepCopy();
        ((ObjectNode) nullBoundary.path("narratives").get(0)
                .path("distribution").path("bins").get(0))
                .putNull("maxInclusive");
        invalidShapes.add(nullBoundary);

        for (ObjectNode invalid : invalidShapes) {
            assertThat(contract.validate(invalid)).isNotEmpty();
            ReportDefinition parsed =
                    mapper.treeToValue(invalid, ReportDefinition.class);
            assertThat(new ReportDefinitionValidator().validate(parsed).issues())
                    .extracting(ValidationIssue::getCode)
                    .contains("TEXT-001");
        }
    }

    @Test
    void loadsAndValidatesStronglyTypedTrendComparison() throws Exception {
        Path path = temp.resolve("trend.yml");
        Files.write(path, ("schemaVersion: '1.0'\n"
                + "report: {code: demo, name: Demo}\n"
                + "datasets:\n"
                + "  - {id: monthly, sheetName: Monthly, sql: 'select 1'}\n"
                + "  - {id: baseline, sheetName: Baseline, sql: 'select 1',"
                + " resultType: SINGLE}\n"
                + "narratives:\n"
                + "  - id: trend\n"
                + "    sourceType: RULE_GENERATED\n"
                + "    analyzer: approvalTrend\n"
                + "    analyzerType: TREND\n"
                + "    dataset: monthly\n"
                + "    sentence: '${summary.direction}'\n"
                + "    trend:\n"
                + "      periodField: month\n"
                + "      valueField: hours\n"
                + "      comparisonSource: DATASET_FIELD\n"
                + "      comparisonDataset: baseline\n"
                + "      comparisonField: standardHours\n"
                + "      flatTolerance: 0.01\n"
                + "      abnormalThreshold: 24\n")
                .getBytes(StandardCharsets.UTF_8));

        ReportDefinition definition =
                ReportDefinitionLoader.createDefault().load(path);
        TrendDefinition trend = definition.getNarratives().get(0).getTrend();

        assertThat(trend.getComparisonSource())
                .isEqualTo(TrendDefinition.ComparisonSource.DATASET_FIELD);
        assertThat(new ReportDefinitionValidator().validate(definition).isValid())
                .isTrue();
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
                + "    analyzerType: DISTRIBUTION\n"
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
                + "\"analyzerType\":\"DISTRIBUTION\","
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
