package com.xn.report.excel;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ExcelDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.config.definition.ExcelValueBinding;
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
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelWorkbookWriter {

    private final ExcelTemplateLoader templateLoader;
    private final ExcelDatasetSheetWriter datasetWriter;
    private final ExcelOutputValidator outputValidator;
    private final ExcelSheetNameValidator sheetNameValidator;

    public ExcelWorkbookWriter() {
        this(new ExcelTemplateLoader(), new ExcelDatasetSheetWriter(),
                new ExcelOutputValidator(), new ExcelSheetNameValidator());
    }

    public ExcelWorkbookWriter(
            ExcelTemplateLoader templateLoader,
            ExcelDatasetSheetWriter datasetWriter,
            ExcelOutputValidator outputValidator,
            ExcelSheetNameValidator sheetNameValidator) {
        this.templateLoader = require(
                templateLoader, "templateLoader");
        this.datasetWriter = require(datasetWriter, "datasetWriter");
        this.outputValidator = require(
                outputValidator, "outputValidator");
        this.sheetNameValidator = require(
                sheetNameValidator, "sheetNameValidator");
    }

    public void write(
            Path template,
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context) throws IOException {
        writeInternal(
                template, output, definitions, context,
                new ExcelDefinition(),
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
                workbook.setForceFormulaRecalculation(true);
                workbook.write(stream);
            }
            outputValidator.validate(
                    temporary, definitions, context,
                    tableBindings);
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
