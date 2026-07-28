package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.DistributionDefinition.BinDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
    void rejectsSheetNamesBeginningOrEndingWithSingleQuote() {
        DatasetDefinition leadingQuote = TestFixtures.dataset("leadingQuote");
        leadingQuote.setSheetName("'abc");
        DatasetDefinition trailingQuote = TestFixtures.dataset("trailingQuote");
        trailingQuote.setSheetName("abc'");

        ValidationResult result =
                validator.validate(TestFixtures.report(leadingQuote, trailingQuote));

        assertThat(pathsForCode(result, "CFG-SHEET-NAME-ILLEGAL"))
                .containsExactly(
                        "$.datasets[0].sheetName",
                        "$.datasets[1].sheetName");
    }

    @Test
    void matchesXssfWorkbookUnicodeCaseInsensitiveDuplicateBehavior() throws Exception {
        assertThat(xssfRejectsSecondSheet("Σ", "ς")).isTrue();
        assertThat(xssfRejectsSecondSheet("İ", "i")).isTrue();

        DatasetDefinition greekUpper = TestFixtures.dataset("greekUpper");
        greekUpper.setSheetName("Σ");
        DatasetDefinition greekFinal = TestFixtures.dataset("greekFinal");
        greekFinal.setSheetName("ς");
        DatasetDefinition turkishUpper = TestFixtures.dataset("turkishUpper");
        turkishUpper.setSheetName("İ");
        DatasetDefinition latinLower = TestFixtures.dataset("latinLower");
        latinLower.setSheetName("i");

        ValidationResult result = validator.validate(TestFixtures.report(
                greekUpper, greekFinal, turkishUpper, latinLower));

        assertThat(pathsForCode(result, "CFG-DUPLICATE-SHEET-NAME"))
                .containsExactly(
                        "$.datasets[1].sheetName",
                        "$.datasets[3].sheetName");
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
    void validatesSectionTitleUsingJavaUtf16Length() {
        ReportDefinition definition = TestFixtures.report(TestFixtures.dataset("source"));
        WordSectionDefinition missing = section("missingTitle", 1, "KEEP");
        missing.setTitle(" ");
        WordSectionDefinition boundary = section("boundaryTitle", 1, "KEEP");
        boundary.setTitle(repeat("\uD83D\uDE00", 127) + "x");
        WordSectionDefinition overlong = section("overlongTitle", 1, "KEEP");
        overlong.setTitle(repeat("\uD83D\uDE00", 128));
        definition.getWord().setSections(Arrays.asList(missing, boundary, overlong));

        ValidationResult result = validator.validate(definition);

        assertThat(pathsForCode(result, "CFG-SECTION-TITLE"))
                .containsExactly("$.word.sections[0].title");
        assertThat(pathsForCode(result, "CFG-SECTION-TITLE-LENGTH"))
                .containsExactly("$.word.sections[2].title");
    }

    @Test
    void stopsWordSectionTraversalBeyondFourLevels() {
        ReportDefinition definition = TestFixtures.report(TestFixtures.dataset("source"));
        WordSectionDefinition first = section("first", 1, "KEEP");
        WordSectionDefinition second = section("second", 2, "KEEP");
        WordSectionDefinition third = section("third", 3, "KEEP");
        WordSectionDefinition fourth = section("fourth", 4, "KEEP");
        WordSectionDefinition fifth = section("fifth", 4, "KEEP");
        first.setChildren(Arrays.asList(second));
        second.setChildren(Arrays.asList(third));
        third.setChildren(Arrays.asList(fourth));
        fourth.setChildren(Arrays.asList(fifth));
        definition.getWord().setSections(Arrays.asList(first));

        ValidationResult result = validator.validate(definition);

        assertThat(result.codes()).contains("CFG-SECTION-DEPTH");
    }

    @Test
    void validationIssuesHaveValueSemantics() {
        ValidationIssue issue =
                new ValidationIssue("CFG-CODE", "$.path", "message");
        ValidationIssue same =
                new ValidationIssue("CFG-CODE", "$.path", "message");
        ValidationIssue different =
                new ValidationIssue("CFG-OTHER", "$.path", "message");

        assertThat(issue).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(issue).isNotEqualTo(different);
        assertThat(issue.toString()).isEqualTo("CFG-CODE at $.path: message");
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
    void schemaValidatesCompleteValidAndInvalidInstances() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaDocument = mapper.readTree(Paths.get(
                "src/main/resources/schema/report-definition.schema.json").toFile());
        JsonSchemaContract schema = new JsonSchemaContract(schemaDocument);
        ObjectNode valid = (ObjectNode) mapper.readTree(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.json").toFile());

        assertThat(schema.validate(valid)).isEmpty();

        ObjectNode unknownRootProperty = valid.deepCopy();
        unknownRootProperty.put("unknown", true);
        assertSchemaRejects(schema, unknownRootProperty);

        ObjectNode missingRequiredProperty = valid.deepCopy();
        missingRequiredProperty.remove("report");
        assertSchemaRejects(schema, missingRequiredProperty);

        ObjectNode bothSqlSources = valid.deepCopy();
        ((ObjectNode) bothSqlSources.path("datasets").get(0))
                .put("sql", "select 1");
        assertSchemaRejects(schema, bothSqlSources);

        ObjectNode quotedSheetName = valid.deepCopy();
        ((ObjectNode) quotedSheetName.path("datasets").get(0))
                .put("sheetName", "'invalid");
        assertSchemaRejects(schema, quotedSheetName);

        ObjectNode invalidRecursiveSection = valid.deepCopy();
        ((ObjectNode) invalidRecursiveSection.path("word").path("sections")
                .get(0).path("children").get(0))
                .put("level", 5);
        assertSchemaRejects(schema, invalidRecursiveSection);

        ObjectNode invalidComponentType = valid.deepCopy();
        ((ObjectNode) invalidComponentType.path("word").path("sections")
                .get(0).path("children").get(0).path("components").get(0))
                .put("type", "UNKNOWN");
        assertSchemaRejects(schema, invalidComponentType);

        ObjectNode invalidNarrativeType = valid.deepCopy();
        ((ObjectNode) invalidNarrativeType.path("narratives").get(0))
                .put("sourceType", "UNKNOWN");
        assertSchemaRejects(schema, invalidNarrativeType);

        ObjectNode blankSectionTitle = valid.deepCopy();
        ((ObjectNode) blankSectionTitle.path("word").path("sections").get(0))
                .put("title", " ");
        assertSchemaRejects(schema, blankSectionTitle);

        ObjectNode boundarySectionTitle = valid.deepCopy();
        ((ObjectNode) boundarySectionTitle.path("word").path("sections").get(0))
                .put("title", repeat("\uD83D\uDE00", 127) + "x");
        assertThat(schema.validate(boundarySectionTitle)).isEmpty();

        ObjectNode overlongSectionTitle = valid.deepCopy();
        ((ObjectNode) overlongSectionTitle.path("word").path("sections").get(0))
                .put("title", repeat("\uD83D\uDE00", 128));
        assertSchemaRejects(schema, overlongSectionTitle);

        ObjectNode overlongSheetName = valid.deepCopy();
        ((ObjectNode) overlongSheetName.path("datasets").get(0))
                .put("sheetName", "12345678901234567890123456789012");
        assertSchemaRejects(schema, overlongSheetName);

        JsonNode datasetSheetName = schemaDocument.path("definitions")
                .path("dataset").path("properties").path("sheetName");
        assertThat(datasetSheetName.has("maxLength")).isFalse();
        assertThat(datasetSheetName.path("x-java-maxUtf16Length").asInt())
                .isEqualTo(31);
        JsonNode sectionTitle = schemaDocument.path("definitions")
                .path("wordSection").path("properties").path("title");
        assertThat(sectionTitle.has("maxLength")).isFalse();
        assertThat(sectionTitle.path("x-java-maxUtf16Length").asInt())
                .isEqualTo(255);
        assertThat(sectionTitle.path("description").asText()).contains("UTF-16");
        assertThat(sectionTitle.path("pattern").asText()).contains("\\S");
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

    private static List<String> pathsForCode(ValidationResult result, String code) {
        return result.issues().stream()
                .filter(issue -> code.equals(issue.getCode()))
                .map(ValidationIssue::getPath)
                .collect(Collectors.toList());
    }

    private static boolean xssfRejectsSecondSheet(String first, String second)
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet(first);
            try {
                workbook.createSheet(second);
                return false;
            } catch (IllegalArgumentException expected) {
                return true;
            }
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder repeated = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            repeated.append(value);
        }
        return repeated.toString();
    }

    private static void assertSchemaRejects(
            JsonSchemaContract schema, JsonNode instance) {
        assertThat(schema.validate(instance)).isNotEmpty();
    }
}
