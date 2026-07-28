package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.chart.ChartAxis;
import com.xn.report.chart.ChartType;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.xn.report.chart.ChartModelBuilder;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    void rejectsMixedStackGroupsInSchemaValidatorAndBuilder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode reportJson = mapper.readTree(validChartJson(
                "templateNative",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"stackedColumn\","
                        + "\"stackGroup\":\"same\"},"
                        + "{\"field\":\"b\",\"name\":\"B\",\"type\":\"stackedArea\","
                        + "\"stackGroup\":\"same\"}"));
        assertThat(schema().validate(reportJson)).isNotEmpty();
        JsonNode mixedAxisJson = mapper.readTree(validChartJson(
                "templateNative",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"stackedColumn\","
                        + "\"stackGroup\":\"same\",\"axis\":\"primary\"},"
                        + "{\"field\":\"b\",\"name\":\"B\",\"type\":\"stackedColumn\","
                        + "\"stackGroup\":\"same\",\"axis\":\"secondary\"}"));
        assertThat(schema().validate(mixedAxisJson)).isNotEmpty();

        ReportDefinition report = TestFixtures.report(datasetWithFields("a", "b"));
        ChartDefinition chart = chart(
                ChartType.STACKED_COLUMN, "a", "same");
        ChartSeriesDefinition second = series(
                ChartType.STACKED_AREA, "b", "same");
        chart.setSeries(Arrays.asList(chart.getSeries().get(0), second));
        report.setCharts(Collections.singletonList(chart));
        assertThat(new ReportDefinitionValidator().validate(report).codes())
                .contains("CHART-001");
        assertThatThrownBy(() -> new ChartModelBuilder().build(
                chart, DatasetResult.list("centerEvents",
                        Collections.singletonList(DatasetRow.of(
                                "month", "01", "a", 1, "b", 2)))))
                .hasMessageContaining("same type");

        second.setType(ChartType.STACKED_COLUMN);
        second.setAxis(ChartAxis.SECONDARY);
        assertThat(new ReportDefinitionValidator().validate(report).codes())
                .contains("CHART-001");
    }

    @Test
    void rejectsStockOutsideTemplateNativeInSchemaAndValidator() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode generated = mapper.readTree(validChartJson(
                "generatedNative",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"stock\"}"));
        assertThat(schema().validate(generated)).isNotEmpty();

        ReportDefinition report = TestFixtures.report(datasetWithFields("a"));
        ChartDefinition stock = chart(ChartType.STOCK, "a", null);
        stock.setMode(ChartDefinition.Mode.IMAGE);
        report.setCharts(Collections.singletonList(stock));
        assertThat(new ReportDefinitionValidator().validate(report).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("CHART-001");
                    assertThat(issue.getMessage()).contains("TEMPLATE_NATIVE");
                });
    }

    @Test
    void categoriesAndCrossTypePropertiesHaveTheSameStrictContract()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(schema().validate(mapper.readTree(validChartJson(
                "generatedNative",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"line\"}",
                "1")))).isNotEmpty();
        assertThat(schema().validate(mapper.readTree(validChartJson(
                "generatedNative",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"line\","
                        + "\"stackGroup\":\"\"}")))).isNotEmpty();
        assertThat(schema().validate(mapper.readTree(validChartJson(
                "generatedNative",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"bubble\","
                        + "\"sizeField\":\"\"}")))).isNotEmpty();
    }

    @Test
    void stackGroupAndBubbleSizeRejectMissingNullAndBlankAtEveryLayer()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String invalidStack : Arrays.asList(
                "",
                ",\"stackGroup\":null",
                ",\"stackGroup\":\"\"")) {
            JsonNode json = mapper.readTree(validChartJson(
                    "templateNative",
                    "{\"field\":\"a\",\"name\":\"A\","
                            + "\"type\":\"stackedColumn\""
                            + invalidStack + "}"));
            assertThat(schema().validate(json)).isNotEmpty();
        }
        for (String invalidSize : Arrays.asList(
                "",
                ",\"sizeField\":null",
                ",\"sizeField\":\"\"")) {
            JsonNode json = mapper.readTree(validChartJson(
                    "templateNative",
                    "{\"field\":\"a\",\"name\":\"A\",\"type\":\"bubble\""
                            + invalidSize + "}"));
            assertThat(schema().validate(json)).isNotEmpty();
        }

        for (String value : Arrays.asList(null, "")) {
            ChartDefinition stacked = chart(
                    ChartType.STACKED_COLUMN, "a", value);
            if (value == null) {
                stacked.getSeries().get(0).setStackGroup(null);
            }
            assertRejectedByValidatorAndBuilder(stacked);

            ChartDefinition bubble = chart(ChartType.BUBBLE, "a", null);
            bubble.getSeries().get(0).setSizeField(value);
            assertRejectedByValidatorAndBuilder(bubble);
        }
    }

    @Test
    void rejectsConfiguredSeriesPropertiesThatTheRendererCannotHonor()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String seriesJson : Arrays.asList(
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"scatter\","
                        + "\"dataLabels\":\"percent\"}",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"bubble\","
                        + "\"sizeField\":\"size\",\"marker\":true}",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"radar\","
                        + "\"dataLabels\":\"value\"}",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"column\","
                        + "\"lineStyle\":\"dashed\"}",
                "{\"field\":\"a\",\"name\":\"A\",\"type\":\"pie\","
                        + "\"dataLabels\":\"value\",\"format\":\"0.0\"}")) {
            assertThat(schema().validate(mapper.readTree(
                    validChartJson("generatedNative", seriesJson))))
                    .as(seriesJson).isNotEmpty();
        }

        ChartDefinition scatter = chart(ChartType.SCATTER, "a", null);
        scatter.getSeries().get(0).setDataLabels(
                com.xn.report.chart.ChartDataLabelMode.PERCENT);
        assertRejectedByValidatorAndBuilder(scatter);

        ChartDefinition column = chart(ChartType.COLUMN, "a", null);
        column.getSeries().get(0).setLineStyle(
                com.xn.report.chart.ChartLineStyle.DASHED);
        assertRejectedByValidatorAndBuilder(column);

        ChartDefinition pie = chart(ChartType.PIE, "a", null);
        pie.getSeries().get(0).setAxis(ChartAxis.SECONDARY);
        assertRejectedByValidatorAndBuilder(pie);
    }

    private static com.xn.report.config.definition.DatasetDefinition datasetWithFields() {
        return datasetWithFields("uncertain", "certain", "baseline");
    }

    private static com.xn.report.config.definition.DatasetDefinition datasetWithFields(
            String... fields) {
        com.xn.report.config.definition.DatasetDefinition dataset =
                TestFixtures.dataset("centerEvents");
        dataset.getExpectedFields().put("month", new FieldDefinition());
        for (String field : fields) {
            dataset.getExpectedFields().put(field, new FieldDefinition());
        }
        return dataset;
    }

    private static ChartDefinition chart(
            ChartType type, String field, String stackGroup) {
        ChartDefinition chart = new ChartDefinition();
        chart.setId("chart");
        chart.setDataset("centerEvents");
        chart.setCategoryField("month");
        chart.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
        chart.setSeries(Collections.singletonList(
                series(type, field, stackGroup)));
        return chart;
    }

    private static ChartSeriesDefinition series(
            ChartType type, String field, String stackGroup) {
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField(field);
        series.setName(field);
        series.setType(type);
        if (stackGroup != null) {
            series.setStackGroup(stackGroup);
        }
        return series;
    }

    private static String validChartJson(
            String mode, String seriesJson) {
        return validChartJson(mode, seriesJson, null);
    }

    private static void assertRejectedByValidatorAndBuilder(
            ChartDefinition chart) {
        ReportDefinition report = TestFixtures.report(datasetWithFields("a"));
        report.setCharts(Collections.singletonList(chart));
        assertThat(new ReportDefinitionValidator().validate(report).codes())
                .contains("CHART-001");
        assertThatThrownBy(() -> new ChartModelBuilder().build(
                chart, DatasetResult.list("centerEvents",
                        Collections.singletonList(
                                DatasetRow.of("month", "01", "a", 1)))))
                .isInstanceOf(com.xn.report.chart.ChartBuildException.class);
    }

    private static String validChartJson(
            String mode, String seriesJson, String categoryJson) {
        return "{\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"r\",\"name\":\"R\"},"
                + "\"datasets\":[{\"id\":\"d\",\"sheetName\":\"D\","
                + "\"sql\":\"select 1\"}],"
                + "\"charts\":[{\"id\":\"c\",\"mode\":\"" + mode + "\","
                + "\"dataset\":\"d\",\"categoryField\":\"month\","
                + (categoryJson == null ? "" : "\"categories\":["
                        + categoryJson + "],")
                + "\"series\":[" + seriesJson + "]}]}";
    }

    private static JsonSchemaContract schema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = ChartConfigurationTest.class.getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            return new JsonSchemaContract(mapper.readTree(stream));
        }
    }
}
