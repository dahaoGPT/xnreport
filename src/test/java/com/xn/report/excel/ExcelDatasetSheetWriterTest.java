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
            FieldDefinition field = new FieldDefinition();
            field.setType(pairs[index + 1]);
            result.put(pairs[index], field);
        }
        return result;
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
}
