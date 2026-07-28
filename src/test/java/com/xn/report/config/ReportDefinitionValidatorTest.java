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
import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformOperator;
import com.xn.report.config.definition.TransformType;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import com.xn.report.dataset.DatasetType;
import com.xn.report.transform.Direction;
import com.xn.report.transform.DivideByZeroStrategy;
import com.xn.report.transform.NullOrder;
import com.xn.report.transform.TransformFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
                "TEXT-001");
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
    void validatesEveryDatasetTransformAndReportsAllProblems() {
        DatasetDefinition dataset = TestFixtures.dataset("source");
        dataset.setResultType(DatasetType.SCALAR);

        TransformDefinition missingType = new TransformDefinition();

        TransformDefinition filter = new TransformDefinition();
        filter.setType(TransformType.FILTER);
        filter.setField(" ");
        filter.setOperator(TransformOperator.GREATER_THAN);
        filter.setLimit(1);
        filter.setSourceField("wrongForFilter");

        TransformDefinition sort = new TransformDefinition();
        sort.setType(TransformType.SORT);
        sort.setSortFields(Arrays.asList(new SortFieldDefinition()));

        TransformDefinition distinct = new TransformDefinition();
        distinct.setType(TransformType.DISTINCT);

        TransformDefinition limit = new TransformDefinition();
        limit.setType(TransformType.LIMIT);
        limit.setLimit(-1);

        TransformDefinition derived = new TransformDefinition();
        derived.setType(TransformType.DERIVED_FIELD);
        derived.setTargetField("Result");
        derived.setSourceField(" ");
        derived.setScale(-1);
        derived.setDivideByZeroStrategy(DivideByZeroStrategy.DEFAULT_VALUE);

        TransformDefinition duplicateDerived = new TransformDefinition();
        duplicateDerived.setType(TransformType.DERIVED_FIELD);
        duplicateDerived.setTargetField("result");
        duplicateDerived.setSourceField("value");
        duplicateDerived.setOperator(TransformOperator.ADD);
        duplicateDerived.setOperand(BigDecimal.ONE);

        dataset.setTransforms(Arrays.asList(
                missingType, filter, sort, distinct, limit, derived, duplicateDerived));

        ValidationResult result = validator.validate(TestFixtures.report(dataset));

        assertThat(result.codes()).contains(
                "CFG-TRANSFORM-TYPE",
                "CFG-TRANSFORM-FIELD",
                "CFG-TRANSFORM-VALUE",
                "CFG-TRANSFORM-ATTRIBUTE",
                "CFG-TRANSFORM-SORT-FIELD",
                "CFG-TRANSFORM-DIRECTION",
                "CFG-TRANSFORM-NULL-ORDER",
                "CFG-TRANSFORM-DISTINCT-FIELDS",
                "CFG-TRANSFORM-LIMIT",
                "CFG-TRANSFORM-SOURCE-FIELD",
                "CFG-TRANSFORM-OPERATOR",
                "CFG-TRANSFORM-OPERAND",
                "CFG-TRANSFORM-SCALE",
                "CFG-TRANSFORM-DIVIDE-DEFAULT",
                "CFG-TRANSFORM-DUPLICATE-TARGET",
                "CFG-TRANSFORM-DATASET-TYPE");
    }

    @Test
    void acceptsCompleteStronglyTypedTransformDefinitions() {
        DatasetDefinition dataset = TestFixtures.dataset("source");

        TransformDefinition filter = new TransformDefinition();
        filter.setType(TransformType.FILTER);
        filter.setField("avgHours");
        filter.setOperator(TransformOperator.GREATER_THAN);
        filter.setValue(new BigDecimal("5"));

        SortFieldDefinition sortField = new SortFieldDefinition();
        sortField.setField("avgHours");
        sortField.setDirection(Direction.DESC);
        sortField.setNullOrder(NullOrder.LAST);
        TransformDefinition sort = new TransformDefinition();
        sort.setType(TransformType.SORT);
        sort.setSortFields(Arrays.asList(sortField));

        TransformDefinition distinct = new TransformDefinition();
        distinct.setType(TransformType.DISTINCT);
        distinct.setFields(Arrays.asList("personName"));

        TransformDefinition limit = new TransformDefinition();
        limit.setType(TransformType.LIMIT);
        limit.setLimit(10);

        TransformDefinition derived = new TransformDefinition();
        derived.setType(TransformType.DERIVED_FIELD);
        derived.setTargetField("overHours");
        derived.setSourceField("avgHours");
        derived.setOperator(TransformOperator.SUBTRACT);
        derived.setOperand(new BigDecimal("5"));
        derived.setScale(2);

        dataset.setTransforms(Arrays.asList(filter, sort, distinct, limit, derived));

        ValidationResult result = validator.validate(TestFixtures.report(dataset));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void loaderTracksExplicitNullForValidatorAndFactoryAcrossYamlAndJson(
            @TempDir Path temporaryDirectory) throws Exception {
        assertExplicitNullRejected(
                temporaryDirectory,
                "comparison-value",
                "      - type: FILTER\n"
                        + "        field: value\n"
                        + "        operator: EQUAL\n"
                        + "        value: null\n",
                "{\"type\":\"FILTER\",\"field\":\"value\","
                        + "\"operator\":\"EQUAL\",\"value\":null}",
                "value",
                "CFG-TRANSFORM-VALUE");
        assertExplicitNullRejected(
                temporaryDirectory,
                "filter-limit",
                "      - type: FILTER\n"
                        + "        field: value\n"
                        + "        operator: EQUAL\n"
                        + "        value: 1\n"
                        + "        limit: null\n",
                "{\"type\":\"FILTER\",\"field\":\"value\","
                        + "\"operator\":\"EQUAL\",\"value\":1,\"limit\":null}",
                "limit",
                "CFG-TRANSFORM-ATTRIBUTE");
        assertExplicitNullRejected(
                temporaryDirectory,
                "null-operator-value",
                "      - type: FILTER\n"
                        + "        field: value\n"
                        + "        operator: IS_NULL\n"
                        + "        value: null\n",
                "{\"type\":\"FILTER\",\"field\":\"value\","
                        + "\"operator\":\"IS_NULL\",\"value\":null}",
                "value",
                "CFG-TRANSFORM-ATTRIBUTE");
    }

    @ParameterizedTest(name = "{0} rejects explicit null property {1}")
    @MethodSource("transformPropertyMatrix")
    void schemaValidatorAndFactoryRejectExplicitNullForEveryTransformProperty(
            TransformType type,
            String property,
            @TempDir Path temporaryDirectory) throws Exception {
        assertCompleteJsonTransformRejected(
                temporaryDirectory,
                type.name() + "-" + property,
                type,
                property,
                validTransformJson(type));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("derivedConditionalExplicitNullCases")
    void derivedConditionalPropertiesRejectExplicitNull(
            String name,
            String property,
            String transformJson,
            @TempDir Path temporaryDirectory) throws Exception {
        assertCompleteJsonTransformRejected(
                temporaryDirectory,
                name,
                TransformType.DERIVED_FIELD,
                property,
                transformJson);
    }

    @Test
    void schemaValidatesCompleteValidAndInvalidInstances() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaDocument = mapper.readTree(Paths.get(
                "src/main/resources/schema/report-definition.schema.json").toFile());
        JsonSchemaContract schema = new JsonSchemaContract(schemaDocument);
        ObjectNode valid = (ObjectNode) mapper.readTree(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.json").toFile());
        ObjectNode validDataset = (ObjectNode) valid.path("datasets").get(0);
        validDataset.set("transforms", mapper.readTree("["
                + "{\"type\":\"FILTER\",\"field\":\"avgHours\","
                + "\"operator\":\"GREATER_THAN\",\"value\":5},"
                + "{\"type\":\"SORT\",\"sortFields\":["
                + "{\"field\":\"team\",\"direction\":\"ASC\",\"nullOrder\":\"FIRST\"},"
                + "{\"field\":\"avgHours\",\"direction\":\"DESC\",\"nullOrder\":\"LAST\"}]},"
                + "{\"type\":\"DISTINCT\",\"fields\":[\"personName\",\"team\"]},"
                + "{\"type\":\"LIMIT\",\"limit\":10},"
                + "{\"type\":\"DERIVED_FIELD\",\"targetField\":\"overHours\","
                + "\"sourceField\":\"avgHours\",\"operator\":\"SUBTRACT\","
                + "\"operand\":5,\"scale\":2,"
                + "\"fieldConflictStrategy\":\"REPLACE\"}"
                + "]"));

        assertThat(schema.validate(valid)).isEmpty();
        ReportDefinition parsedValid =
                mapper.treeToValue(valid, ReportDefinition.class);
        assertThat(validator.validate(parsedValid).isValid()).isTrue();

        ObjectNode nullFilter = valid.deepCopy();
        ObjectNode nullFilterDefinition = (ObjectNode) nullFilter.path("datasets")
                .get(0).path("transforms").get(0);
        nullFilterDefinition.put("operator", "IS_NULL");
        nullFilterDefinition.remove("value");
        assertThat(schema.validate(nullFilter)).isEmpty();
        assertThat(validator.validate(
                mapper.treeToValue(nullFilter, ReportDefinition.class)).isValid())
                .isTrue();

        ObjectNode nullFilterWithValue = nullFilter.deepCopy();
        ((ObjectNode) nullFilterWithValue.path("datasets").get(0)
                .path("transforms").get(0)).put("value", 5);
        assertSchemaRejects(schema, nullFilterWithValue);
        assertThat(validator.validate(mapper.treeToValue(
                nullFilterWithValue, ReportDefinition.class)).codes())
                .contains("CFG-TRANSFORM-ATTRIBUTE");

        ObjectNode comparisonWithoutValue = valid.deepCopy();
        ((ObjectNode) comparisonWithoutValue.path("datasets").get(0)
                .path("transforms").get(0)).remove("value");
        assertSchemaRejects(schema, comparisonWithoutValue);
        assertThat(validator.validate(mapper.treeToValue(
                comparisonWithoutValue, ReportDefinition.class)).codes())
                .contains("CFG-TRANSFORM-VALUE");

        ObjectNode comparisonWithNullValue = valid.deepCopy();
        ((ObjectNode) comparisonWithNullValue.path("datasets").get(0)
                .path("transforms").get(0)).putNull("value");
        assertSchemaRejects(schema, comparisonWithNullValue);

        ObjectNode filterWithNullLimit = valid.deepCopy();
        ((ObjectNode) filterWithNullLimit.path("datasets").get(0)
                .path("transforms").get(0)).putNull("limit");
        assertSchemaRejects(schema, filterWithNullLimit);

        ObjectNode nullFilterWithNullValue = nullFilter.deepCopy();
        ((ObjectNode) nullFilterWithNullValue.path("datasets").get(0)
                .path("transforms").get(0)).putNull("value");
        assertSchemaRejects(schema, nullFilterWithNullValue);

        ObjectNode defaultWithoutValue = valid.deepCopy();
        ObjectNode defaultDerived = (ObjectNode) defaultWithoutValue.path("datasets")
                .get(0).path("transforms").get(4);
        defaultDerived.put("operator", "DIVIDE");
        defaultDerived.put("divideByZeroStrategy", "DEFAULT_VALUE");
        defaultDerived.remove("divideByZeroDefault");
        assertSchemaRejects(schema, defaultWithoutValue);
        assertThat(validator.validate(mapper.treeToValue(
                defaultWithoutValue, ReportDefinition.class)).codes())
                .contains("CFG-TRANSFORM-DIVIDE-DEFAULT");

        ObjectNode filterWithCrossTypeAttributes = valid.deepCopy();
        ObjectNode crossTypeFilter = (ObjectNode) filterWithCrossTypeAttributes
                .path("datasets").get(0).path("transforms").get(0);
        crossTypeFilter.put("limit", 1);
        crossTypeFilter.put("sourceField", "wrong");
        assertSchemaRejects(schema, filterWithCrossTypeAttributes);
        assertThat(validator.validate(mapper.treeToValue(
                filterWithCrossTypeAttributes, ReportDefinition.class)).codes())
                .contains("CFG-TRANSFORM-ATTRIBUTE");

        ObjectNode unknownTransformType = valid.deepCopy();
        ((ObjectNode) unknownTransformType.path("datasets").get(0)
                .path("transforms").get(0)).put("type", "UNKNOWN");
        assertSchemaRejects(schema, unknownTransformType);

        ObjectNode missingFilterField = valid.deepCopy();
        ((ObjectNode) missingFilterField.path("datasets").get(0)
                .path("transforms").get(0)).remove("field");
        assertSchemaRejects(schema, missingFilterField);

        ObjectNode emptySortFields = valid.deepCopy();
        ((ObjectNode) emptySortFields.path("datasets").get(0)
                .path("transforms").get(1)).set(
                        "sortFields", mapper.createArrayNode());
        assertSchemaRejects(schema, emptySortFields);

        ObjectNode unknownSortProperty = valid.deepCopy();
        ((ObjectNode) unknownSortProperty.path("datasets").get(0)
                .path("transforms").get(1).path("sortFields").get(0))
                .put("unknown", true);
        assertSchemaRejects(schema, unknownSortProperty);

        ObjectNode negativeLimit = valid.deepCopy();
        ((ObjectNode) negativeLimit.path("datasets").get(0)
                .path("transforms").get(3)).put("limit", -1);
        assertSchemaRejects(schema, negativeLimit);

        ObjectNode unknownArithmeticOperator = valid.deepCopy();
        ((ObjectNode) unknownArithmeticOperator.path("datasets").get(0)
                .path("transforms").get(4)).put("operator", "POWER");
        assertSchemaRejects(schema, unknownArithmeticOperator);

        ObjectNode unknownTransformProperty = valid.deepCopy();
        ((ObjectNode) unknownTransformProperty.path("datasets").get(0)
                .path("transforms").get(4)).put("script", "arbitrary()");
        assertSchemaRejects(schema, unknownTransformProperty);

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

    private static Stream<Arguments> transformPropertyMatrix() {
        List<String> properties = Arrays.asList(
                "type",
                "field",
                "fields",
                "sortFields",
                "operator",
                "value",
                "sourceField",
                "targetField",
                "operand",
                "limit",
                "scale",
                "divideByZeroStrategy",
                "divideByZeroDefault",
                "fieldConflictStrategy");
        return Arrays.stream(TransformType.values())
                .flatMap(type -> properties.stream()
                        .map(property -> Arguments.of(type, property)));
    }

    private static Stream<Arguments> derivedConditionalExplicitNullCases() {
        return Stream.of(
                Arguments.of(
                        "derived-scale-null",
                        "scale",
                        "{\"type\":\"DERIVED_FIELD\",\"sourceField\":\"value\","
                                + "\"targetField\":\"result\",\"operator\":\"ADD\","
                                + "\"operand\":1,\"scale\":null}"),
                Arguments.of(
                        "derived-add-strategy-null",
                        "divideByZeroStrategy",
                        "{\"type\":\"DERIVED_FIELD\",\"sourceField\":\"value\","
                                + "\"targetField\":\"result\",\"operator\":\"ADD\","
                                + "\"operand\":1,\"divideByZeroStrategy\":null}"),
                Arguments.of(
                        "derived-non-default-default-null",
                        "divideByZeroDefault",
                        "{\"type\":\"DERIVED_FIELD\",\"sourceField\":\"value\","
                                + "\"targetField\":\"result\",\"operator\":\"DIVIDE\","
                                + "\"operand\":1,\"divideByZeroStrategy\":\"FAIL\","
                                + "\"divideByZeroDefault\":null}"));
    }

    private static String validTransformJson(TransformType type) {
        switch (type) {
            case FILTER:
                return "{\"type\":\"FILTER\",\"field\":\"value\","
                        + "\"operator\":\"EQUAL\",\"value\":1}";
            case SORT:
                return "{\"type\":\"SORT\",\"sortFields\":[{\"field\":\"value\","
                        + "\"direction\":\"ASC\",\"nullOrder\":\"LAST\"}]}";
            case DISTINCT:
                return "{\"type\":\"DISTINCT\",\"fields\":[\"value\"]}";
            case LIMIT:
                return "{\"type\":\"LIMIT\",\"limit\":1}";
            case DERIVED_FIELD:
                return "{\"type\":\"DERIVED_FIELD\",\"sourceField\":\"value\","
                        + "\"targetField\":\"result\",\"operator\":\"ADD\","
                        + "\"operand\":1}";
            default:
                throw new IllegalArgumentException("Unsupported transform type: " + type);
        }
    }

    private void assertCompleteJsonTransformRejected(
            Path temporaryDirectory,
            String name,
            TransformType expectedType,
            String property,
            String transformJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode report = (ObjectNode) mapper.readTree(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.json").toFile());
        ObjectNode transform = (ObjectNode) mapper.readTree(transformJson);
        transform.putNull(property);
        ((ObjectNode) report.path("datasets").get(0)).set(
                "transforms", mapper.createArrayNode().add(transform));

        JsonNode schemaDocument = mapper.readTree(Paths.get(
                "src/main/resources/schema/report-definition.schema.json").toFile());
        assertSchemaRejects(new JsonSchemaContract(schemaDocument), report);

        Path path = temporaryDirectory.resolve(name + ".json");
        Files.write(path, mapper.writeValueAsBytes(report));
        ReportDefinition loaded = ReportDefinitionLoader.createDefault().load(path);
        TransformDefinition definition =
                loaded.getDatasets().get(0).getTransforms().get(0);
        assertThat(definition.hasProperty(property)).isTrue();
        if (!"type".equals(property)) {
            assertThat(definition.getType()).isEqualTo(expectedType);
        }
        assertThat(validator.validate(loaded).isValid()).isFalse();
        assertThatThrownBy(() -> new TransformFactory().create(definition))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WordSectionDefinition section(String id, int level, String emptyStrategy) {
        WordSectionDefinition section = new WordSectionDefinition();
        section.setId(id);
        section.setTitle(id);
        section.setLevel(level);
        section.setEmptyStrategy(emptyStrategy);
        return section;
    }

    private void assertExplicitNullRejected(
            Path temporaryDirectory,
            String name,
            String yamlTransform,
            String jsonTransform,
            String presentProperty,
            String expectedCode) throws Exception {
        String yaml = "schemaVersion: \"1.0\"\n"
                + "report:\n"
                + "  code: explicit-null\n"
                + "  name: Explicit Null\n"
                + "datasets:\n"
                + "  - id: source\n"
                + "    sheetName: Source\n"
                + "    sqlFile: source.sql\n"
                + "    resultType: LIST\n"
                + "    transforms:\n"
                + yamlTransform;
        String json = "{\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"explicit-null\","
                + "\"name\":\"Explicit Null\"},"
                + "\"datasets\":[{\"id\":\"source\",\"sheetName\":\"Source\","
                + "\"sqlFile\":\"source.sql\",\"resultType\":\"LIST\","
                + "\"transforms\":[" + jsonTransform + "]}]}";
        Path yamlPath = temporaryDirectory.resolve(name + ".yml");
        Path jsonPath = temporaryDirectory.resolve(name + ".json");
        Files.write(yamlPath, yaml.getBytes(StandardCharsets.UTF_8));
        Files.write(jsonPath, json.getBytes(StandardCharsets.UTF_8));

        ReportDefinitionLoader loader = ReportDefinitionLoader.createDefault();
        for (Path path : Arrays.asList(yamlPath, jsonPath)) {
            ReportDefinition loaded = loader.load(path);
            TransformDefinition transform =
                    loaded.getDatasets().get(0).getTransforms().get(0);
            assertThat(transform.hasProperty(presentProperty)).isTrue();
            assertThat(validator.validate(loaded).codes()).contains(expectedCode);
            assertThatThrownBy(() -> new TransformFactory().create(transform))
                    .isInstanceOf(IllegalArgumentException.class);
        }
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
        try {
            narrative.setSourceType(
                    NarrativeDefinition.SourceType.valueOf(sourceType));
            if (narrative.getSourceType()
                    == NarrativeDefinition.SourceType.RULE_GENERATED) {
                narrative.setAnalyzer("testAnalyzer");
                narrative.setAnalyzerType(
                        NarrativeDefinition.AnalyzerType.DISTRIBUTION);
                narrative.setDataset("source");
                narrative.setSentence("test sentence");
                DistributionDefinition distribution =
                        new DistributionDefinition();
                distribution.setField("value");
                BinDefinition all = new BinDefinition();
                all.setId("all");
                all.setLabel("All");
                distribution.setBins(Collections.singletonList(all));
                distribution.setLabelMode(
                        DistributionDefinition.LabelMode.COUNT);
                narrative.setDistribution(distribution);
            } else {
                narrative.setTemplate("test template");
            }
        } catch (IllegalArgumentException exception) {
            narrative.setSourceType(null);
        }
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
        bin.setLabel(id);
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
