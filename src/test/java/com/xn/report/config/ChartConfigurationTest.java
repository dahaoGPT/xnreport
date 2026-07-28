package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.chart.ChartAxis;
import com.xn.report.chart.ChartType;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ChartConfigurationTest {

    @Test
    void loadsStronglyTypedChartConfiguration() {
        Path path = Paths.get("src/test/resources/fixtures/configs/chart-report.yml");

        ReportDefinition definition = ReportDefinitionLoader.createDefault().load(path);

        ChartDefinition chart = definition.getCharts().get(0);
        assertThat(chart.getMode()).isEqualTo(ChartDefinition.Mode.TEMPLATE_NATIVE);
        assertThat(chart.getSeries().get(0).getType())
                .isEqualTo(ChartType.STACKED_COLUMN);
        assertThat(chart.getSeries().get(2).getAxis()).isEqualTo(ChartAxis.SECONDARY);
    }

    @Test
    void schemaAndRuntimeBothRejectCrossTypeOrExplicitNullFields() throws Exception {
        JsonSchemaContract schema = schema();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode invalid = mapper.readTree("{"
                + "\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"r\",\"name\":\"R\"},"
                + "\"datasets\":[{\"id\":\"d\",\"sheetName\":\"D\",\"sql\":\"select 1\"}],"
                + "\"charts\":[{\"id\":\"c\",\"dataset\":\"d\","
                + "\"categoryField\":\"x\",\"series\":[{"
                + "\"field\":\"y\",\"name\":\"Y\",\"type\":\"LINE\","
                + "\"stackGroup\":\"bad\"}]}]}");
        assertThat(schema.validate(invalid)).isNotEmpty();

        ReportDefinition report = TestFixtures.report(datasetWithFields());
        ChartDefinition chart = TestFixtures.comboChartDefinition();
        chart.getSeries().get(0).setColor(null);
        report.setCharts(java.util.Collections.singletonList(chart));

        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("CHART-001");
                    assertThat(issue.getPath()).contains("color");
                });
    }

    @Test
    void validatorPrechecksFieldsWhenExpectedFieldsAreKnown() {
        ReportDefinition report = TestFixtures.report(datasetWithFields());
        ChartDefinition chart = TestFixtures.comboChartDefinition();
        chart.getSeries().get(0).setField("doesNotExist");
        report.setCharts(java.util.Collections.singletonList(chart));

        ValidationResult result = new ReportDefinitionValidator().validate(report);

        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.getCode()).isEqualTo("CHART-001");
            assertThat(issue.getMessage()).contains("doesNotExist");
        });
    }

    private static com.xn.report.config.definition.DatasetDefinition datasetWithFields() {
        com.xn.report.config.definition.DatasetDefinition dataset =
                TestFixtures.dataset("centerEvents");
        dataset.getExpectedFields().put("month", new FieldDefinition());
        dataset.getExpectedFields().put("uncertain", new FieldDefinition());
        dataset.getExpectedFields().put("certain", new FieldDefinition());
        dataset.getExpectedFields().put("baseline", new FieldDefinition());
        return dataset;
    }

    private static JsonSchemaContract schema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = ChartConfigurationTest.class.getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            return new JsonSchemaContract(mapper.readTree(stream));
        }
    }
}
