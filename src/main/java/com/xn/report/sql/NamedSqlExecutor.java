package com.xn.report.sql;

import com.xn.report.dataset.DatasetRow;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;

/**
 * 命名参数只读 SQL 执行器。
 * <p>
 * 封装 Spring JDBC 的 {@link NamedParameterJdbcTemplate}，执行带命名参数的只读 SQL 查询：
 * <ul>
 *   <li><b>超时与行数保护</b>：动态设置 JDBC Statement queryTimeout 与 maxRows，防止慢查询与大内存占用。</li>
 *   <li><b>大对象（LOB）保护</b>：控制 CLOB/BLOB 字符与字节的最大读取限制，防止内存溢出。</li>
 *   <li><b>类型安全映射</b>：利用 {@link ResultSetRowMapper} 将 ResultSet 映射为不可变的 {@link DatasetRow} 与 {@link com.xn.report.dataset.DatasetSchema}。</li>
 * </ul>
 * </p>
 */
public final class NamedSqlExecutor {

    /** Spring 命名参数 JDBC 模板。 */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 结果集行映射器。 */
    private final ResultSetRowMapper rowMapper;

    /**
     * 根据数据源构造执行器（使用默认 LOB 大小限制）。
     *
     * @param dataSource 数据源，不可为 null
     */
    public NamedSqlExecutor(DataSource dataSource) {
        this(
                dataSource,
                ResultSetRowMapper.DEFAULT_MAX_LOB_CHARS,
                ResultSetRowMapper.DEFAULT_MAX_LOB_BYTES);
    }

    /**
     * 根据数据源与自定义 LOB 大小限制构造执行器。
     *
     * @param dataSource 数据源
     * @param maxLobChars CLOB 最大字符数
     * @param maxLobBytes BLOB 最大字节数
     */
    public NamedSqlExecutor(
            DataSource dataSource, int maxLobChars, int maxLobBytes) {
        this(new NamedParameterJdbcTemplate(
                        Objects.requireNonNull(dataSource, "dataSource")),
                maxLobChars,
                maxLobBytes);
    }

    /**
     * 根据 NamedParameterJdbcTemplate 构造执行器。
     *
     * @param jdbcTemplate JDBC 模板，不可为 null
     */
    public NamedSqlExecutor(NamedParameterJdbcTemplate jdbcTemplate) {
        this(
                jdbcTemplate,
                ResultSetRowMapper.DEFAULT_MAX_LOB_CHARS,
                ResultSetRowMapper.DEFAULT_MAX_LOB_BYTES);
    }

    /**
     * 根据 NamedParameterJdbcTemplate 与自定义 LOB 大小限制构造执行器。
     *
     * @param jdbcTemplate JDBC 模板
     * @param maxLobChars CLOB 最大字符数
     * @param maxLobBytes BLOB 最大字节数
     */
    public NamedSqlExecutor(
            NamedParameterJdbcTemplate jdbcTemplate,
            int maxLobChars,
            int maxLobBytes) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.rowMapper = new ResultSetRowMapper(maxLobChars, maxLobBytes);
    }

    /**
     * 执行 SQL 查询（匿名数据集）。
     *
     * @param sql SQL 语句文本
     * @param parameters 解析后的命名参数集
     * @param queryTimeoutSeconds 超时秒数
     * @param maxRows 最大允许返回行数
     * @return 查询结果对象
     */
    public SqlQueryResult query(
            String sql,
            ResolvedSqlParameters parameters,
            int queryTimeoutSeconds,
            int maxRows) {
        return query(null, sql, parameters, queryTimeoutSeconds, maxRows);
    }

    /**
     * 执行指定数据集的 SQL 查询。
     *
     * @param datasetId 数据集 ID（用于异常诊断日志）
     * @param sql SQL 语句文本
     * @param parameters 解析后的命名参数集
     * @param queryTimeoutSeconds 超时秒数
     * @param maxRows 最大允许返回行数
     * @return 包含 Schema 与不可变行的 SqlQueryResult
     * @throws ReportException 如果超过最大行数（DATA-004）或 SQL 执行失败（SQL-004）
     */
    public SqlQueryResult query(
            String datasetId,
            String sql,
            ResolvedSqlParameters parameters,
            int queryTimeoutSeconds,
            int maxRows) {
        validateLimits(queryTimeoutSeconds, maxRows);
        Objects.requireNonNull(parameters, "parameters");
        PreparedStatementCreator statementCreator =
                statementCreator(requireSql(sql), parameters);
        try {
            return jdbcTemplate.getJdbcTemplate().execute(
                    statementCreator,
                    new PreparedStatementCallback<SqlQueryResult>() {
                        @Override
                        public SqlQueryResult doInPreparedStatement(
                                PreparedStatement statement) throws SQLException {
                            statement.setQueryTimeout(queryTimeoutSeconds);
                            // 设置最大行数比限制多 1，以准确检测是否超限
                            statement.setMaxRows(maxRows + 1);
                            List<DatasetRow> rows = new ArrayList<DatasetRow>();
                            try (ResultSet resultSet = statement.executeQuery()) {
                                com.xn.report.dataset.DatasetSchema schema =
                                        rowMapper.schema(resultSet.getMetaData());
                                while (resultSet.next()) {
                                    if (rows.size() == maxRows) {
                                        throw tooManyRows(datasetId, maxRows);
                                    }
                                    rows.add(rowMapper.map(resultSet));
                                }
                                return new SqlQueryResult(schema, rows);
                            }
                        }
                    });
        } catch (ReportException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ReportException(
                    ReportErrorCode.SQL_004,
                    "SQL execution failed"
                            + (datasetId == null ? "" : " for dataset " + datasetId),
                    null,
                    "DATASET_QUERY",
                    null,
                    datasetId,
                    exception);
        }
    }

    /**
     * 将命名参数 SQL 解析转换为 PreparedStatementCreator。
     */
    private static PreparedStatementCreator statementCreator(
            String sql, ResolvedSqlParameters parameters) {
        MapSqlParameterSource source = parameters.toMapSqlParameterSource();
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        String expandedSql =
                NamedParameterUtils.substituteNamedParameters(parsedSql, source);
        List<SqlParameter> declaredParameters =
                NamedParameterUtils.buildSqlParameterList(parsedSql, source);
        Object[] values =
                NamedParameterUtils.buildValueArray(parsedSql, source, null);
        PreparedStatementCreatorFactory factory =
                new PreparedStatementCreatorFactory(expandedSql, declaredParameters);
        return factory.newPreparedStatementCreator(values);
    }

    private static void validateLimits(int queryTimeoutSeconds, int maxRows) {
        if (queryTimeoutSeconds < 1) {
            throw new IllegalArgumentException(
                    "queryTimeoutSeconds must be positive");
        }
        if (maxRows < 1 || maxRows == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "maxRows must be between 1 and " + (Integer.MAX_VALUE - 1));
        }
    }

    private static String requireSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        return sql;
    }

    private static ReportException tooManyRows(String datasetId, int maxRows) {
        return new ReportException(
                ReportErrorCode.DATA_004,
                "Dataset " + (datasetId == null ? "<unknown>" : datasetId)
                        + " exceeded maximum row count " + maxRows,
                null,
                "DATASET_QUERY",
                null,
                datasetId,
                null);
    }
}
