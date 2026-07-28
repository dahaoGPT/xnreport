package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.support.JsonSchemaContract;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsExcelValueAndTableBindingsFromYaml() {
        String yaml = "schemaVersion: '1.0'\n"
                + "report: { code: r, name: R }\n"
                + "datasets:\n"
                + "  - id: center\n"
                + "    sheetName: 中心-每月\n"
                + "    sql: SELECT 1\n"
                + "    resultType: LIST\n"
                + "    expectedFields:\n"
                + "      month: { type: STRING }\n"
                + "      hours: { type: DECIMAL }\n"
                + "excel:\n"
                + "  valueBindings:\n"
                + "    - { sheet: 报表首页, cell: B3, value: '${runtime.period}', format: yyyy-mm }\n"
                + "  tableBindings:\n"
                + "    - dataset: center\n"
                + "      sheet: 中心-每月\n"
                + "      table: tbl_center_monthly\n"
                + "      startRow: 2\n"
                + "      columns:\n"
                + "        - { field: month, header: 月份 }\n"
                + "        - { field: hours, header: 耗时 }\n";

        ReportDefinition definition = loadYaml(yaml);

        assertThat(definition.getExcel().getValueBindings()).hasSize(1);
        assertThat(definition.getExcel().getValueBindings().get(0).getCell())
                .isEqualTo("B3");
        ExcelTableBinding table =
                definition.getExcel().getTableBindings().get(0);
        assertThat(table.getDataset()).isEqualTo("center");
        assertThat(table.getStartRow()).isEqualTo(2);
        assertThat(table.getColumns()).extracting("field")
                .containsExactly("month", "hours");
    }

    @Test
    void validatorRejectsBindingThatDoesNotMatchDatasetSheetOrFields() {
        String yaml = "schemaVersion: '1.0'\n"
                + "report: { code: r, name: R }\n"
                + "datasets:\n"
                + "  - id: center\n"
                + "    sheetName: 中心-每月\n"
                + "    sql: SELECT 1\n"
                + "    resultType: LIST\n"
                + "    expectedFields:\n"
                + "      month: { type: STRING }\n"
                + "excel:\n"
                + "  tableBindings:\n"
                + "    - dataset: center\n"
                + "      sheet: 错误页\n"
                + "      table: A1\n"
                + "      columns:\n"
                + "        - { field: missing, header: 缺失 }\n";
        ReportDefinition definition = loadYaml(yaml);

        List<ValidationIssue> issues =
                new ReportDefinitionValidator().validate(definition)
                        .issues();

        assertThat(issues).extracting(ValidationIssue::getPath)
                .contains(
                        "$.excel.tableBindings[0].sheet",
                        "$.excel.tableBindings[0].table",
                        "$.excel.tableBindings[0].columns[0].field");
    }

    @Test
    void jsonSchemaAcceptsExcelBindingsAndRejectsUnknownProperties()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode valid = mapper.readTree("{"
                + "\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"r\",\"name\":\"R\"},"
                + "\"datasets\":[{\"id\":\"center\",\"sheetName\":\"中心\","
                + "\"sql\":\"SELECT 1\",\"resultType\":\"LIST\"}],"
                + "\"excel\":{\"tableBindings\":[{"
                + "\"dataset\":\"center\",\"sheet\":\"中心\","
                + "\"table\":\"tbl_center\",\"startRow\":0,"
                + "\"columns\":[{\"field\":\"value\",\"header\":\"值\"}]}]}}");
        JsonNode invalid = mapper.readTree("{"
                + "\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"r\",\"name\":\"R\"},"
                + "\"datasets\":[{\"id\":\"center\",\"sheetName\":\"中心\","
                + "\"sql\":\"SELECT 1\"}],"
                + "\"excel\":{\"mystery\":true}}");
        JsonSchemaContract schema = schema();

        assertThat(schema.validate(valid)).isEmpty();
        assertThat(schema.validate(invalid))
                .anyMatch(message -> message.contains(
                        "$.excel has unknown property mystery"));
    }

    @Test
    void runtimeValidatorRejectsExplicitNullExcelConfiguration() {
        ReportDefinition definition = loadYaml(
                "schemaVersion: '1.0'\n"
                        + "report: { code: r, name: R }\n"
                        + "datasets:\n"
                        + "  - { id: center, sheetName: 中心, sql: SELECT 1 }\n"
                        + "excel: null\n");

        assertThat(new ReportDefinitionValidator()
                .validate(definition).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("EXCEL-001");
                    assertThat(issue.getPath()).isEqualTo("$.excel");
                });
    }

    private ReportDefinition loadYaml(String yaml) {
        try {
            Path path = tempDir.resolve("report.yml");
            Files.write(path, yaml.getBytes(StandardCharsets.UTF_8));
            return ReportDefinitionLoader.createDefault().load(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JsonSchemaContract schema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream =
                ExcelConfigurationTest.class.getResourceAsStream(
                        "/schema/report-definition.schema.json")) {
            return new JsonSchemaContract(mapper.readTree(stream));
        }
    }
}
