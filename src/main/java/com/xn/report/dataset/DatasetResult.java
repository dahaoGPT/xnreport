package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DatasetResult {

    private final String id;
    private final DatasetType type;
    private final DatasetSchema schema;
    private final List<DatasetRow> rows;

    private DatasetResult(
            String id,
            DatasetType type,
            DatasetSchema explicitSchema,
            List<DatasetRow> sourceRows) {
        this.id = requireId(id);
        this.type = type;
        if (sourceRows == null) {
            throw new IllegalArgumentException("Dataset rows must not be null: " + id);
        }
        ArrayList<DatasetRow> copiedRows =
                new ArrayList<DatasetRow>(sourceRows.size());
        for (DatasetRow row : sourceRows) {
            if (row == null) {
                throw new IllegalArgumentException(
                        "Dataset rows must not contain null: " + id);
            }
            copiedRows.add(row);
        }
        validateShape(id, type, copiedRows);
        this.rows = Collections.unmodifiableList(copiedRows);
        this.schema = explicitSchema == null
                ? DatasetSchema.infer(copiedRows) : explicitSchema;
    }

    public static DatasetResult scalar(String id, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SCALAR, null, rows);
    }

    public static DatasetResult scalar(
            String id, DatasetSchema schema, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SCALAR, requireSchema(schema), rows);
    }

    public static DatasetResult single(String id, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SINGLE, null, rows);
    }

    public static DatasetResult single(
            String id, DatasetSchema schema, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SINGLE, requireSchema(schema), rows);
    }

    public static DatasetResult list(String id, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.LIST, null, rows);
    }

    public static DatasetResult list(
            String id, DatasetSchema schema, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.LIST, requireSchema(schema), rows);
    }

    public String id() {
        return id;
    }

    public DatasetType type() {
        return type;
    }

    public DatasetSchema schema() {
        return schema;
    }

    public List<DatasetRow> list() {
        requireType(DatasetType.LIST);
        return rows;
    }

    public DatasetRow single() {
        requireType(DatasetType.SINGLE);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Object scalar() {
        requireType(DatasetType.SCALAR);
        if (rows.isEmpty()) {
            return null;
        }
        DatasetRow row = rows.get(0);
        return row.asMap().values().iterator().next();
    }

    private void requireType(DatasetType expected) {
        if (type != expected) {
            throw new IllegalStateException(
                    "Dataset " + id + " has type " + type + ", not " + expected);
        }
    }

    private static void validateShape(
            String id, DatasetType type, List<DatasetRow> rows) {
        if ((type == DatasetType.SCALAR || type == DatasetType.SINGLE)
                && rows.size() > 1) {
            throw new IllegalArgumentException(
                    "Dataset " + id + " of type " + type + " permits at most one row");
        }
        if (type == DatasetType.SCALAR
                && rows.size() == 1
                && rows.get(0).fieldNames().size() != 1) {
            throw new IllegalArgumentException(
                    "Scalar dataset " + id + " requires exactly one field");
        }
    }

    private static String requireId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Dataset id must not be blank");
        }
        return id;
    }

    private static DatasetSchema requireSchema(DatasetSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("Dataset schema must not be null");
        }
        return schema;
    }
}
