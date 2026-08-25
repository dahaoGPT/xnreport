package com.xn.report.excel;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ExcelDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.config.definition.ExcelValueBinding;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.chart.ExcelChartWriter;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel 工作簿端到端渲染与生成核心执行器。
 * <p>
 * 统领整个 Excel 报表的填充流程：
 * <ol>
 *   <li>加载 Excel 模板并创建受工作空间保护的临时渲染文件。</li>
 *   <li>执行工作表名称及单元格值绑定（{@link ExcelValueBinding}，如 <code>${runtime.date}</code>、<code>${dataset.ds1.total}</code>）跨界防冲突校验。</li>
 *   <li>填充离散单元格参数（{@link ExcelValueBinder}）。</li>
 *   <li>逐个数据集执行工作表与结构化表格物化（{@link ExcelDatasetSheetWriter}）。</li>
 *   <li>生成并嵌入原生图表或更新模板图表（{@link ExcelChartWriter}）。</li>
 *   <li>强制触发公式重算标记（setForceFormulaRecalculation），经 {@link ExcelOutputValidator} 严苛质检后原子移动（ATOMIC_MOVE）至最终目标路径。</li>
 * </ol>
 * </p>
 */
public final class ExcelWorkbookWriter {

    private final ExcelTemplateLoader templateLoader;
    private final ExcelDatasetSheetWriter datasetWriter;
    private final ExcelOutputValidator outputValidator;
    private final ExcelSheetNameValidator sheetNameValidator;
    private final ExcelChartWriter chartWriter;

    public ExcelWorkbookWriter() {
        this(new ExcelTemplateLoader(), new ExcelDatasetSheetWriter(),
                new ExcelOutputValidator(), new ExcelSheetNameValidator(),
                new ExcelChartWriter());
    }

    public ExcelWorkbookWriter(
            ExcelTemplateLoader templateLoader,
            ExcelDatasetSheetWriter datasetWriter,
            ExcelOutputValidator outputValidator,
            ExcelSheetNameValidator sheetNameValidator) {
        this(templateLoader, datasetWriter, outputValidator,
                sheetNameValidator, new ExcelChartWriter());
    }

    public ExcelWorkbookWriter(
            ExcelTemplateLoader templateLoader,
            ExcelDatasetSheetWriter datasetWriter,
            ExcelOutputValidator outputValidator,
            ExcelSheetNameValidator sheetNameValidator,
            ExcelChartWriter chartWriter) {
        this.templateLoader = require(
                templateLoader, "templateLoader");
        this.datasetWriter = require(datasetWriter, "datasetWriter");
        this.outputValidator = require(
                outputValidator, "outputValidator");
        this.sheetNameValidator = require(
                sheetNameValidator, "sheetNameValidator");
        this.chartWriter = require(chartWriter, "chartWriter");
    }

    public void write(
            Path template,
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context) throws IOException {
        writeInternal(
                template, output, definitions, context,
                new ExcelDefinition(),
                Collections.<ChartDefinition>emptyList(),
                Collections.<String, Object>emptyMap());
    }

    public void write(
            Path template,
            Path output,
            ReportDefinition definition,
            DatasetContext context,
            Map<String, Object> runtime) throws IOException {
        if (definition == null) {
            throw new IllegalArgumentException(
                    "definition must not be null");
        }
        writeInternal(
                template, output, definition.getDatasets(),
                context, definition.getExcel(),
                definition.getCharts(),
                runtime == null
                        ? Collections.<String, Object>emptyMap()
                        : runtime);
    }

    private void writeInternal(
            Path template,
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context,
            ExcelDefinition excel,
            List<ChartDefinition> charts,
            Map<String, Object> runtime) throws IOException {
        if (template == null || output == null
                || definitions == null || context == null) {
            throw new IllegalArgumentException(
                    "template, output, definitions and context must not be null");
        }
        if (template.toAbsolutePath().normalize().equals(
                output.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "Excel output must not overwrite the template");
        }
        List<String> sheetNames =
                new ArrayList<String>(definitions.size());
        for (DatasetDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException(
                        "Dataset definition must not be null");
            }
            sheetNames.add(definition.getSheetName());
            if (!context.contains(definition.getId())) {
                throw new IllegalArgumentException(
                        "Missing dataset result: " + definition.getId());
            }
        }
        sheetNameValidator.validateAll(sheetNames);
        Map<String, ExcelTableBinding> tableBindings =
                tableBindings(excel);
        validateValueBindingTableRanges(
                definitions,
                context,
                tableBindings,
                excel == null
                        ? Collections.<ExcelValueBinding>emptyList()
                        : excel.getValueBindings());

        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Excel output must have a parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(
                parent, ".xnreport-excel-", ".xlsx");
        boolean published = false;
        try {
            Files.copy(template, temporary,
                    StandardCopyOption.REPLACE_EXISTING);
            try (XSSFWorkbook workbook =
                            templateLoader.load(temporary);
                    OutputStream stream =
                            Files.newOutputStream(temporary)) {
                bindValues(
                        workbook, excel.getValueBindings(),
                        context, runtime);
                for (DatasetDefinition definition : definitions) {
                    datasetWriter.write(
                            workbook,
                            definition,
                            context.get(definition.getId()),
                            tableBindings.get(definition.getId()));
                }
                chartWriter.write(
                        workbook,
                        charts == null
                                ? Collections.<ChartDefinition>emptyList()
                                : charts,
                        definitions, context, tableBindings);
                workbook.setForceFormulaRecalculation(true);
                workbook.write(stream);
            }
            outputValidator.validate(
                    temporary, definitions, context,
                    tableBindings,
                    charts == null
                            ? Collections.<ChartDefinition>emptyList()
                            : charts);
            move(temporary, output);
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static Map<String, ExcelTableBinding> tableBindings(
            ExcelDefinition excel) {
        Map<String, ExcelTableBinding> byDataset =
                new LinkedHashMap<String, ExcelTableBinding>();
        if (excel == null || excel.getTableBindings() == null) {
            return byDataset;
        }
        for (ExcelTableBinding binding : excel.getTableBindings()) {
            if (binding == null) {
                throw new IllegalArgumentException(
                        "Excel table binding must not be null");
            }
            if (byDataset.put(binding.getDataset(), binding) != null) {
                throw new IllegalArgumentException(
                        "Duplicate table binding for dataset: "
                                + binding.getDataset());
            }
        }
        return byDataset;
    }

    private static void validateValueBindingTableRanges(
            List<DatasetDefinition> definitions,
            DatasetContext context,
            Map<String, ExcelTableBinding> tableBindings,
            List<ExcelValueBinding> valueBindings) {
        List<DatasetTableRange> ranges =
                new ArrayList<DatasetTableRange>();
        for (DatasetDefinition definition : definitions) {
            DatasetResult result = context.get(definition.getId());
            ExcelTableBinding tableBinding =
                    tableBindings.get(definition.getId());
            int startRow = tableBinding == null
                    || tableBinding.getStartRow() == null
                    ? 0 : tableBinding.getStartRow().intValue();
            int columnCount = ExcelDatasetSheetWriter.fields(
                    definition, result, tableBinding).size();
            int lastRow = startRow
                    + ExcelDatasetSheetWriter.rows(result).size();
            ranges.add(new DatasetTableRange(
                    definition.getId(),
                    definition.getSheetName(),
                    new CellRangeAddress(
                            startRow,
                            lastRow,
                            0,
                            columnCount - 1)));
        }
        if (valueBindings == null) {
            return;
        }
        for (ExcelValueBinding binding : valueBindings) {
            if (binding == null
                    || binding.getSheet() == null
                    || binding.getCell() == null) {
                continue;
            }
            CellReference cell = new CellReference(
                    binding.getCell());
            for (DatasetTableRange table : ranges) {
                if (binding.getSheet().equalsIgnoreCase(
                        table.sheet)
                        && table.range.isInRange(
                                cell.getRow(), cell.getCol())) {
                    throw new IllegalArgumentException(
                            "Value binding "
                                    + binding.getSheet() + "!"
                                    + cell.formatAsString()
                                    + " conflicts with dataset table "
                                    + table.datasetId
                                    + " range "
                                    + table.range.formatAsString());
                }
            }
        }
    }

    private static final class DatasetTableRange {

        private final String datasetId;
        private final String sheet;
        private final CellRangeAddress range;

        private DatasetTableRange(
                String datasetId,
                String sheet,
                CellRangeAddress range) {
            this.datasetId = datasetId;
            this.sheet = sheet;
            this.range = range;
        }
    }

    private static void bindValues(
            XSSFWorkbook workbook,
            List<ExcelValueBinding> bindings,
            DatasetContext context,
            Map<String, Object> runtime) {
        if (bindings == null) {
            throw new IllegalArgumentException(
                    "Excel valueBindings must not be null");
        }
        ExcelValueBinder binder = new ExcelValueBinder(workbook);
        for (ExcelValueBinding binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException(
                        "Excel value binding must not be null");
            }
            if (binding.isFormatPresent()
                    && (binding.getFormat() == null
                    || binding.getFormat().trim().isEmpty())) {
                throw new IllegalArgumentException(
                        "Excel value binding format must be non-blank "
                                + "when configured: "
                                + binding.getSheet() + "!"
                                + binding.getCell());
            }
            XSSFSheet sheet = workbook.getSheet(binding.getSheet());
            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Missing value binding sheet: "
                                + binding.getSheet());
            }
            CellReference reference =
                    new CellReference(binding.getCell());
            Row row = sheet.getRow(reference.getRow());
            if (row == null) {
                row = sheet.createRow(reference.getRow());
            }
            Cell cell = row.getCell(reference.getCol());
            if (cell == null) {
                cell = row.createCell(reference.getCol());
            }
            binder.bind(
                    cell,
                    resolveValue(binding.getValue(), context, runtime));
            if (binding.isFormatPresent()) {
                CellStyle style = workbook.createCellStyle();
                style.cloneStyleFrom(cell.getCellStyle());
                style.setDataFormat(workbook.createDataFormat()
                        .getFormat(binding.getFormat()));
                cell.setCellStyle(style);
            }
        }
    }

    private static Object resolveValue(
            String expression,
            DatasetContext context,
            Map<String, Object> runtime) {
        if (expression == null
                || !expression.startsWith("${")
                || !expression.endsWith("}")) {
            return expression;
        }
        String path =
                expression.substring(2, expression.length() - 1);
        if (path.startsWith("runtime.")) {
            String key = path.substring("runtime.".length());
            if (!runtime.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Missing runtime value: " + key);
            }
            return runtime.get(key);
        }
        if (path.startsWith("dataset.")) {
            String[] parts = path.split("\\.", 3);
            if (parts.length != 3 || !context.contains(parts[1])) {
                throw new IllegalArgumentException(
                        "Invalid dataset value binding: " + expression);
            }
            DatasetResult result = context.get(parts[1]);
            if (!result.schema().containsField(parts[2])) {
                throw new IllegalArgumentException(
                        "Missing dataset field in value binding: "
                                + parts[2]);
            }
            DatasetRow row = firstRow(result);
            return row == null ? null : row.get(parts[2]);
        }
        throw new IllegalArgumentException(
                "Unsupported Excel value binding: " + expression);
    }

    private static DatasetRow firstRow(DatasetResult result) {
        if (result.type() == DatasetType.LIST) {
            return result.list().isEmpty()
                    ? null : result.list().get(0);
        }
        if (result.type() == DatasetType.SINGLE) {
            return result.single();
        }
        if (result.scalar() == null) {
            return null;
        }
        return DatasetRow.of(
                result.schema().fieldNames().get(0),
                result.scalar());
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    name + " must not be null");
        }
        return value;
    }
}
