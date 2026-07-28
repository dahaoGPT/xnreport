package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ExcelChartConfigurationTest {

    @Test
    void templateModeRequiresExactlyOneNonNullLocator() {
        ReportDefinition report =
                TestFixtures.report(TestFixtures.dataset("centerEvents"));
        ChartDefinition chart = TestFixtures.comboChartDefinition();
        chart.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
        chart.setExcelSheet("中心-每月");
        report.setCharts(Collections.singletonList(chart));

        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("CHART-002");
                    assertThat(issue.getPath()).contains("templateChart");
                });

        chart.setTemplateChartMarker(null);
        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .anySatisfy(issue -> assertThat(issue.getPath())
                        .endsWith(".templateChartMarker"));

        chart.setTemplateChartMarker("REPORT_CHART:centerEventChart");
        chart.setTemplateChartIndex(0);
        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .anySatisfy(issue -> assertThat(issue.getMessage())
                        .contains("exactly one"));
    }

    @Test
    void generatedModeRejectsTemplateLocatorAndInvalidAnchor() {
        ReportDefinition report =
                TestFixtures.report(TestFixtures.dataset("centerEvents"));
        ChartDefinition chart = TestFixtures.comboChartDefinition();
        chart.setMode(ChartDefinition.Mode.GENERATED_NATIVE);
        chart.setTemplateChartIndex(0);
        chart.setAnchorWidthColumns(0);
        report.setCharts(Collections.singletonList(chart));

        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .extracting(ValidationIssue::getPath)
                .contains("$.charts[0].templateChartIndex",
                        "$.charts[0].anchorWidthColumns");
    }

    @Test
    void schemaExposesExcelModeLocatorAndAnchorContract()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schema;
        try (InputStream input = getClass().getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            schema = mapper.readTree(input);
        }
        JsonSchemaContract contract = new JsonSchemaContract(schema);
        JsonNode invalid = mapper.readTree("{"
                + "\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"r\",\"name\":\"R\"},"
                + "\"datasets\":[{\"id\":\"d\",\"sheetName\":\"D\","
                + "\"sql\":\"select 1\"}],"
                + "\"charts\":[{"
                + "\"id\":\"c\",\"dataset\":\"d\","
                + "\"categoryField\":\"x\","
                + "\"mode\":\"GENERATED_NATIVE\","
                + "\"templateChartMarker\":\"REPORT_CHART:c\","
                + "\"series\":[{\"field\":\"y\",\"name\":\"Y\","
                + "\"type\":\"LINE\"}]}]}");

        assertThat(contract.validate(invalid)).isNotEmpty();
    }

    @Test
    void loadsAndValidatesOneTemplateLocatorPerDeclaredGroup()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(
                com.fasterxml.jackson.databind.DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES);
        ReportDefinition report = mapper.readValue(groupedReportJson(
                "[{\"groupKey\":\"A\",\"marker\":\"REPORT_CHART:a\"},"
                        + "{\"groupKey\":\"B\",\"index\":1}]"),
                ReportDefinition.class);

        Method getter = ChartDefinition.class.getMethod(
                "getTemplateChartLocators");
        List<?> locators = (List<?>) getter.invoke(report.getCharts().get(0));
        assertThat(locators).hasSize(2);
        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .isEmpty();
    }

    @Test
    void rejectsGroupedTemplateLocatorDuplicatesAndLegacyMixing()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ReportDefinition duplicates = mapper.readValue(groupedReportJson(
                "[{\"groupKey\":\"A\",\"marker\":\"REPORT_CHART:a\"},"
                        + "{\"groupKey\":\"A\",\"index\":1}]"),
                ReportDefinition.class);
        assertThat(new ReportDefinitionValidator()
                .validate(duplicates).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getPath())
                            .contains("templateChartLocators");
                    assertThat(issue.getMessage()).contains("groupKey");
                });

        ReportDefinition mixed = mapper.readValue(
                groupedReportJson(
                        "[{\"groupKey\":\"A\","
                                + "\"marker\":\"REPORT_CHART:a\"}]")
                        .replace("\"templateChartLocators\"",
                                "\"templateChartMarker\":"
                                        + "\"REPORT_CHART:legacy\","
                                        + "\"templateChartLocators\""),
                ReportDefinition.class);
        assertThat(new ReportDefinitionValidator().validate(mixed).issues())
                .anySatisfy(issue -> assertThat(issue.getMessage())
                        .contains("must not be combined"));
    }

    @Test
    void rejectsDuplicateGroupedMarkersAndIndexesAtSecondLocator()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ReportDefinition duplicateMarkers = mapper.readValue(
                groupedReportJson(
                        "[{\"groupKey\":\"A\","
                                + "\"marker\":\"REPORT_CHART:same\"},"
                                + "{\"groupKey\":\"B\","
                                + "\"marker\":\"REPORT_CHART:same\"}]"),
                ReportDefinition.class);
        assertThat(new ReportDefinitionValidator()
                .validate(duplicateMarkers).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getPath()).isEqualTo(
                            "$.charts[0].templateChartLocators[1].marker");
                    assertThat(issue.getMessage())
                            .contains("marker")
                            .contains("unique");
                });

        ReportDefinition duplicateIndexes = mapper.readValue(
                groupedReportJson(
                        "[{\"groupKey\":\"A\",\"index\":0},"
                                + "{\"groupKey\":\"B\",\"index\":0}]"),
                ReportDefinition.class);
        assertThat(new ReportDefinitionValidator()
                .validate(duplicateIndexes).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getPath()).isEqualTo(
                            "$.charts[0].templateChartLocators[1].index");
                    assertThat(issue.getMessage())
                            .contains("index")
                            .contains("unique");
                });
    }

    @Test
    void rejectsGroupedLocatorMissingBothFormsAtMarkerPath()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ReportDefinition missing = mapper.readValue(
                groupedReportJson("[{\"groupKey\":\"A\"}]"),
                ReportDefinition.class);

        assertThat(new ReportDefinitionValidator()
                .validate(missing).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getPath()).isEqualTo(
                            "$.charts[0].templateChartLocators[0].marker");
                    assertThat(issue.getMessage())
                            .contains("marker or index");
                });
    }

    @Test
    void schemaRejectsGroupedLocatorWithoutExactlyOneMarkerOrIndex()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schema;
        try (InputStream input = getClass().getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            schema = mapper.readTree(input);
        }
        JsonSchemaContract contract = new JsonSchemaContract(schema);
        JsonNode invalid = mapper.readTree(groupedReportJson(
                "[{\"groupKey\":\"A\","
                        + "\"marker\":\"REPORT_CHART:a\",\"index\":0}]"));

        assertThat(contract.validate(invalid)).isNotEmpty();
    }

    private static String groupedReportJson(String locators) {
        return "{"
                + "\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"r\",\"name\":\"R\"},"
                + "\"datasets\":[{\"id\":\"d\",\"sheetName\":\"D\","
                + "\"sql\":\"select 1\"}],"
                + "\"charts\":[{"
                + "\"id\":\"c\",\"dataset\":\"d\","
                + "\"excelSheet\":\"D\","
                + "\"categoryField\":\"month\","
                + "\"groupByField\":\"groupName\","
                + "\"mode\":\"TEMPLATE_NATIVE\","
                + "\"templateChartLocators\":" + locators + ","
                + "\"series\":[{\"field\":\"value\",\"name\":\"Value\","
                + "\"type\":\"LINE\"}]}]}";
    }
}
