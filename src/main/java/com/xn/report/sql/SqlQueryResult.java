package com.xn.report.sql;

import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SQL 查询结果不可变数据快照值对象。
 * <p>
 * 封装单次 SQL 查询返回的 {@link DatasetSchema} 结构元数据以及由 {@link DatasetRow} 组成的不可变数据行列表。
 * </p>
 */
public final class SqlQueryResult {

    /** 结果集 Schema 元数据。 */
    private final DatasetSchema schema;

    /** 不可变数据行列表。 */
    private final List<DatasetRow> rows;

    /**
     * 构造 SQL 查询结果对象。
     *
     * @param schema 结果集 Schema，不可为 null
     * @param sourceRows 原始数据行列表，不可为 null
     */
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

    /**
     * 获取结果集 Schema。
     *
     * @return Schema 契约对象
     */
    public DatasetSchema schema() {
        return schema;
    }

    /**
     * 获取不可变数据行列表。
     *
     * @return 数据行列表
     */
    public List<DatasetRow> rows() {
        return rows;
    }
}
