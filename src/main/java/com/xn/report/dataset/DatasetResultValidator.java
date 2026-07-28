package com.xn.report.dataset;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.sql.SqlQueryResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DatasetResultValidator {

    private static final Map<String, Class<?>> FIELD_TYPES = fieldTypes();

    public DatasetResult validate(
            DatasetDefinition definition, List<DatasetRow> sourceRows) {
        List<DatasetRow> rows = copyRows(
                definition == null ? "<unknown>" : definition.getId(), sourceRows);
        return validateInternal(
                definition,
                new SqlQueryResult(DatasetSchema.infer(rows), rows),
                false);
    }

    public DatasetResult validate(
            DatasetDefinition definition, SqlQueryResult queryResult) {
        return validateInternal(definition, queryResult, true);
    }

    private DatasetResult validateInternal(
            DatasetDefinition definition,
            SqlQueryResult queryResult,
            boolean validateMetadataTypes) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(queryResult, "queryResult");
        String datasetId = requireText(definition.getId(), "dataset id");
        DatasetType resultType = Objects.requireNonNull(
                definition.getResultType(),
                "resultType for dataset " + datasetId);
        List<DatasetRow> rows = copyRows(datasetId, queryResult.rows());
        DatasetSchema schema = queryResult.schema();

        validateShape(datasetId, resultType, schema, rows);
        validateFields(
                datasetId,
                definition.getExpectedFields(),
                schema,
                rows,
                validateMetadataTypes);
        return buildResult(datasetId, resultType, schema, rows);
    }

    private static void validateShape(
            String datasetId,
            DatasetType resultType,
            DatasetSchema schema,
            List<DatasetRow> rows) {
        if ((resultType == DatasetType.SCALAR || resultType == DatasetType.SINGLE)
                && rows.size() > 1) {
            throw error(
                    ReportErrorCode.DATA_001,
                    datasetId,
                    "Dataset " + datasetId + " declared as " + resultType
                            + " returned " + rows.size() + " rows");
        }
        if (resultType == DatasetType.SCALAR
                && schema.fieldNames().size() != 1) {
            throw error(
                    ReportErrorCode.DATA_001,
                    datasetId,
                    "Scalar dataset " + datasetId
                            + " must return exactly one column");
        }
    }

    private static void validateFields(
            String datasetId,
            Map<String, FieldDefinition> expectedFields,
            DatasetSchema schema,
            List<DatasetRow> rows,
            boolean validateMetadataTypes) {
        if (expectedFields == null || expectedFields.isEmpty()) {
            return;
        }
        for (Map.Entry<String, FieldDefinition> expected
                : expectedFields.entrySet()) {
            String fieldName = requireText(expected.getKey(), "expected field");
            FieldDefinition field = Objects.requireNonNull(
                    expected.getValue(),
                    "field definition for " + fieldName);
            Class<?> expectedType = resolveType(fieldName, field.getType());
            if (validateMetadataTypes && !schema.containsField(fieldName)) {
                throw error(
                        ReportErrorCode.DATA_002,
                        datasetId,
                        "Dataset " + datasetId + " is missing expected alias "
                                + fieldName);
            }
            Class<?> actualType = schema.containsField(fieldName)
                    ? schema.typeOf(fieldName) : Object.class;
            if (validateMetadataTypes
                    && !schemaMatches(expectedType, actualType)) {
                throw error(
                        ReportErrorCode.DATA_003,
                        datasetId,
                        "Field " + fieldName + " in dataset " + datasetId
                                + " expected " + normalizedType(field.getType())
                                + " but JDBC metadata declared "
                                + actualType.getSimpleName());
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                DatasetRow row = rows.get(rowIndex);
                if (!row.containsField(fieldName)) {
                    throw error(
                            ReportErrorCode.DATA_002,
                            datasetId,
                            "Dataset " + datasetId + " is missing expected alias "
                                    + fieldName + " at row " + rowIndex);
                }
                Object value = row.get(fieldName);
                if (value == null) {
                    if (field.isRequired()) {
                        throw error(
                                ReportErrorCode.DATA_002,
                                datasetId,
                                "Required field " + fieldName + " is null in dataset "
                                        + datasetId + " at row " + rowIndex);
                    }
                    continue;
                }
                if (!matches(expectedType, value)) {
                    throw error(
                            ReportErrorCode.DATA_003,
                            datasetId,
                            "Field " + fieldName + " in dataset " + datasetId
                                    + " expected " + normalizedType(field.getType())
                                    + " but was " + value.getClass().getSimpleName());
                }
            }
        }
    }

    private static boolean matches(Class<?> expectedType, Object value) {
        return expectedType.isInstance(value);
    }

    private static boolean schemaMatches(
            Class<?> expectedType, Class<?> actualType) {
        return expectedType == Long.class
                ? actualType == Long.class
                : expectedType.equals(actualType);
    }

    private static Class<?> resolveType(String fieldName, String type) {
        String normalized = normalizedType(type);
        Class<?> resolved = FIELD_TYPES.get(normalized);
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "Unsupported expected field type " + type
                            + " for " + fieldName);
        }
        return resolved;
    }

    private static String normalizedType(String type) {
        return requireText(type, "expected field type")
                .toUpperCase(Locale.ROOT);
    }

    private static DatasetResult buildResult(
            String datasetId,
            DatasetType resultType,
            DatasetSchema schema,
            List<DatasetRow> rows) {
        if (resultType == DatasetType.SCALAR) {
            return DatasetResult.scalar(datasetId, schema, rows);
        }
        if (resultType == DatasetType.SINGLE) {
            return DatasetResult.single(datasetId, schema, rows);
        }
        return DatasetResult.list(datasetId, schema, rows);
    }

    private static List<DatasetRow> copyRows(
            String datasetId, List<DatasetRow> sourceRows) {
        if (sourceRows == null) {
            throw new IllegalArgumentException(
                    "Dataset rows must not be null: " + datasetId);
        }
        List<DatasetRow> rows = new ArrayList<DatasetRow>(sourceRows.size());
        for (DatasetRow row : sourceRows) {
            if (row == null) {
                throw new IllegalArgumentException(
                        "Dataset rows must not contain null: " + datasetId);
            }
            rows.add(row);
        }
        return Collections.unmodifiableList(rows);
    }

    private static ReportException error(
            ReportErrorCode code, String datasetId, String message) {
        return new ReportException(
                code, message, null, "DATASET_QUERY", null, datasetId, null);
    }

    private static String requireText(String value, String description) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return value;
    }

    private static Map<String, Class<?>> fieldTypes() {
        Map<String, Class<?>> types = new LinkedHashMap<String, Class<?>>();
        types.put("STRING", String.class);
        types.put("INTEGER", Long.class);
        types.put("LONG", Long.class);
        types.put("DECIMAL", BigDecimal.class);
        types.put("DATE", LocalDate.class);
        types.put("TIME", LocalTime.class);
        types.put("DATETIME", LocalDateTime.class);
        types.put("TIMESTAMP", LocalDateTime.class);
        types.put("BOOLEAN", Boolean.class);
        types.put("BYTES", byte[].class);
        types.put("BINARY", byte[].class);
        return Collections.unmodifiableMap(types);
    }
}
