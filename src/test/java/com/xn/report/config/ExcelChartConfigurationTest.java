package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.io.InputStream;
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
}
