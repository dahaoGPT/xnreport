package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.DistributionDefinition.BinDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReportDefinitionValidatorTest {

    private final ReportDefinitionValidator validator = new ReportDefinitionValidator();

    @Test
    void reportsDatasetProblemsTogether() {
        DatasetDefinition first = TestFixtures.dataset(
                "a", null, null, new String[]{"missing"});
        DatasetDefinition second = TestFixtures.dataset(
                "a", "a.sql", null, new String[]{"a"});
        second.setSheetName("Second");
        ReportDefinition definition = TestFixtures.report(first, second);

        ValidationResult result = validator.validate(definition);

        assertThat(result.codes()).contains(
                "CFG-DUPLICATE-DATASET",
                "CFG-SQL-SOURCE",
                "CFG-UNKNOWN-DEPENDENCY",
                "CFG-DEPENDENCY-CYCLE");
        assertThat(result.isValid()).isFalse();
        assertThatThrownBy(result::throwIfInvalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CFG-DUPLICATE-DATASET");
    }

    @Test
    void reportsMissingDuplicateIllegalAndOverlongSheetNames() {
        DatasetDefinition missing = TestFixtures.dataset("missing");
        missing.setSheetName(" ");
        DatasetDefinition duplicateOne = TestFixtures.dataset("duplicateOne");
        duplicateOne.setSheetName("Summary");
        DatasetDefinition duplicateTwo = TestFixtures.dataset("duplicateTwo");
        duplicateTwo.setSheetName("summary");
        DatasetDefinition illegal = TestFixtures.dataset("illegal");
        illegal.setSheetName("bad/name");
        DatasetDefinition overlong = TestFixtures.dataset("overlong");
        overlong.setSheetName("12345678901234567890123456789012");

        ValidationResult result = validator.validate(TestFixtures.report(
                missing, duplicateOne, duplicateTwo, illegal, overlong));

        assertThat(result.codes()).contains(
                "CFG-SHEET-NAME-REQUIRED",
                "CFG-DUPLICATE-SHEET-NAME",
                "CFG-SHEET-NAME-ILLEGAL",
                "CFG-SHEET-NAME-LENGTH");
    }

    @Test
    void acceptsValidWordTreeAndComponentReferences() {
        ReportDefinition definition = TestFixtures.report(TestFixtures.dataset("source"));
        NarrativeDefinition narrative = narrative("summary", "RULE_GENERATED");
        definition.setNarratives(Arrays.asList(narrative));

        WordSectionDefinition parent = section("overview", 1, "KEEP");
        parent.setComponents(Arrays.asList(
                component("SCENARIO", "Scenario", null, null, null),
                component("KEY_FACTORS", "Factors", null, null, null),
                component("FIXED_TEXT", "Text", null, null, null),
                component("RULE_TEXT", null, null, "summary", null),
                component("CHART", null, "trend", null, null),
                component("TABLE", null, null, null, "details"),
                component("UNIT", "hours", null, null, null),
                component("ATTACHMENT", "appendix", null, null, null)));
        WordSectionDefinition child = section("details", 2, "SHOW_EMPTY");
        WordSectionDefinition grandchild = section("appendix", 4, "SKIP");
        child.setChildren(Arrays.asList(grandchild));
        parent.setChildren(Arrays.asList(child));
        definition.getWord().setSections(Arrays.asList(parent));
        definition.getWord().getToc().setMaxLevel(4);

        ValidationResult result = validator.validate(definition);

        assertThat(result.isValid()).isTrue();
        assertThat(result.codes()).isEmpty();
        assertThatCode(result::throwIfInvalid).doesNotThrowAnyException();
    }

    @Test
    void reportsWordTreeAndReferenceProblemsTogether() {
        ReportDefinition definition = TestFixtures.report(TestFixtures.dataset("source"));
        definition.setNarratives(Arrays.asList(narrative("narrative", "OTHER")));
        definition.getWord().getToc().setMaxLevel(5);

        WordSectionDefinition parent = section("duplicate", 0, "UNKNOWN");
        parent.setComponents(Arrays.asList(
                component("UNKNOWN", null, null, null, null),
                component("FIXED_TEXT", " ", null, null, null),
                component("RULE_TEXT", null, null, "missing", null),
                component("CHART", null, " ", null, null),
                component("TABLE", null, null, null, " ")));
        WordSectionDefinition child = section("duplicate", 0, "KEEP");
        parent.setChildren(Arrays.asList(child));
        definition.getWord().setSections(Arrays.asList(parent));

        ValidationResult result = validator.validate(definition);

        assertThat(result.codes()).contains(
                "CFG-DUPLICATE-SECTION",
                "CFG-SECTION-LEVEL",
                "CFG-SECTION-HIERARCHY",
                "CFG-COMPONENT-TYPE",
                "CFG-COMPONENT-REFERENCE",
                "CFG-EMPTY-STRATEGY",
                "CFG-TOC-LEVEL",
                "CFG-NARRATIVE-SOURCE-TYPE");
    }

    @Test
    void reportsOverlappingDistributionBinsIncludingSharedClosedBoundary() {
        ReportDefinition definition = TestFixtures.report(TestFixtures.dataset("source"));
        NarrativeDefinition narrative = narrative("distribution", "RULE_GENERATED");
        narrative.getDistribution().setBins(Arrays.asList(
                bin("first", "0", true, "10", true),
                bin("second", "10", true, "20", true)));
        definition.setNarratives(Arrays.asList(narrative));

        ValidationResult result = validator.validate(definition);

        assertThat(result.codes()).contains("CFG-DISTRIBUTION-OVERLAP");
    }

    @Test
    void acceptsTouchingDistributionBinsWhenSharedBoundaryIsOpen() {
        ReportDefinition definition = TestFixtures.report(TestFixtures.dataset("source"));
        NarrativeDefinition narrative = narrative("distribution", "RULE_GENERATED");
        narrative.getDistribution().setBins(Arrays.asList(
                bin("first", "0", true, "10", true),
                bin("second", "10", false, "20", true)));
        definition.setNarratives(Arrays.asList(narrative));

        ValidationResult result = validator.validate(definition);

        assertThat(result.codes()).doesNotContain("CFG-DISTRIBUTION-OVERLAP");
    }

    @Test
    void schemaMatchesRequiredReportDefinitionConstraints() throws Exception {
        JsonNode schema = new ObjectMapper().readTree(Paths.get(
                "src/main/resources/schema/report-definition.schema.json").toFile());

        assertThat(schema.path("$schema").asText()).contains("json-schema");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required").toString())
                .contains("\"schemaVersion\"", "\"report\"", "\"datasets\"");

        JsonNode dataset = schema.path("definitions").path("dataset");
        assertThat(dataset.path("additionalProperties").asBoolean()).isFalse();
        assertThat(dataset.path("required").toString())
                .contains("\"id\"", "\"sheetName\"");
        assertThat(dataset.path("oneOf").size()).isEqualTo(2);
        assertThat(dataset.path("properties").path("sheetName")
                .path("maxLength").asInt()).isEqualTo(31);
        assertThat(dataset.path("properties").path("sheetName")
                .path("pattern").asText()).contains("\\\\", "/", "?");

        JsonNode wordSection = schema.path("definitions").path("wordSection");
        assertThat(wordSection.path("properties").path("children")
                .path("items").path("$ref").asText())
                .isEqualTo("#/definitions/wordSection");
        assertThat(wordSection.path("properties").path("level")
                .path("minimum").asInt()).isEqualTo(1);
        assertThat(wordSection.path("properties").path("level")
                .path("maximum").asInt()).isEqualTo(4);

        assertThat(schema.path("definitions").path("wordComponent")
                .path("properties").path("type").path("enum").toString())
                .contains("\"SCENARIO\"", "\"KEY_FACTORS\"", "\"FIXED_TEXT\"",
                        "\"RULE_TEXT\"", "\"CHART\"", "\"TABLE\"",
                        "\"UNIT\"", "\"ATTACHMENT\"");
        assertThat(schema.path("definitions").path("narrative")
                .path("properties").path("sourceType").path("enum").toString())
                .contains("\"FIXED_TEMPLATE\"", "\"RULE_GENERATED\"");
    }

    private static WordSectionDefinition section(String id, int level, String emptyStrategy) {
        WordSectionDefinition section = new WordSectionDefinition();
        section.setId(id);
        section.setTitle(id);
        section.setLevel(level);
        section.setEmptyStrategy(emptyStrategy);
        return section;
    }

    private static WordComponentDefinition component(
            String type, String text, String chartId, String narrativeId, String tableId) {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setType(type);
        component.setText(text);
        component.setChartId(chartId);
        component.setNarrativeId(narrativeId);
        component.setTableId(tableId);
        return component;
    }

    private static NarrativeDefinition narrative(String id, String sourceType) {
        NarrativeDefinition narrative = new NarrativeDefinition();
        narrative.setId(id);
        narrative.setSourceType(sourceType);
        return narrative;
    }

    private static BinDefinition bin(
            String id,
            String min,
            boolean minInclusive,
            String max,
            boolean maxInclusive) {
        BinDefinition bin = new DistributionDefinition.BinDefinition();
        bin.setId(id);
        bin.setMin(new BigDecimal(min));
        bin.setMinInclusive(minInclusive);
        bin.setMax(new BigDecimal(max));
        bin.setMaxInclusive(maxInclusive);
        return bin;
    }
}
