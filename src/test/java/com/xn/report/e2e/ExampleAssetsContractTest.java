package com.xn.report.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.chart.ChartType;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportDefinitionLoader;
import com.xn.report.config.ReportDefinitionValidator;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.support.JsonSchemaContract;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;

class ExampleAssetsContractTest {

    private static final Path CONFIG =
            Paths.get("config", "api-design-efficiency.yml");
    private static final Path EXCEL =
            Paths.get("templates", "api-design-efficiency.xlsx");
    private static final Path WORD =
            Paths.get("templates", "api-design-efficiency.docx");

    @Test
    void exampleConfigurationAndSqlCoverTheAcceptedReportModel() throws Exception {
        ReportDefinition definition =
                ReportDefinitionLoader.createDefault().load(CONFIG);

        com.fasterxml.jackson.databind.JsonNode schema;
        try (InputStream input = Files.newInputStream(Paths.get(
                "src", "main", "resources", "schema",
                "report-definition.schema.json"))) {
            schema = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(input);
        }
        com.fasterxml.jackson.databind.JsonNode configured =
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                        .readTree(CONFIG.toFile());
        assertThat(new JsonSchemaContract(schema).validate(configured))
                .isEmpty();

        assertThat(new ReportDefinitionValidator().validate(definition).issues())
                .isEmpty();
        assertThat(definition.getDatasets()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(definition.getDatasets()).hasSize(6);
        assertThat(definition.getDatasets())
                .extracting(item -> item.getSheetName())
                .containsExactly(
                        "部门-每月", "中心-全年", "中心-每月",
                        "个人-全年", "个人-每月", "时长分布");
        assertThat(definition.getDatasets())
                .extracting(item -> item.getSqlFile())
                .doesNotHaveDuplicates()
                .allSatisfy(sql -> assertThat(sql).startsWith("sql/"));
        assertThat(definition.getParameters())
                .containsKeys(
                        "startTime", "endTimeExclusive",
                        "baselineStartTime", "baselineEndTimeExclusive",
                        "centerNames", "reportPeriod", "preparedDate");
        assertThat(definition.getCharts()).anySatisfy(chart -> {
            List<ChartType> types = chart.getSeries().stream()
                    .map(item -> item.getType())
                    .collect(Collectors.toList());
            assertThat(types).containsExactly(
                    ChartType.STACKED_COLUMN,
                    ChartType.STACKED_COLUMN,
                    ChartType.LINE);
        });
        assertThat(definition.getCharts())
                .extracting(ChartDefinition::getId)
                .contains("approvalTrend", "durationDistribution");
        assertThat(definition.getNarratives())
                .filteredOn(item -> "abnormalApproverNarrative"
                        .equals(item.getId()))
                .extracting(NarrativeDefinition::getTemplate)
                .singleElement()
                .asString()
                .contains(
                        "${rule.abnormalApprovers.matchedCount}",
                        "${rule.abnormalApprovers.summary.maxHours|number:0.00}");
        assertThat(allComponents(definition.getWord().getSections()))
                .anySatisfy(component -> {
                    assertThat(component.getType()).isEqualTo("RULE_TEXT");
                    assertThat(component.getNarrativeId())
                            .isEqualTo("abnormalApproverNarrative");
                });
        assertThat(maxDepth(definition.getWord().getSections())).isEqualTo(4);

        for (String name : Arrays.asList(
                "department-monthly.sql",
                "center-annual.sql",
                "center-monthly.sql",
                "person-annual.sql",
                "person-monthly.sql",
                "duration-distribution.sql")) {
            String sql = new String(
                    Files.readAllBytes(Paths.get("config", "sql", name)),
                    StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains(":endTimeExclusive")
                    .doesNotContain("24:00:00")
                    .doesNotContain("2026-06-31");
        }
    }

    @Test
    void committedTemplatesExposeVisibleDatasetSheetsAndARealWordToc()
            throws Exception {
        try (InputStream input = Files.newInputStream(EXCEL);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(6);
            for (String sheet : Arrays.asList(
                    "部门-每月", "中心-全年", "中心-每月",
                    "个人-全年", "个人-每月", "时长分布")) {
                assertThat(workbook.getSheet(sheet)).isNotNull();
                assertThat(workbook.getSheetVisibility(
                        workbook.getSheetIndex(sheet)))
                        .isEqualTo(SheetVisibility.VISIBLE);
            }
        }

        try (InputStream input = Files.newInputStream(WORD);
             XWPFDocument document = new XWPFDocument(input)) {
            String bodyText = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .collect(Collectors.joining("\n"));
            assertThat(bodyText)
                    .contains("{{cover:title}}")
                    .contains("{{sections}}");
            assertThat(document.getStyles().getStyle("Heading1")).isNotNull();
            assertThat(document.getStyles().getStyle("Heading2")).isNotNull();
            assertThat(document.getStyles().getStyle("Heading3")).isNotNull();
            assertThat(document.getStyles().getStyle("Heading4")).isNotNull();
            assertThat(document.getParagraphs().stream()
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .flatMap(run -> run.getCTR().getFldCharList().stream())
                    .map(CTFldChar::getFldCharType))
                    .isNotEmpty();
            assertThat(fieldInstructions(document))
                    .contains("TOC \\o \"1-4\"");
            assertThat(document.getDocument().xmlText())
                    .doesNotContain("watermark")
                    .doesNotContain("pict");
        }
    }

    private static String fieldInstructions(XWPFDocument document) {
        return document.getParagraphs().stream()
                .flatMap(paragraph -> paragraph.getRuns().stream())
                .flatMap(run -> run.getCTR().getInstrTextList().stream())
                .map(value -> value.getStringValue())
                .collect(Collectors.joining(" "));
    }

    private static int maxDepth(List<WordSectionDefinition> sections) {
        int result = 0;
        for (WordSectionDefinition section : sections) {
            result = Math.max(
                    result,
                    Math.max(
                            section.getLevel(),
                            maxDepth(section.getChildren())));
        }
        return result;
    }

    private static List<WordComponentDefinition> allComponents(
            List<WordSectionDefinition> sections) {
        java.util.ArrayList<WordComponentDefinition> result =
                new java.util.ArrayList<WordComponentDefinition>();
        for (WordSectionDefinition section : sections) {
            result.addAll(section.getComponents());
            result.addAll(allComponents(section.getChildren()));
        }
        return result;
    }
}
