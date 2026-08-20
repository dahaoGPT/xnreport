package com.xn.report.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.ReportDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetQueryService;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.entry.DefaultReportEntry;
import com.xn.report.entry.ExecutionStatus;
import com.xn.report.entry.ReportExecutionRequest;
import com.xn.report.entry.ReportExecutionResult;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportGenerationE2ETest {

    @TempDir
    Path temporary;

    @Test
    void createsMatchingExcelAndWordFromOneDatasetSnapshot() throws Exception {
        Path templateRoot = Paths.get("templates").toAbsolutePath();
        assertThat(templateRoot.resolve("api-design-efficiency.xlsx"))
                .isRegularFile();
        assertThat(templateRoot.resolve("api-design-efficiency.docx"))
                .isRegularFile();
        AtomicInteger queryCalls = new AtomicInteger();
        DatasetContext snapshot = exampleSnapshot();
        DatasetQueryService queryService = (definition, parameters) -> {
            queryCalls.incrementAndGet();
            return snapshot;
        };
        Path output = temporary.resolve("output");
        Path executionTemp = temporary.resolve("temp");
        Path configRoot = Paths.get("config").toAbsolutePath();
        ReportExecutionRequest request = new ReportExecutionRequest(
                configRoot.resolve("api-design-efficiency.yml"),
                configRoot,
                configRoot.resolve("sql"),
                templateRoot,
                output,
                executionTemp,
                runtime());

        ReportExecutionResult result =
                DefaultReportEntry.create(queryService).generate(request);

        assertThat(result.getStatus())
                .as("stage=%s error=%s failure=%s",
                        result.getFailedStage(), result.getError(),
                        failureSummary(result.getFailure()))
                .isIn(
                ExecutionStatus.SUCCESS,
                ExecutionStatus.SUCCESS_WITH_WARNINGS);
        assertThat(queryCalls).hasValue(1);
        assertThat(result.getDatasetRowCounts()).hasSize(6);
        assertThat(result.getExcelPath()).isRegularFile();
        assertThat(result.getWordPath()).isRegularFile();
        assertExcel(result.getExcelPath());
        assertWord(result.getWordPath());
        assertThat(listChildren(executionTemp)).isEmpty();
    }

    private static void assertExcel(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(6);
            for (String name : Arrays.asList(
                    "部门-每月", "中心-全年", "中心-每月",
                    "个人-全年", "个人-每月", "时长分布")) {
                int index = workbook.getSheetIndex(name);
                assertThat(index).isNotNegative();
                assertThat(workbook.getSheetVisibility(index))
                        .isEqualTo(SheetVisibility.VISIBLE);
                assertThat(workbook.getSheetAt(index).getTables()).hasSize(1);
            }
            org.apache.poi.xssf.usermodel.XSSFSheet sheet =
                    workbook.getSheet("中心-每月");
            assertThat(sheet.getDrawingPatriarch().getCharts()).hasSize(1);
            org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea plot =
                    sheet.getDrawingPatriarch().getCharts().get(0)
                            .getCTChart().getPlotArea();
            assertThat(plot.getBarChartList()).hasSize(1);
            assertThat(plot.getBarChartArray(0).getSerList()).hasSize(2);
            assertThat(plot.getLineChartList()).hasSize(1);
            assertThat(plot.getLineChartArray(0).getSerList()).hasSize(1);
            assertThat(plot.xmlText())
                    .contains("stacked")
                    .contains("'中心-每月'!");
        }
    }

    private static void assertWord(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertThat(text)
                    .contains("研发效能报告")
                    .contains("2026年6月")
                    .contains("2026年7月23日")
                    .contains("全年变化趋势")
                    .contains("当月分析")
                    .contains("异常说明")
                    .contains("附件信息")
                    .doesNotContain("{{")
                    .doesNotContain("${");
            assertThat(document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getStyle())
                    .collect(Collectors.toList()))
                    .contains("Heading1", "Heading2", "Heading3", "Heading4");
            assertThat(document.getAllPictures()).isNotEmpty();
            String xml = document.getDocument().xmlText();
            assertThat(fieldInstructions(document))
                    .contains("TOC \\o \"1-4\"");
            assertThat(xml.toLowerCase(java.util.Locale.ROOT))
                    .doesNotContain("watermark");
        }
    }

    private static String fieldInstructions(XWPFDocument document) {
        return document.getParagraphs().stream()
                .flatMap(paragraph -> paragraph.getRuns().stream())
                .flatMap(run -> run.getCTR().getInstrTextList().stream())
                .map(value -> value.getStringValue())
                .collect(Collectors.joining(" "));
    }

    private static DatasetContext exampleSnapshot() {
        return DatasetContext.builder()
                .put(list("departmentMonthly",
                        row("nodeName", "全部部门", "statMonth", "2026-01",
                                "avgHours", decimal("25.27"),
                                "baselineHours", decimal("19.51")),
                        row("nodeName", "全部部门", "statMonth", "2026-02",
                                "avgHours", decimal("8.90"),
                                "baselineHours", decimal("19.51")),
                        row("nodeName", "全部部门", "statMonth", "2026-06",
                                "avgHours", decimal("40.86"),
                                "baselineHours", decimal("19.51"))))
                .put(list("centerAnnual",
                        row("nodeName", "API设计", "centerName", "开发一中心",
                                "avgHours", decimal("25.27"),
                                "baselineHours", decimal("19.51")),
                        row("nodeName", "API设计", "centerName", "开发二中心",
                                "avgHours", decimal("20.07"),
                                "baselineHours", decimal("19.51"))))
                .put(list("centerMonthly",
                        centerMonth("开发一中心", "2026-01", "25.27", 2, 4),
                        centerMonth("开发一中心", "2026-02", "8.90", 0, 1),
                        centerMonth("开发一中心", "2026-06", "40.86", 1, 3)))
                .put(list("personAnnual",
                        person("001", "张三", "开发一中心", "30.00", "20.00", "开发组", "1"),
                        person("002", "李四", "开发二中心", "12.00", "18.00", "测试组", "1"),
                        person("003", "王五", "研发中心", "200.00", "40.00", "开发组", "0")))
                .put(list("personMonthly",
                        personMonth("001", "张三", "2026-01", "30.00"),
                        personMonth("001", "张三", "2026-02", "18.00"),
                        personMonth("001", "张三", "2026-06", "35.00")))
                .put(list("durationDistribution",
                        row("durationRange", "1天之内", "approvalCount", 27L, "rangeOrder", 1L),
                        row("durationRange", "7天之内", "approvalCount", 5L, "rangeOrder", 2L),
                        row("durationRange", "7天以上", "approvalCount", 2L, "rangeOrder", 3L)))
                .build();
    }

    private static DatasetRow centerMonth(
            String center, String month, String hours, int over, int within) {
        return row("nodeName", "API设计", "centerName", center,
                "chartGroup", center + " / API设计",
                "statMonth", month, "avgHours", decimal(hours),
                "baselineHours", decimal("19.51"),
                "overStandardCount", Long.valueOf(over),
                "withinStandardCount", Long.valueOf(within));
    }

    private static DatasetRow person(
            String id, String name, String center, String hours,
            String baseline, String category, String onJob) {
        return row("nodeName", "API设计", "centerName", center,
                "approverId", id, "approverName", name,
                "avgHours", decimal(hours),
                "baselineHours", decimal(baseline),
                "groupCategory", category, "groupLeader", "组长A",
                "onJob", onJob);
    }

    private static DatasetRow personMonth(
            String id, String name, String month, String hours) {
        return row("nodeName", "API设计", "centerName", "开发一中心",
                "approverId", id, "approverName", name,
                "statMonth", month, "avgHours", decimal(hours),
                "baselineHours", decimal("20"),
                "groupCategory", "开发组", "groupLeader", "组长A",
                "onJob", "1");
    }

    private static DatasetResult list(String id, DatasetRow... rows) {
        return DatasetResult.list(id, Arrays.asList(rows));
    }

    private static DatasetRow row(Object... pairs) {
        return DatasetRow.of(pairs);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static Map<String, Object> runtime() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("startTime", "2026-01-01 00:00:00");
        values.put("endTimeExclusive", "2026-07-01 00:00:00");
        values.put("baselineStartTime", "2025-01-01 00:00:00");
        values.put("baselineEndTimeExclusive", "2026-01-01 00:00:00");
        values.put("centerNames", Arrays.asList("开发一中心", "开发二中心", "研发中心"));
        values.put("reportPeriod", "2026年6月");
        values.put("preparedDate", "2026年7月23日");
        return values;
    }

    private static java.util.List<Path> listChildren(Path root)
            throws Exception {
        if (!Files.exists(root)) {
            return java.util.Collections.emptyList();
        }
        try (java.util.stream.Stream<Path> children = Files.list(root)) {
            return children.collect(Collectors.toList());
        }
    }

    private static String failureSummary(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (result.length() > 0) {
                result.append(" <- ");
            }
            result.append(current.getClass().getSimpleName())
                    .append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return result.toString();
    }
}
