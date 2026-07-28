package com.xn.report.sql;

import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SqlQueryResult {

    private final DatasetSchema schema;
    private final List<DatasetRow> rows;

    public SqlQueryResult(DatasetSchema schema, List<DatasetRow> sourceRows) {
        this.schema = Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(sourceRows, "sourceRows");
        List<DatasetRow> copiedRows =
                new ArrayList<DatasetRow>(sourceRows.size());
        for (DatasetRow row : sourceRows) {
            copiedRows.add(Objects.requireNonNull(row, "query row"));
        }
        this.rows = Collections.unmodifiableList(copiedRows);
    }

    public DatasetSchema schema() {
        return schema;
    }

    public List<DatasetRow> rows() {
        return rows;
    }
}
