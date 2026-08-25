package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个数据集执行计算结果不可变值对象。
 * <p>
 * 封装已校验并完成类型规范化的数据集产物：
 * <ul>
 *   <li><b>形态保障（{@link DatasetType}）</b>：
 *     <ul>
 *       <li>{@link DatasetType#SCALAR}：标量单值（最多 1 行 1 列），通过 {@link #scalar()} 安全获取。</li>
 *       <li>{@link DatasetType#SINGLE}：单行多列记录（最多 1 行），通过 {@link #single()} 安全获取。</li>
 *       <li>{@link DatasetType#LIST}：多行多列表格数据，通过 {@link #list()} 安全获取。</li>
 *     </ul>
 *   </li>
 *   <li><b>Schema 元数据</b>：关联显式契约或类型推断后的 {@link DatasetSchema}。</li>
 *   <li><b>行不可变性</b>：内部数据行列表与 {@link DatasetRow} 严格不可变。</li>
 * </ul>
 * </p>
 */
public final class DatasetResult {

    /** 数据集唯一标识。 */
    private final String id;

    /** 数据集结果形态。 */
    private final DatasetType type;

    /** 结果集 Schema 契约元数据。 */
    private final DatasetSchema schema;

    /** 是否具有显式配置的 Schema。 */
    private final boolean explicitSchema;

    /** 不可变数据行列表。 */
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
        this.explicitSchema = explicitSchema != null;
        this.schema = explicitSchema == null
                ? DatasetSchema.infer(copiedRows) : explicitSchema;
    }

    /**
     * 构建推断 Schema 的标量数据集结果。
     *
     * @param id 数据集 ID
     * @param rows 行列表
     * @return DatasetResult 实例
     */
    public static DatasetResult scalar(String id, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SCALAR, null, rows);
    }

    /**
     * 构建具有显式 Schema 的标量数据集结果。
     *
     * @param id 数据集 ID
     * @param schema Schema 契约
     * @param rows 行列表
     * @return DatasetResult 实例
     */
    public static DatasetResult scalar(
            String id, DatasetSchema schema, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SCALAR, requireSchema(schema), rows);
    }

    /**
     * 构建推断 Schema 的单行数据集结果。
     *
     * @param id 数据集 ID
     * @param rows 行列表
     * @return DatasetResult 实例
     */
    public static DatasetResult single(String id, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SINGLE, null, rows);
    }

    /**
     * 构建具有显式 Schema 的单行数据集结果。
     *
     * @param id 数据集 ID
     * @param schema Schema 契约
     * @param rows 行列表
     * @return DatasetResult 实例
     */
    public static DatasetResult single(
            String id, DatasetSchema schema, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.SINGLE, requireSchema(schema), rows);
    }

    /**
     * 构建推断 Schema 的多行列表数据集结果。
     *
     * @param id 数据集 ID
     * @param rows 行列表
     * @return DatasetResult 实例
     */
    public static DatasetResult list(String id, List<DatasetRow> rows) {
        return new DatasetResult(id, DatasetType.LIST, null, rows);
    }

    /**
     * 构建具有显式 Schema 的多行列表数据集结果。
     *
     * @param id 数据集 ID
     * @param schema Schema 契约
     * @param rows 行列表
     * @return DatasetResult 实例
     */
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

    public boolean hasExplicitSchema() {
        return explicitSchema;
    }

    /**
     * 获取多行列表数据（仅当 type 为 LIST 时允许调用）。
     *
     * @return 不可变数据行列表
     */
    public List<DatasetRow> list() {
        requireType(DatasetType.LIST);
        return rows;
    }

    /**
     * 获取单行记录（仅当 type 为 SINGLE 时允许调用）。
     *
     * @return 首行 DatasetRow，若为空返回 null
     */
    public DatasetRow single() {
        requireType(DatasetType.SINGLE);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 获取标量单值（仅当 type 为 SCALAR 时允许调用）。
     *
     * @return 标量列的 Object 值，若无数据返回 null
     */
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
