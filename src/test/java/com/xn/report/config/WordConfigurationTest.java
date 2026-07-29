package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class WordConfigurationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void schemaRequiresAllFiveNonBlankCoverValues() throws Exception {
        JsonSchemaContract schema = schema();
        JsonNode valid = mapper.readTree(reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\""));
        JsonNode missing = mapper.readTree(reportWithCover(
                "\"title\":\"研发效能报告\""));
        JsonNode blank = mapper.readTree(reportWithCover(
                "\"title\":\" \","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\""));
        JsonNode noUpdate = mapper.readTree(reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"")
                .replace("\"updateOnOpen\":true", "\"updateOnOpen\":false"));

        assertThat(schema.validate(valid)).isEmpty();
        assertThat(schema.validate(missing)).isNotEmpty();
        assertThat(schema.validate(blank)).isNotEmpty();
        assertThat(schema.validate(noUpdate)).isNotEmpty();
    }

    @Test
    void runtimeValidatorReportsIncompleteCoverAndNonUpdatingEnabledToc() {
        ReportDefinition definition =
                TestFixtures.report(TestFixtures.dataset("source"));
        definition.getReport().setWordTemplate("report-template.docx");
        definition.getWord().getCover().setTitle("研发效能报告");
        definition.getWord().getToc().setEnabled(true);
        definition.getWord().getToc().setUpdateOnOpen(false);

        ValidationResult result = new ReportDefinitionValidator().validate(definition);

        assertThat(result.codes())
                .contains("CFG-WORD-COVER", "CFG-TOC-UPDATE");
    }

    private JsonSchemaContract schema() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/schema/report-definition.schema.json")) {
            return new JsonSchemaContract(mapper.readTree(input));
        }
    }

    private static String reportWithCover(String cover) {
        return "{\"schemaVersion\":\"1.0\","
                + "\"report\":{\"code\":\"word\",\"name\":\"Word\"},"
                + "\"datasets\":[{\"id\":\"source\",\"sheetName\":\"Source\","
                + "\"sqlFile\":\"source.sql\",\"resultType\":\"LIST\"}],"
                + "\"word\":{\"cover\":{" + cover + "},"
                + "\"toc\":{\"enabled\":true,\"maxLevel\":3,"
                + "\"updateOnOpen\":true},\"sections\":[]}}";
    }
}
