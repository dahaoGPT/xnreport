package com.xn.report.excel;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelDatasetSheetWriter {

    private final ExcelTableWriter tableWriter;

    public ExcelDatasetSheetWriter() {
        this(new ExcelTableWriter());
    }

    public ExcelDatasetSheetWriter(ExcelTableWriter tableWriter) {
        if (tableWriter == null) {
            throw new IllegalArgumentException(
                    "tableWriter must not be null");
        }
        this.tableWriter = tableWriter;
    }

    public void write(
            XSSFWorkbook workbook,
            DatasetDefinition definition,
            DatasetResult result) {
        write(workbook, definition, result, null);
    }

    public void write(
            XSSFWorkbook workbook,
            DatasetDefinition definition,
            DatasetResult result,
            ExcelTableBinding binding) {
        if (workbook == null || definition == null || result == null) {
            throw new IllegalArgumentException(
                    "workbook, definition and result must not be null");
        }
        if (!definition.getId().equals(result.id())) {
            throw new IllegalArgumentException(
                    "Dataset definition/result mismatch: "
                            + definition.getId() + " != " + result.id());
        }
        String sheetName = definition.getSheetName();
        ExcelSheetNameRules.validate(sheetName);
        XSSFSheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            sheet = workbook.createSheet(sheetName);
        }
        workbook.setSheetVisibility(
                workbook.getSheetIndex(sheet), SheetVisibility.VISIBLE);

        List<DatasetRow> rows = rows(result);
        LinkedHashMap<String, String> knownFields =
                knownFields(definition, result, rows);
        List<String> fields = new ArrayList<String>();
        List<String> headers = new ArrayList<String>();
        List<String> formats = new ArrayList<String>();
        int startRow = 0;
        String tableName = null;
        if (binding != null) {
            if (!definition.getId().equals(binding.getDataset())
                    || !definition.getSheetName().equals(
                            binding.getSheet())) {
                throw new IllegalArgumentException(
                        "Excel table binding does not match dataset "
                                + definition.getId());
            }
            Set<String> configuredFields =
                    new LinkedHashSet<String>();
            Set<String> configuredHeaders =
                    new LinkedHashSet<String>();
            for (ExcelTableBinding.ColumnBinding column
                    : binding.getColumns()) {
                if (column == null) {
                    throw new IllegalArgumentException(
                            "Excel table column must not be null");
                }
                String normalized =
                        normalize(column.getField());
                if (!configuredFields.add(normalized)) {
                    throw new IllegalArgumentException(
                            "Duplicate Excel table column field: "
                                    + column.getField());
                }
                String canonical = knownFields.get(normalized);
                if (canonical == null) {
                    throw new IllegalArgumentException(
                            "Unknown Excel table column field: "
                                    + column.getField());
                }
                String header = requireText(
                        column.getHeader(), "Excel table column header");
                if (!configuredHeaders.add(
                        header.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "Duplicate Excel table column header: "
                                    + header);
                }
                if (column.isFormatPresent()
                        && (column.getFormat() == null
                        || column.getFormat().trim().isEmpty())) {
                    throw new IllegalArgumentException(
                            "Excel table column format must be non-blank "
                                    + "when configured: "
                                    + column.getField());
                }
                fields.add(canonical);
                headers.add(column.getHeader());
                formats.add(column.getFormat());
            }
            startRow = binding.getStartRow() == null
                    ? 0 : binding.getStartRow().intValue();
            tableName = binding.getTable();
        }
        for (Map.Entry<String, String> known : knownFields.entrySet()) {
            if (!containsIgnoreCase(fields, known.getValue())) {
                if (containsIgnoreCase(headers, known.getValue())) {
                    throw new IllegalArgumentException(
                            "Excel table header " + known.getValue()
                                    + " conflicts with a configured header");
                }
                fields.add(known.getValue());
                headers.add(known.getValue());
                formats.add(null);
            }
        }
        validateExpectedTypes(definition, result, rows);
        tableWriter.write(
                workbook,
                sheet,
                tableName == null
                        ? tableName(
                                workbook, sheet,
                                definition.getId(), startRow)
                        : tableName,
                startRow,
                fields,
                headers,
                formats,
                rows);
    }

    static List<String> fields(
            DatasetDefinition definition, DatasetResult result) {
        return Collections.unmodifiableList(
                new ArrayList<String>(knownFields(
                        definition, result, rows(result)).values()));
    }

    static List<String> fields(
            DatasetDefinition definition,
            DatasetResult result,
            ExcelTableBinding binding) {
        List<DatasetRow> rows = rows(result);
        LinkedHashMap<String, String> known =
                knownFields(definition, result, rows);
        List<String> ordered = new ArrayList<String>();
        if (binding != null) {
            Set<String> configured =
                    new LinkedHashSet<String>();
            for (ExcelTableBinding.ColumnBinding column
                    : binding.getColumns()) {
                if (column == null) {
                    throw new IllegalArgumentException(
                            "Excel table column must not be null");
                }
                String normalized = normalize(column.getField());
                if (!configured.add(normalized)) {
                    throw new IllegalArgumentException(
                            "Duplicate Excel table column field: "
                                    + column.getField());
                }
                String canonical = known.get(normalized);
                if (canonical == null) {
                    throw new IllegalArgumentException(
                            "Unknown Excel table column field: "
                                    + column.getField());
                }
                ordered.add(canonical);
            }
        }
        for (String field : known.values()) {
            if (!containsIgnoreCase(ordered, field)) {
                ordered.add(field);
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    static List<DatasetRow> rows(DatasetResult result) {
        if (result.type() == DatasetType.LIST) {
            return result.list();
        }
        if (result.type() == DatasetType.SINGLE) {
            DatasetRow row = result.single();
            return row == null
                    ? Collections.<DatasetRow>emptyList()
                    : Collections.singletonList(row);
        }
        Object value = result.scalar();
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> fields = result.schema().fieldNames();
        if (fields.size() != 1) {
            throw new IllegalArgumentException(
                    "Scalar dataset must have exactly one field: "
                            + result.id());
        }
        return Collections.singletonList(
                DatasetRow.of(fields.get(0), value));
    }

    private static String tableName(
            XSSFWorkbook workbook,
            XSSFSheet targetSheet,
            String datasetId,
            int startRow) {
        for (XSSFTable table : targetSheet.getTables()) {
            if (table.getArea().getFirstCell().getRow()
                            == startRow
                    && table.getArea().getFirstCell().getCol()
                            == 0) {
                return table.getName();
            }
        }
        Set<String> existing = new LinkedHashSet<String>();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            for (org.apache.poi.xssf.usermodel.XSSFTable table
                    : workbook.getSheetAt(index).getTables()) {
                existing.add(table.getName().toLowerCase(Locale.ROOT));
            }
        }
        String base = "tbl_" + datasetId.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() > 240) {
            base = base.substring(0, 240);
        }
        String candidate = base;
        int suffix = 2;
        while (existing.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "_" + suffix++;
        }
        ExcelTableNameRules.validate(candidate);
        return candidate;
    }

    private static LinkedHashMap<String, String> knownFields(
            DatasetDefinition definition,
            DatasetResult result,
            List<DatasetRow> rows) {
        LinkedHashMap<String, String> fields =
                new LinkedHashMap<String, String>();
        if (definition.getExpectedFields() != null) {
            Set<String> expected =
                    new LinkedHashSet<String>();
            for (String field
                    : definition.getExpectedFields().keySet()) {
                String normalized = normalize(field);
                if (!expected.add(normalized)) {
                    throw new IllegalArgumentException(
                            "Duplicate expected field ignoring case: "
                                    + field);
                }
                addField(fields, field);
            }
        }
        for (String field : result.schema().fieldNames()) {
            addField(fields, field);
        }
        for (DatasetRow row : rows) {
            for (String field : row.fieldNames()) {
                addField(fields, field);
            }
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel dataset table requires at least one field: "
                            + definition.getId());
        }
        if (fields.size()
                > SpreadsheetVersion.EXCEL2007.getMaxColumns()) {
            throw new IllegalArgumentException(
                    "Excel dataset table exceeds XLSX column limit "
                            + SpreadsheetVersion.EXCEL2007
                                    .getMaxColumns()
                            + " for dataset "
                            + definition.getId()
                            + ": " + fields.size());
        }
        return fields;
    }

    private static void addField(
            LinkedHashMap<String, String> fields, String field) {
        String normalized = normalize(field);
        if (!fields.containsKey(normalized)) {
            fields.put(normalized, field);
        }
    }

    private static boolean containsIgnoreCase(
            Iterable<String> fields, String candidate) {
        for (String field : fields) {
            if (field.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String field) {
        return requireText(field, "Excel table column field")
                .toLowerCase(Locale.ROOT);
    }

    private static String requireText(
            String value, String description) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    description + " must not be blank");
        }
        return value;
    }

    private static void validateExpectedTypes(
            DatasetDefinition definition,
            DatasetResult result,
            List<DatasetRow> rows) {
        if (definition.getExpectedFields() == null) {
            return;
        }
        for (Map.Entry<String, FieldDefinition> entry
                : definition.getExpectedFields().entrySet()) {
            String field = entry.getKey();
            FieldDefinition expected = entry.getValue();
            if (expected == null || expected.getType() == null) {
                throw new IllegalArgumentException(
                        "Expected field type is required for " + field);
            }
            Class<?> expectedType = expectedType(expected.getType());
            if (!result.schema().fieldNames().isEmpty()
                    && !result.schema().containsField(field)) {
                throw new IllegalArgumentException(
                        "Dataset schema is missing field " + field);
            }
            if (result.schema().containsField(field)) {
                Class<?> schemaType = result.schema().typeOf(field);
                if (schemaType != Object.class
                        && !expectedType.equals(schemaType)) {
                    throw new IllegalArgumentException(
                            "Field " + field + " expected "
                                    + expected.getType().toUpperCase(Locale.ROOT)
                                    + " but schema was "
                                    + schemaType.getSimpleName());
                }
            }
            for (int index = 0; index < rows.size(); index++) {
                DatasetRow row = rows.get(index);
                if (!row.containsField(field)) {
                    throw new IllegalArgumentException(
                            "Dataset row is missing field " + field
                                    + " at row " + index);
                }
                Object value = row.get(field);
                if (value != null && !expectedType.isInstance(value)) {
                    throw new IllegalArgumentException(
                            "Field " + field + " expected "
                                    + expected.getType().toUpperCase(Locale.ROOT)
                                    + " but was "
                                    + value.getClass().getSimpleName());
                }
            }
        }
    }

    private static Class<?> expectedType(String configuredType) {
        String type = configuredType.toUpperCase(Locale.ROOT);
        if ("STRING".equals(type)) return String.class;
        if ("INTEGER".equals(type) || "LONG".equals(type)) return Long.class;
        if ("DECIMAL".equals(type)) return BigDecimal.class;
        if ("BOOLEAN".equals(type)) return Boolean.class;
        if ("DATE".equals(type)) return LocalDate.class;
        if ("TIME".equals(type)) return LocalTime.class;
        if ("DATETIME".equals(type) || "TIMESTAMP".equals(type)) {
            return LocalDateTime.class;
        }
        if ("BYTES".equals(type) || "BINARY".equals(type)) return byte[].class;
        throw new IllegalArgumentException(
                "Unsupported expected field type: " + configuredType);
    }
}
