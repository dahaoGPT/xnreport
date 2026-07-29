package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.config.definition.WordTableColumnDefinition;
import com.xn.report.support.JsonSchemaContract;
import com.xn.report.support.TestFixtures;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    void schemaAcceptsCompleteSectionAndComponentConfiguration() throws Exception {
        String document = reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"")
                .replace("\"sections\":[]",
                        "\"sections\":[{"
                                + "\"id\":\"approval\",\"title\":\"审批时长\","
                                + "\"level\":1,\"emptyStrategy\":\"SHOW_EMPTY\","
                                + "\"emptyMessage\":\"暂无审批数据\","
                                + "\"components\":["
                                + "{\"type\":\"TABLE\",\"tableId\":\"details\","
                                + "\"dataset\":\"source\",\"emptyMessage\":\"无明细\","
                                + "\"columns\":[{\"field\":\"name\","
                                + "\"header\":\"姓名\",\"format\":\"text\","
                                + "\"widthDxa\":2400}]},"
                                + "{\"type\":\"CHART\",\"chartId\":\"trend\","
                                + "\"widthInches\":6.2,\"caption\":\"图1 趋势\","
                                + "\"altText\":\"审批时长趋势图\"},"
                                + "{\"type\":\"ATTACHMENT\",\"title\":\"附件\","
                                + "\"description\":\"附件说明\","
                                + "\"items\":[\"人员明细.xlsx\"]}"
                                + "]}]");

        assertThat(schema().validate(mapper.readTree(document))).isEmpty();
    }

    @Test
    void schemaAcceptsFourLevelNumberingAndRejectsIncompleteNumbering()
            throws Exception {
        String base = reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"");
        String valid = base.replace("\"sections\":[]",
                "\"numbering\":{\"numId\":7,\"levels\":["
                        + "{\"level\":1,\"numFmt\":\"decimal\","
                        + "\"lvlText\":\"第%1章\"},"
                        + "{\"level\":2,\"numFmt\":\"lowerLetter\","
                        + "\"lvlText\":\"%1.%2\"},"
                        + "{\"level\":3,\"numFmt\":\"lowerRoman\","
                        + "\"lvlText\":\"（%3）\"},"
                        + "{\"level\":4,\"numFmt\":\"decimalEnclosedCircle\","
                        + "\"lvlText\":\"%4\"}]},\"sections\":[]");
        String invalid = valid.replace(
                ",{\"level\":4,\"numFmt\":\"decimalEnclosedCircle\","
                        + "\"lvlText\":\"%4\"}", "");

        assertThat(schema().validate(mapper.readTree(valid))).isEmpty();
        assertThat(schema().validate(mapper.readTree(invalid))).isNotEmpty();
    }

    @Test
    void schemaAcceptsAttachmentMetadataWithoutTextAndRejectsEmptyAttachment()
            throws Exception {
        String base = reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"");
        String sectionPrefix = "\"sections\":[{\"id\":\"appendix\","
                + "\"title\":\"附件\",\"level\":1,\"components\":[";
        String suffix = "]}]";

        assertThat(schema().validate(mapper.readTree(base.replace(
                "\"sections\":[]", sectionPrefix
                        + "{\"type\":\"ATTACHMENT\",\"title\":\"附件信息\"}"
                        + suffix)))).isEmpty();
        assertThat(schema().validate(mapper.readTree(base.replace(
                "\"sections\":[]", sectionPrefix
                        + "{\"type\":\"ATTACHMENT\","
                        + "\"description\":\"说明\"}" + suffix)))).isEmpty();
        assertThat(schema().validate(mapper.readTree(base.replace(
                "\"sections\":[]", sectionPrefix
                        + "{\"type\":\"ATTACHMENT\","
                        + "\"items\":[\"明细.xlsx\"]}" + suffix)))).isEmpty();
        assertThat(schema().validate(mapper.readTree(base.replace(
                "\"sections\":[]", sectionPrefix
                        + "{\"type\":\"ATTACHMENT\"}" + suffix)))).isNotEmpty();
        assertThat(schema().validate(mapper.readTree(base.replace(
                "\"sections\":[]", sectionPrefix
                        + "{\"type\":\"ATTACHMENT\",\"text\":\"旧文本\"}"
                        + suffix)))).isNotEmpty();
    }

    @Test
    void schemaRejectsManualNumberingPrefixInSectionTitle()
            throws Exception {
        String document = reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"")
                .replace("\"sections\":[]",
                        "\"sections\":[{\"id\":\"delivery\","
                                + "\"title\":\"一、交付速率\","
                                + "\"level\":1}]");

        assertThat(schema().validate(mapper.readTree(document))).isNotEmpty();
    }

    @Test
    void schemaRejectsInvalidWordComponentDetails() throws Exception {
        String document = reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"")
                .replace("\"sections\":[]",
                        "\"sections\":[{"
                                + "\"id\":\"approval\",\"title\":\"审批时长\","
                                + "\"level\":1,\"emptyMessage\":\" \","
                                + "\"components\":[{\"type\":\"CHART\","
                                + "\"chartId\":\"trend\",\"widthInches\":0},"
                                + "{\"type\":\"TABLE\",\"dataset\":\"source\","
                                + "\"columns\":[{\"field\":\" \",\"widthDxa\":0}]}]}]");

        assertThat(schema().validate(mapper.readTree(document))).isNotEmpty();
    }

    @Test
    void schemaAcceptsOnlySupportedWordImageAlignments() throws Exception {
        String base = reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"")
                .replace("\"sections\":[]",
                        "\"sections\":[{\"id\":\"approval\","
                                + "\"title\":\"审批时长\",\"level\":1,"
                                + "\"components\":[{\"type\":\"CHART\","
                                + "\"chartId\":\"trend\","
                                + "\"alignment\":\"%s\"}]}]");

        assertThat(schema().validate(mapper.readTree(
                String.format(base, "LEFT")))).isEmpty();
        assertThat(schema().validate(mapper.readTree(
                String.format(base, "CENTER")))).isEmpty();
        assertThat(schema().validate(mapper.readTree(
                String.format(base, "RIGHT")))).isEmpty();
        assertThat(schema().validate(mapper.readTree(
                String.format(base, "JUSTIFY")))).isNotEmpty();
    }

    @Test
    void runtimeValidatorChecksTableDatasetColumnsAndImageWidth() {
        ReportDefinition definition =
                TestFixtures.report(TestFixtures.dataset("source"));
        WordSectionDefinition section = new WordSectionDefinition();
        section.setId("approval");
        section.setTitle("审批时长");
        section.setLevel(1);

        WordComponentDefinition table = new WordComponentDefinition();
        table.setType("TABLE");
        table.setDataset("missing");
        table.setEmptyMessage(" ");
        WordTableColumnDefinition column = new WordTableColumnDefinition();
        column.setField(" ");
        column.setWidthDxa(Integer.valueOf(0));
        table.setColumns(Collections.singletonList(column));

        WordComponentDefinition chart = new WordComponentDefinition();
        chart.setType("CHART");
        chart.setChartId("missing");
        chart.setWidthInches(Double.valueOf(0));
        chart.setAlignment("JUSTIFY");
        section.setComponents(Arrays.asList(table, chart));
        definition.getWord().setSections(Collections.singletonList(section));

        ValidationResult result =
                new ReportDefinitionValidator().validate(definition);

        assertThat(result.codes()).contains(
                "CFG-COMPONENT-REFERENCE",
                "CFG-EMPTY-MESSAGE",
                "CFG-WORD-TABLE-COLUMN",
                "CFG-WORD-IMAGE-WIDTH",
                "CFG-WORD-IMAGE-ALIGNMENT");
    }

    @Test
    void schemaAcceptsWordTableBindingContract() throws Exception {
        String document = reportWithCover(
                "\"title\":\"研发效能报告\","
                        + "\"organization\":\"研发中心\","
                        + "\"reportPeriod\":\"2026年6月\","
                        + "\"preparedBy\":\"效能小组\","
                        + "\"preparedDate\":\"2026年7月23日\"")
                .replace("\"sections\":[]",
                        "\"tableBindings\":[{"
                                + "\"id\":\"people\",\"dataset\":\"source\","
                                + "\"marker\":\"{{table:people}}\","
                                + "\"strategy\":\"PROTOTYPE\","
                                + "\"emptyStrategy\":\"SHOW_EMPTY\","
                                + "\"emptyMessage\":\"暂无明细\","
                                + "\"columns\":[{\"field\":\"name\","
                                + "\"header\":\"姓名\"}]}],"
                                + "\"sections\":[]");

        assertThat(schema().validate(mapper.readTree(document))).isEmpty();
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
