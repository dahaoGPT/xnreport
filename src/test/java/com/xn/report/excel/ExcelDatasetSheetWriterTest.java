package com.xn.report.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.ExcelDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.config.definition.ExcelValueBinding;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.dataset.DatasetType;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelDatasetSheetWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesOneVisibleTypedTablePerDatasetAndPreservesTemplateSheets()
            throws Exception {
        Path template = tempDir.resolve("template.xlsx");
        createTemplate(template);
        byte[] originalTemplate = Files.readAllBytes(template);

        DatasetDefinition monthly = dataset(
                "centerMonthly", "中心-每月", DatasetType.LIST,
                fields("employeeId", "STRING", "avgHours", "DECIMAL",
                        "onJob", "BOOLEAN", "approvalDate", "DATE",
                        "updatedAt", "DATETIME", "description", "STRING"));
        DatasetDefinition annual = dataset(
                "centerAnnual", "中心-全年", DatasetType.SINGLE,
                fields("center", "STRING", "avgHours", "DECIMAL"));
        DatasetDefinition scalar = dataset(
                "baseline", "基准值", DatasetType.SCALAR,
                fields("standardHours", "DECIMAL"));

        DatasetResult monthlyResult = DatasetResult.list(
                "centerMonthly",
                DatasetSchema.of(
                        "updatedAt", LocalDateTime.class,
                        "employeeId", String.class,
                        "description", String.class,
                        "avgHours", BigDecimal.class,
                        "onJob", Boolean.class,
                        "approvalDate", LocalDate.class),
                Arrays.asList(
                        DatasetRow.of(
                                "employeeId", "00123",
                                "avgHours", new BigDecimal("12.50"),
                                "onJob", true,
                                "approvalDate", LocalDate.of(2026, 6, 30),
                                "updatedAt", LocalDateTime.of(2026, 7, 1, 8, 30),
                                "description", " =2+2"),
                        DatasetRow.of(
                                "employeeId", "00456",
                                "avgHours", null,
                                "onJob", false,
                                "approvalDate", null,
                                "updatedAt", null,
                                "description", "正常")));
        DatasetResult annualResult = DatasetResult.single(
                "centerAnnual",
                DatasetSchema.of(
                        "center", String.class, "avgHours", BigDecimal.class),
                Collections.singletonList(
                        DatasetRow.of("center", "开发一中心",
                                "avgHours", new BigDecimal("8.25"))));
        DatasetResult scalarResult = DatasetResult.scalar(
                "baseline",
                DatasetSchema.of("standardHours", BigDecimal.class),
                Collections.singletonList(
                        DatasetRow.of("standardHours", new BigDecimal("19.51"))));
        DatasetContext context = DatasetContext.builder()
                .put(monthlyResult)
                .put(annualResult)
                .put(scalarResult)
                .build();

        Path output = tempDir.resolve("report.xlsx");
        new ExcelWorkbookWriter().write(
                template, output, Arrays.asList(monthly, annual, scalar), context);

        assertThat(Files.readAllBytes(template)).isEqualTo(originalTemplate);
        try (InputStream stream = Files.newInputStream(output);
                XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            assertThat(workbook.getSheet("报表首页")).isNotNull();
            assertThat(workbook.getSheet("图表数据")).isNull();
            assertVisibleTable(workbook, "中心-每月", 2, 6);
            assertVisibleTable(workbook, "中心-全年", 1, 2);
            assertVisibleTable(workbook, "基准值", 1, 1);

            XSSFSheet sheet = workbook.getSheet("中心-每月");
            assertThat(sheet.getPaneInformation()).isNotNull();
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition())
                    .isEqualTo((short) 1);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("employeeId");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("avgHours");
            assertThat(sheet.getRow(1).getCell(0).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("00123");
            assertThat(sheet.getRow(1).getCell(1).getCellType())
                    .isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue())
                    .isEqualTo(12.50d);
            assertThat(sheet.getRow(1).getCell(2).getCellType())
                    .isEqualTo(CellType.BOOLEAN);
            assertThat(sheet.getRow(1).getCell(3).getCellStyle()
                    .getDataFormatString()).isEqualTo("yyyy-mm-dd");
            assertThat(sheet.getRow(1).getCell(4).getCellStyle()
                    .getDataFormatString()).isEqualTo("yyyy-mm-dd hh:mm:ss");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue())
                    .isEqualTo("' =2+2");
            assertThat(sheet.getRow(2).getCell(1).getCellType())
                    .isEqualTo(CellType.BLANK);
            assertThat(sheet.getColumnWidth(0)).isBetween(8 * 256, 60 * 256);
        }
    }

    @Test
    void writesHeaderOnlyTableForEmptyDatasetUsingExpectedFieldOrder()
            throws Exception {
        Path template = tempDir.resolve("empty-template.xlsx");
        createTemplate(template);
        DatasetDefinition definition = dataset(
                "empty", "空数据", DatasetType.LIST,
                fields("month", "STRING", "hours", "DECIMAL"));
        DatasetResult empty = DatasetResult.list(
                "empty",
                DatasetSchema.of(
                        "hours", BigDecimal.class, "month", String.class),
                Collections.<DatasetRow>emptyList());
        Path output = tempDir.resolve("empty.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                output,
                Collections.singletonList(definition),
                DatasetContext.builder().put(empty).build());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            XSSFSheet sheet = workbook.getSheet("空数据");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("month");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("hours");
            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getTables().get(0).getCTTable().getRef())
                    .isEqualTo("A1:B1");
        }
    }

    @Test
    void rejectsDuplicateSheetNamesWithoutPublishingOutput()
            throws Exception {
        Path template = tempDir.resolve("duplicate-template.xlsx");
        createTemplate(template);
        DatasetDefinition first = dataset(
                "first", "中心", DatasetType.LIST,
                fields("value", "STRING"));
        DatasetDefinition second = dataset(
                "second", "中心", DatasetType.LIST,
                fields("value", "STRING"));
        DatasetContext context = DatasetContext.builder()
                .put(DatasetResult.list(
                        "first", DatasetSchema.of("value", String.class),
                        Collections.<DatasetRow>emptyList()))
                .put(DatasetResult.list(
                        "second", DatasetSchema.of("value", String.class),
                        Collections.<DatasetRow>emptyList()))
                .build();
        Path output = tempDir.resolve("duplicate.xlsx");

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        template,
                        output,
                        Arrays.asList(first, second),
                        context))
                .withMessageContaining("Duplicate Excel sheet name");
        assertThat(output).doesNotExist();
    }

    @Test
    void createsUniqueSafeTableNameWhenTemplateAlreadyUsesDefaultName()
            throws Exception {
        Path template = tempDir.resolve("table-conflict-template.xlsx");
        createTemplateWithNamedTable(template, "tbl_centerMonthly");
        DatasetDefinition definition = dataset(
                "centerMonthly", "中心-每月", DatasetType.LIST,
                fields("value", "STRING"));
        DatasetResult result = DatasetResult.list(
                "centerMonthly",
                DatasetSchema.of("value", String.class),
                Collections.singletonList(DatasetRow.of("value", "ok")));
        Path output = tempDir.resolve("table-conflict.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                output,
                Collections.singletonList(definition),
                DatasetContext.builder().put(result).build());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            assertThat(workbook.getSheet("报表首页").getTables().get(0).getName())
                    .isEqualTo("tbl_centerMonthly");
            assertThat(workbook.getSheet("中心-每月")
                    .getTables().get(0).getName())
                    .isEqualTo("tbl_centerMonthly_2");
        }
    }

    @Test
    void rejectsMissingOrMismatchedExpectedFieldsAndClosesResources()
            throws Exception {
        Path template = tempDir.resolve("invalid-template.xlsx");
        createTemplate(template);
        DatasetDefinition definition = dataset(
                "invalid", "无效", DatasetType.LIST,
                fields("hours", "DECIMAL"));
        DatasetResult missing = DatasetResult.list(
                "invalid",
                DatasetSchema.of("other", String.class),
                Collections.singletonList(DatasetRow.of("other", "x")));
        Path missingOutput = tempDir.resolve("missing.xlsx");

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        template,
                        missingOutput,
                        Collections.singletonList(definition),
                        DatasetContext.builder().put(missing).build()))
                .withMessageContaining("missing field hours");
        assertThat(missingOutput).doesNotExist();

        DatasetResult mismatch = DatasetResult.list(
                "invalid",
                DatasetSchema.of("hours", String.class),
                Collections.singletonList(DatasetRow.of("hours", "12.5")));
        Path mismatchOutput = tempDir.resolve("mismatch.xlsx");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        template,
                        mismatchOutput,
                        Collections.singletonList(definition),
                        DatasetContext.builder().put(mismatch).build()))
                .withMessageContaining("expected DECIMAL");
        assertThat(mismatchOutput).doesNotExist();

        Path renamedTemplate = tempDir.resolve("template-renamed.xlsx");
        Files.move(template, renamedTemplate, StandardCopyOption.ATOMIC_MOVE);
        assertThat(renamedTemplate).exists();
    }

    @Test
    void makesExistingDatasetSheetVisibleAndDoesNotOverwriteTemplate()
            throws Exception {
        Path template = tempDir.resolve("hidden-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("报表首页");
            workbook.createSheet("已有数据");
            workbook.setSheetVisibility(
                    workbook.getSheetIndex("已有数据"),
                    SheetVisibility.HIDDEN);
            try (java.io.OutputStream stream = Files.newOutputStream(template)) {
                workbook.write(stream);
            }
        }
        DatasetDefinition definition = dataset(
                "existing", "已有数据", DatasetType.SINGLE,
                fields("value", "STRING"));
        DatasetResult result = DatasetResult.single(
                "existing",
                DatasetSchema.of("value", String.class),
                Collections.singletonList(
                        DatasetRow.of("value", "=danger")));
        Path output = tempDir.resolve("visible.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                output,
                Collections.singletonList(definition),
                DatasetContext.builder().put(result).build());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            XSSFSheet sheet = workbook.getSheet("已有数据");
            assertThat(workbook.getSheetVisibility(
                    workbook.getSheetIndex(sheet)))
                    .isEqualTo(SheetVisibility.VISIBLE);
            assertThat(sheet.getRow(1).getCell(0).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("'=danger");
        }
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        template,
                        template,
                        Collections.singletonList(definition),
                        DatasetContext.builder().put(result).build()))
                .withMessageContaining("must not overwrite");
    }

    @Test
    void valueBindingWriterAllowsOmittedFormatAndRejectsExplicitBlanks()
            throws Exception {
        Path template = tempDir.resolve("value-format-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Cover");
            try (java.io.OutputStream stream =
                    Files.newOutputStream(template)) {
                workbook.write(stream);
            }
        }
        ReportDefinition report = new ReportDefinition();
        report.setDatasets(Collections.<DatasetDefinition>emptyList());
        ExcelDefinition excel = new ExcelDefinition();
        report.setExcel(excel);
        ExcelValueBinding omitted = new ExcelValueBinding();
        omitted.setSheet("Cover");
        omitted.setCell("B3");
        omitted.setValue("text");
        excel.setValueBindings(Collections.singletonList(omitted));
        Path omittedOutput = tempDir.resolve("value-format-omitted.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                omittedOutput,
                report,
                DatasetContext.builder().build(),
                Collections.<String, Object>emptyMap());

        assertThat(omittedOutput).isRegularFile();
        for (String invalidFormat : Arrays.asList(null, "", "   ")) {
            ExcelValueBinding invalid = new ExcelValueBinding();
            invalid.setSheet("Cover");
            invalid.setCell("B3");
            invalid.setValue("text");
            invalid.setFormat(invalidFormat);
            excel.setValueBindings(Collections.singletonList(invalid));
            Path output = tempDir.resolve(
                    "value-format-invalid-"
                            + String.valueOf(invalidFormat).length()
                            + "-" + System.nanoTime() + ".xlsx");

            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ExcelWorkbookWriter().write(
                            template,
                            output,
                            report,
                            DatasetContext.builder().build(),
                            Collections.<String, Object>emptyMap()))
                    .withMessageContaining("format")
                    .withMessageContaining("non-blank");
            assertThat(output).doesNotExist();
        }
    }

    @Test
    void honorsConfiguredTableLayoutAndTypedScalarBindings()
            throws Exception {
        Path template = tempDir.resolve("binding-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("报表首页");
            XSSFSheet data = workbook.createSheet("中心-每月");
            data.createRow(0).createCell(0).setCellValue("数据说明");
            data.setColumnWidth(0, 30 * 256);
            try (java.io.OutputStream stream = Files.newOutputStream(template)) {
                workbook.write(stream);
            }
        }
        DatasetDefinition monthly = dataset(
                "centerMonthly", "中心-每月", DatasetType.LIST,
                fields("month", "STRING", "hours", "DECIMAL"));
        DatasetDefinition baseline = dataset(
                "baseline", "基准值", DatasetType.SCALAR,
                fields("standardHours", "DECIMAL"));
        DatasetContext context = DatasetContext.builder()
                .put(DatasetResult.list(
                        "centerMonthly",
                        DatasetSchema.of(
                                "month", String.class,
                                "hours", BigDecimal.class),
                        Collections.singletonList(DatasetRow.of(
                                "month", "2026-06",
                                "hours", new BigDecimal("8.25")))))
                .put(DatasetResult.scalar(
                        "baseline",
                        DatasetSchema.of(
                                "standardHours", BigDecimal.class),
                        Collections.singletonList(DatasetRow.of(
                                "standardHours",
                                new BigDecimal("19.51")))))
                .build();
        ReportDefinition report = new ReportDefinition();
        report.setDatasets(Arrays.asList(monthly, baseline));
        ExcelDefinition excel = new ExcelDefinition();
        ExcelTableBinding table = new ExcelTableBinding();
        table.setDataset("centerMonthly");
        table.setSheet("中心-每月");
        table.setTable("tbl_center_monthly");
        table.setStartRow(2);
        ExcelTableBinding.ColumnBinding month =
                new ExcelTableBinding.ColumnBinding();
        month.setField("month");
        month.setHeader("月份");
        ExcelTableBinding.ColumnBinding hours =
                new ExcelTableBinding.ColumnBinding();
        hours.setField("hours");
        hours.setHeader("耗时");
        hours.setFormat("0.00");
        table.setColumns(Arrays.asList(month, hours));
        excel.setTableBindings(Collections.singletonList(table));
        ExcelValueBinding value = new ExcelValueBinding();
        value.setSheet("报表首页");
        value.setCell("B3");
        value.setValue("${dataset.baseline.standardHours}");
        value.setFormat("0.00");
        ExcelValueBinding runtime = new ExcelValueBinding();
        runtime.setSheet("报表首页");
        runtime.setCell("B4");
        runtime.setValue("${runtime.period}");
        excel.setValueBindings(Arrays.asList(value, runtime));
        report.setExcel(excel);
        Path output = tempDir.resolve("bindings.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                output,
                report,
                context,
                Collections.<String, Object>singletonMap(
                        "period", "2026年6月"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            XSSFSheet cover = workbook.getSheet("报表首页");
            assertThat(cover.getRow(2).getCell(1).getNumericCellValue())
                    .isEqualTo(19.51d);
            assertThat(cover.getRow(2).getCell(1).getCellStyle()
                    .getDataFormatString()).isEqualTo("0.00");
            assertThat(cover.getRow(3).getCell(1).getStringCellValue())
                    .isEqualTo("2026年6月");
            XSSFSheet data = workbook.getSheet("中心-每月");
            assertThat(data.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("数据说明");
            assertThat(data.getRow(2).getCell(0).getStringCellValue())
                    .isEqualTo("月份");
            assertThat(data.getRow(2).getCell(1).getStringCellValue())
                    .isEqualTo("耗时");
            assertThat(data.getTables().get(0).getName())
                    .isEqualTo("tbl_center_monthly");
            assertThat(data.getTables().get(0).getCTTable().getRef())
                    .isEqualTo("A3:B4");
            assertThat(data.getColumnWidth(0)).isEqualTo(30 * 256);
        }
    }

    @Test
    void configuredColumnsLeadButAllResultAndRowFieldsAreAppended()
            throws Exception {
        Path template = tempDir.resolve("complete-schema-template.xlsx");
        createTemplate(template);
        DatasetDefinition definition = dataset(
                "complete", "完整数据", DatasetType.LIST,
                fields("expectedOnly", "STRING"));
        DatasetResult result = DatasetResult.list(
                "complete",
                DatasetSchema.of(
                        "actualA", String.class,
                        "extra", BigDecimal.class,
                        "expectedOnly", String.class),
                Collections.singletonList(DatasetRow.of(
                        "actualA", "A",
                        "extra", new BigDecimal("2.50"),
                        "expectedOnly", "E",
                        "rowOnly", true)));
        ExcelTableBinding binding = new ExcelTableBinding();
        binding.setDataset("complete");
        binding.setSheet("完整数据");
        binding.setTable("tbl_complete");
        binding.setStartRow(0);
        ExcelTableBinding.ColumnBinding configured =
                new ExcelTableBinding.ColumnBinding();
        configured.setField("actualA");
        configured.setHeader("配置列");
        binding.setColumns(Collections.singletonList(configured));
        ReportDefinition report = new ReportDefinition();
        report.setDatasets(Collections.singletonList(definition));
        ExcelDefinition excel = new ExcelDefinition();
        excel.setTableBindings(Collections.singletonList(binding));
        report.setExcel(excel);
        Path output = tempDir.resolve("complete-schema.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                output,
                report,
                DatasetContext.builder().put(result).build(),
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            XSSFSheet sheet = workbook.getSheet("完整数据");
            org.apache.poi.ss.usermodel.Row header = sheet.getRow(0);
            assertThat(Arrays.asList(
                    header.getCell(0).getStringCellValue(),
                    header.getCell(1).getStringCellValue(),
                    header.getCell(2).getStringCellValue(),
                    header.getCell(3).getStringCellValue()))
                    .isEqualTo(Arrays.asList(
                            "配置列", "expectedOnly", "extra", "rowOnly"));
            assertThat(sheet.getRow(1).getCell(3).getBooleanCellValue())
                    .isTrue();
            assertThat(sheet.getTables().get(0).getCTTable().getRef())
                    .isEqualTo("A1:D2");
        }
    }

    @Test
    void rejectsDuplicateOrUnknownConfiguredColumnsBeforeWriting()
            throws Exception {
        Path template = tempDir.resolve("column-errors-template.xlsx");
        createTemplate(template);
        DatasetDefinition definition = dataset(
                "columns", "列校验", DatasetType.LIST,
                fields("known", "STRING"));
        DatasetResult result = DatasetResult.list(
                "columns",
                DatasetSchema.of("known", String.class),
                Collections.singletonList(DatasetRow.of("known", "x")));

        assertThatIllegalArgumentException().isThrownBy(() ->
                writeWithColumns(template, definition, result,
                        column("known", "一"),
                        column("KNOWN", "二")))
                .withMessageContaining("Duplicate");
        assertThatIllegalArgumentException().isThrownBy(() ->
                writeWithColumns(template, definition, result,
                        column("missing", "未知")))
                .withMessageContaining("Unknown");
        for (String invalidFormat : Arrays.asList(null, "", "   ")) {
            ExcelTableBinding.ColumnBinding invalid =
                    column("known", "Invalid format");
            invalid.setFormat(invalidFormat);
            assertThatIllegalArgumentException().isThrownBy(() ->
                    writeWithColumns(
                            template, definition, result, invalid))
                    .withMessageContaining("format")
                    .withMessageContaining("non-blank");
        }
    }

    @Test
    void preservesFixtureContentOtherTablesAndStylesWhenTableExpandsAndShrinks()
            throws Exception {
        Path fixture = Paths.get(
                "src/test/resources/fixtures/templates/report-template.xlsx");
        assertThat(fixture).isRegularFile();
        DatasetDefinition definition = dataset(
                "centerMonthly", "中心-每月", DatasetType.LIST,
                fields("month", "STRING", "hours", "DECIMAL"));
        ReportDefinition report = reportWithTableBinding(
                definition, "tbl_center_monthly", 2,
                column("month", "月份"),
                column("hours", "耗时"));
        DatasetResult expanded = DatasetResult.list(
                "centerMonthly",
                DatasetSchema.of(
                        "month", String.class,
                        "hours", BigDecimal.class),
                Arrays.asList(
                        DatasetRow.of("month", "2026-01",
                                "hours", new BigDecimal("1.00")),
                        DatasetRow.of("month", "2026-02",
                                "hours", new BigDecimal("2.00")),
                        DatasetRow.of("month", "2026-03",
                                "hours", new BigDecimal("3.00")),
                        DatasetRow.of("month", "2026-04",
                                "hours", new BigDecimal("4.00"))));
        Path expandedOutput = tempDir.resolve("expanded.xlsx");

        new ExcelWorkbookWriter().write(
                fixture,
                expandedOutput,
                report,
                DatasetContext.builder().put(expanded).build(),
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(expandedOutput))) {
            XSSFSheet sheet = workbook.getSheet("中心-每月");
            assertThat(sheet.getTables()).extracting(XSSFTable::getName)
                    .containsExactlyInAnyOrder(
                            "tbl_center_monthly", "tbl_attachment");
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue())
                    .isEqualTo("表下说明：必须保留");
            assertThat(sheet.getRow(12).getCell(0).getStringCellValue())
                    .isEqualTo("附件：approval-detail.xlsx");
            assertThat(sheet.getTables().stream()
                    .filter(table -> "tbl_center_monthly"
                            .equals(table.getName()))
                    .findFirst().get().getCTTable().getRef())
                    .isEqualTo("A3:B7");
            assertThat(sheet.getRow(6).getCell(0).getCellStyle()
                    .getBorderBottom()).isEqualTo(BorderStyle.THIN);
            assertThat(sheet.getTables().stream()
                    .filter(table -> "tbl_center_monthly"
                            .equals(table.getName()))
                    .findFirst().get().getStyle().getName())
                    .isEqualTo("TableStyleMedium2");
            org.openxmlformats.schemas.spreadsheetml.x2006.main
                    .CTTableStyleInfo styleInfo =
                    sheet.getTables().stream()
                            .filter(table -> "tbl_center_monthly"
                                    .equals(table.getName()))
                            .findFirst().get().getCTTable()
                            .getTableStyleInfo();
            assertThat(styleInfo.getShowFirstColumn()).isFalse();
            assertThat(styleInfo.getShowLastColumn()).isFalse();
            assertThat(styleInfo.getShowRowStripes()).isTrue();
            assertThat(styleInfo.getShowColumnStripes()).isFalse();
        }

        DatasetResult shrunk = DatasetResult.list(
                "centerMonthly",
                DatasetSchema.of(
                        "month", String.class,
                        "hours", BigDecimal.class),
                Collections.singletonList(DatasetRow.of(
                        "month", "2026-06",
                        "hours", new BigDecimal("6.00"))));
        Path shrunkOutput = tempDir.resolve("shrunk.xlsx");
        new ExcelWorkbookWriter().write(
                expandedOutput,
                shrunkOutput,
                report,
                DatasetContext.builder().put(shrunk).build(),
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(shrunkOutput))) {
            XSSFSheet sheet = workbook.getSheet("中心-每月");
            assertThat(sheet.getTables().stream()
                    .filter(table -> "tbl_center_monthly"
                            .equals(table.getName()))
                    .findFirst().get().getCTTable().getRef())
                    .isEqualTo("A3:B4");
            assertThat((Object) sheet.getRow(4)).isNull();
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue())
                    .isEqualTo("表下说明：必须保留");
            assertThat(sheet.getTables()).extracting(XSSFTable::getName)
                    .contains("tbl_attachment");
        }
    }

    @Test
    void rejectsMergedRegionsIntersectingNewOrExistingTargetArea()
            throws Exception {
        DatasetDefinition definition = dataset(
                "merged", "Data", DatasetType.LIST,
                fields("value", "STRING"));
        ReportDefinition report = reportWithTableBinding(
                definition, "tbl_merged", 0,
                column("value", "Value"));
        DatasetResult oneRow = DatasetResult.list(
                "merged",
                DatasetSchema.of("value", String.class),
                Collections.singletonList(
                        DatasetRow.of("value", "new")));

        Path newAreaTemplate =
                tempDir.resolve("new-merged-area-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Home");
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            0, 0, 0, 1));
            try (java.io.OutputStream stream =
                    Files.newOutputStream(newAreaTemplate)) {
                workbook.write(stream);
            }
        }
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        newAreaTemplate,
                        tempDir.resolve("new-merged-area.xlsx"),
                        report,
                        DatasetContext.builder().put(oneRow).build(),
                        Collections.<String, Object>emptyMap()))
                .withMessageContaining("merged region")
                .withMessageContaining("A1:B1");

        Path oldAreaTemplate =
                tempDir.resolve("old-merged-area-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Home");
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Value");
            sheet.createRow(1).createCell(0).setCellValue("old");
            XSSFTable table = sheet.createTable(
                    new org.apache.poi.ss.util.AreaReference(
                            "A1:A2",
                            org.apache.poi.ss.SpreadsheetVersion.EXCEL2007));
            table.setName("tbl_merged");
            table.setDisplayName("tbl_merged");
            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            1, 1, 0, 1));
            try (java.io.OutputStream stream =
                    Files.newOutputStream(oldAreaTemplate)) {
                workbook.write(stream);
            }
        }
        DatasetResult noRows = DatasetResult.list(
                "merged",
                DatasetSchema.of("value", String.class),
                Collections.<DatasetRow>emptyList());
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        oldAreaTemplate,
                        tempDir.resolve("old-merged-area.xlsx"),
                        report,
                        DatasetContext.builder().put(noRows).build(),
                        Collections.<String, Object>emptyMap()))
                .withMessageContaining("merged region")
                .withMessageContaining("A2:B2");
    }

    @Test
    void rejectsValueBindingInsideFinalDatasetTableBeforeWriting()
            throws Exception {
        Path template = tempDir.resolve(
                "value-table-conflict-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Data");
            try (java.io.OutputStream stream =
                    Files.newOutputStream(template)) {
                workbook.write(stream);
            }
        }
        DatasetDefinition definition = dataset(
                "conflict", "Data", DatasetType.LIST,
                fields("value", "STRING"));
        ReportDefinition report = reportWithTableBinding(
                definition, "tbl_conflict", 0,
                column("value", "Value"));
        ExcelValueBinding binding = new ExcelValueBinding();
        binding.setSheet("Data");
        binding.setCell("A2");
        binding.setValue("reserved");
        report.getExcel().setValueBindings(
                Collections.singletonList(binding));
        DatasetResult result = DatasetResult.list(
                "conflict",
                DatasetSchema.of("value", String.class),
                Collections.singletonList(
                        DatasetRow.of("value", "new")));
        Path output = tempDir.resolve("value-table-conflict.xlsx");

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        template,
                        output,
                        report,
                        DatasetContext.builder().put(result).build(),
                        Collections.<String, Object>emptyMap()))
                .withMessageContaining("Value binding Data!A2")
                .withMessageContaining("dataset table conflict")
                .withMessageContaining("A1:A2");
        assertThat(output).doesNotExist();
    }

    @Test
    void rejectsFinalTableWiderThanXlsxColumnLimit()
            throws Exception {
        int columnCount = org.apache.poi.ss.SpreadsheetVersion
                .EXCEL2007.getMaxColumns() + 1;
        List<String> fields = new java.util.ArrayList<String>(
                columnCount);
        for (int index = 0; index < columnCount; index++) {
            fields.add("field" + index);
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ExcelTableWriter().write(
                            workbook,
                            workbook.createSheet("Data"),
                            "tbl_too_wide",
                            0,
                            fields,
                            fields,
                            Collections.nCopies(columnCount, null),
                            Collections.<DatasetRow>emptyList()))
                    .withMessageContaining("XLSX column limit")
                    .withMessageContaining("16384");
        }
    }

    @Test
    void rejectsDuplicateExpectedFieldsIgnoringCaseAtWriteTime()
            throws Exception {
        Map<String, FieldDefinition> duplicateExpected =
                new LinkedHashMap<String, FieldDefinition>();
        duplicateExpected.put("foo", field("STRING"));
        duplicateExpected.put("FOO", field("STRING"));
        DatasetDefinition definition = dataset(
                "caseFields", "Case Fields", DatasetType.LIST,
                duplicateExpected);
        DatasetResult result = DatasetResult.list(
                "caseFields",
                DatasetSchema.of("foo", String.class),
                Collections.singletonList(
                        DatasetRow.of("foo", "value")));
        Path template = tempDir.resolve("case-fields-template.xlsx");
        createTemplate(template);

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        template,
                        tempDir.resolve("case-fields.xlsx"),
                        Collections.singletonList(definition),
                        DatasetContext.builder().put(result).build()))
                .withMessageContaining(
                        "Duplicate expected field ignoring case")
                .withMessageContaining("FOO");
    }

    @Test
    void preservesCellsOutsideTargetColumnsOnTheSameRows()
            throws Exception {
        Path template = tempDir.resolve("same-row-content-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Home");
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("value");
            sheet.getRow(0).createCell(3).setCellValue("side heading");
            sheet.createRow(1).createCell(0).setCellValue("old");
            sheet.getRow(1).createCell(3).setCellValue("side note");
            XSSFTable table = sheet.createTable(
                    new org.apache.poi.ss.util.AreaReference(
                            "A1:A2",
                            org.apache.poi.ss.SpreadsheetVersion.EXCEL2007));
            table.setName("tbl_same_rows");
            table.setDisplayName("tbl_same_rows");
            try (java.io.OutputStream stream =
                    Files.newOutputStream(template)) {
                workbook.write(stream);
            }
        }
        DatasetDefinition definition = dataset(
                "sameRows", "Data", DatasetType.LIST,
                fields("value", "STRING"));
        ReportDefinition report = reportWithTableBinding(
                definition, "tbl_same_rows", 0,
                column("value", "Value"));
        DatasetResult result = DatasetResult.list(
                "sameRows",
                DatasetSchema.of("value", String.class),
                Collections.singletonList(
                        DatasetRow.of("value", "new")));
        Path output = tempDir.resolve("same-row-content.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                output,
                report,
                DatasetContext.builder().put(result).build(),
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            XSSFSheet sheet = workbook.getSheet("Data");
            assertThat(sheet.getRow(0).getCell(3)
                    .getStringCellValue()).isEqualTo("side heading");
            assertThat(sheet.getRow(1).getCell(3)
                    .getStringCellValue()).isEqualTo("side note");
        }
    }

    @Test
    void rejectsWritesThatOverlapOtherTablesOrPreservedCells()
            throws Exception {
        DatasetDefinition definition = dataset(
                "conflict", "Data", DatasetType.LIST,
                fields("value", "STRING"));
        DatasetResult result = DatasetResult.list(
                "conflict",
                DatasetSchema.of("value", String.class),
                Collections.singletonList(
                        DatasetRow.of("value", "new")));

        Path cellTemplate = tempDir.resolve(
                "preserved-cell-conflict-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Home");
            workbook.createSheet("Data")
                    .createRow(1).createCell(0)
                    .setCellValue("keep me");
            try (java.io.OutputStream stream =
                    Files.newOutputStream(cellTemplate)) {
                workbook.write(stream);
            }
        }
        ReportDefinition cellReport = reportWithTableBinding(
                definition, "tbl_conflict", 0,
                column("value", "Value"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        cellTemplate,
                        tempDir.resolve("preserved-cell-conflict.xlsx"),
                        cellReport,
                        DatasetContext.builder().put(result).build(),
                        Collections.<String, Object>emptyMap()))
                .withMessageContaining("conflicts with preserved cell")
                .withMessageContaining("A2");

        Path tableTemplate = tempDir.resolve(
                "overlapping-table-conflict-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Home");
            XSSFSheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("first");
            sheet.getRow(0).createCell(1).setCellValue("second");
            sheet.createRow(1).createCell(0).setCellValue("one");
            sheet.getRow(1).createCell(1).setCellValue("two");
            XSSFTable table = sheet.createTable(
                    new org.apache.poi.ss.util.AreaReference(
                            "A1:B2",
                            org.apache.poi.ss.SpreadsheetVersion.EXCEL2007));
            table.setName("tbl_other");
            table.setDisplayName("tbl_other");
            try (java.io.OutputStream stream =
                    Files.newOutputStream(tableTemplate)) {
                workbook.write(stream);
            }
        }
        ReportDefinition tableReport = reportWithTableBinding(
                definition, "tbl_conflict", 1,
                column("value", "Value"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        tableTemplate,
                        tempDir.resolve("overlapping-table-conflict.xlsx"),
                        tableReport,
                        DatasetContext.builder().put(result).build(),
                        Collections.<String, Object>emptyMap()))
                .withMessageContaining("overlaps table")
                .withMessageContaining("tbl_other");
    }

    @Test
    void dateFormattingClonesPrototypeStyleInsteadOfReplacingIt()
            throws Exception {
        Path template = tempDir.resolve("date-style-template.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("报表首页");
            XSSFSheet sheet = workbook.createSheet("日期");
            sheet.createRow(0).createCell(0).setCellValue("date");
            org.apache.poi.ss.usermodel.Cell prototype =
                    sheet.createRow(1).createCell(0);
            org.apache.poi.ss.usermodel.CellStyle style =
                    workbook.createCellStyle();
            style.setBorderBottom(BorderStyle.THICK);
            style.setFillForegroundColor(
                    org.apache.poi.ss.usermodel.IndexedColors.YELLOW
                            .getIndex());
            style.setFillPattern(
                    org.apache.poi.ss.usermodel.FillPatternType
                            .SOLID_FOREGROUND);
            prototype.setCellStyle(style);
            XSSFTable table = sheet.createTable(
                    new org.apache.poi.ss.util.AreaReference(
                            "A1:A2",
                            org.apache.poi.ss.SpreadsheetVersion.EXCEL2007));
            table.setName("tbl_dates");
            table.setDisplayName("tbl_dates");
            try (java.io.OutputStream stream = Files.newOutputStream(template)) {
                workbook.write(stream);
            }
        }
        DatasetDefinition definition = dataset(
                "dates", "日期", DatasetType.LIST,
                fields("date", "DATE"));
        ReportDefinition report = reportWithTableBinding(
                definition, "tbl_dates", 0,
                column("date", "日期"));
        DatasetResult result = DatasetResult.list(
                "dates",
                DatasetSchema.of("date", LocalDate.class),
                Collections.singletonList(
                        DatasetRow.of("date", LocalDate.of(2026, 6, 1))));
        Path output = tempDir.resolve("date-style.xlsx");

        new ExcelWorkbookWriter().write(
                template,
                output,
                report,
                DatasetContext.builder().put(result).build(),
                Collections.<String, Object>emptyMap());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output))) {
            org.apache.poi.ss.usermodel.Cell cell =
                    workbook.getSheet("日期").getRow(1).getCell(0);
            assertThat(cell.getCellStyle().getBorderBottom())
                    .isEqualTo(BorderStyle.THICK);
            assertThat(cell.getCellStyle().getFillForegroundColor())
                    .isEqualTo(org.apache.poi.ss.usermodel.IndexedColors
                            .YELLOW.getIndex());
            assertThat(cell.getCellStyle().getDataFormatString())
                    .isEqualTo("yyyy-mm-dd");
        }
    }

    @Test
    void rejectsConfiguredTableNameCollidingWithAnotherSheetIgnoringCase()
            throws Exception {
        Path template = tempDir.resolve("global-table-template.xlsx");
        createTemplateWithNamedTable(template, "Tbl_Collision");
        DatasetDefinition definition = dataset(
                "collision", "数据", DatasetType.LIST,
                fields("value", "STRING"));
        ReportDefinition report = reportWithTableBinding(
                definition, "tbl_collision", 0,
                column("value", "值"));
        DatasetResult result = DatasetResult.list(
                "collision",
                DatasetSchema.of("value", String.class),
                Collections.singletonList(DatasetRow.of("value", "x")));

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExcelWorkbookWriter().write(
                        template,
                        tempDir.resolve("collision.xlsx"),
                        report,
                        DatasetContext.builder().put(result).build(),
                        Collections.<String, Object>emptyMap()))
                .withMessageContaining("table name")
                .withMessageContaining("Tbl_Collision");
    }

    private static void assertVisibleTable(
            XSSFWorkbook workbook,
            String sheetName,
            int dataRows,
            int columns) {
        XSSFSheet sheet = workbook.getSheet(sheetName);
        assertThat(sheet).isNotNull();
        assertThat(workbook.getSheetVisibility(workbook.getSheetIndex(sheet)))
                .isEqualTo(SheetVisibility.VISIBLE);
        assertThat(sheet.getTables()).hasSize(1);
        XSSFTable table = sheet.getTables().get(0);
        CellReference first = table.getArea().getFirstCell();
        CellReference last = table.getArea().getLastCell();
        assertThat(first.getRow()).isZero();
        assertThat(first.getCol()).isZero();
        assertThat(last.getRow()).isEqualTo(dataRows);
        assertThat(last.getCol()).isEqualTo((short) (columns - 1));
        assertThat(table.getCTTable().getAutoFilter().getRef())
                .isEqualTo(table.getCTTable().getRef());
    }

    private static DatasetDefinition dataset(
            String id,
            String sheetName,
            DatasetType type,
            Map<String, FieldDefinition> expectedFields) {
        DatasetDefinition definition = new DatasetDefinition();
        definition.setId(id);
        definition.setSheetName(sheetName);
        definition.setSql(id + ".sql");
        definition.setResultType(type);
        definition.setExpectedFields(expectedFields);
        return definition;
    }

    private static Map<String, FieldDefinition> fields(String... pairs) {
        Map<String, FieldDefinition> result =
                new LinkedHashMap<String, FieldDefinition>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(pairs[index], field(pairs[index + 1]));
        }
        return result;
    }

    private static FieldDefinition field(String type) {
        FieldDefinition field = new FieldDefinition();
        field.setType(type);
        return field;
    }

    private static void createTemplate(Path path) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("报表首页");
            try (java.io.OutputStream stream = Files.newOutputStream(path)) {
                workbook.write(stream);
            }
        }
    }

    private static void createTemplateWithNamedTable(
            Path path, String tableName) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("报表首页");
            sheet.createRow(0).createCell(0).setCellValue("保留");
            sheet.createRow(1).createCell(0).setCellValue("值");
            XSSFTable table = sheet.createTable(
                    new org.apache.poi.ss.util.AreaReference(
                            "A1:A2",
                            org.apache.poi.ss.SpreadsheetVersion.EXCEL2007));
            table.setName(tableName);
            table.setDisplayName(tableName);
            try (java.io.OutputStream stream = Files.newOutputStream(path)) {
                workbook.write(stream);
            }
        }
    }

    private void writeWithColumns(
            Path template,
            DatasetDefinition definition,
            DatasetResult result,
            ExcelTableBinding.ColumnBinding... columns) throws Exception {
        ReportDefinition report = reportWithTableBinding(
                definition, "tbl_columns", 0, columns);
        new ExcelWorkbookWriter().write(
                template,
                tempDir.resolve("column-result-" + System.nanoTime() + ".xlsx"),
                report,
                DatasetContext.builder().put(result).build(),
                Collections.<String, Object>emptyMap());
    }

    private static ReportDefinition reportWithTableBinding(
            DatasetDefinition definition,
            String tableName,
            int startRow,
            ExcelTableBinding.ColumnBinding... columns) {
        ExcelTableBinding binding = new ExcelTableBinding();
        binding.setDataset(definition.getId());
        binding.setSheet(definition.getSheetName());
        binding.setTable(tableName);
        binding.setStartRow(startRow);
        binding.setColumns(Arrays.asList(columns));
        ExcelDefinition excel = new ExcelDefinition();
        excel.setTableBindings(Collections.singletonList(binding));
        ReportDefinition report = new ReportDefinition();
        report.setDatasets(Collections.singletonList(definition));
        report.setExcel(excel);
        return report;
    }

    private static ExcelTableBinding.ColumnBinding column(
            String field, String header) {
        ExcelTableBinding.ColumnBinding column =
                new ExcelTableBinding.ColumnBinding();
        column.setField(field);
        column.setHeader(header);
        return column;
    }
}
