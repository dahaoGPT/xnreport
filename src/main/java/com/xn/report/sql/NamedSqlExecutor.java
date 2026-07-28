package com.xn.report.sql;

import com.xn.report.dataset.DatasetRow;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
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

public final class NamedSqlExecutor {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ResultSetRowMapper rowMapper;

    public NamedSqlExecutor(DataSource dataSource) {
        this(new NamedParameterJdbcTemplate(
                Objects.requireNonNull(dataSource, "dataSource")));
    }

    public NamedSqlExecutor(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.rowMapper = new ResultSetRowMapper();
    }

    public List<DatasetRow> query(
            String sql,
            ResolvedSqlParameters parameters,
            int queryTimeoutSeconds,
            int maxRows) {
        return query(null, sql, parameters, queryTimeoutSeconds, maxRows);
    }

    public List<DatasetRow> query(
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
                    new PreparedStatementCallback<List<DatasetRow>>() {
                        @Override
                        public List<DatasetRow> doInPreparedStatement(
                                PreparedStatement statement) throws SQLException {
                            statement.setQueryTimeout(queryTimeoutSeconds);
                            statement.setMaxRows(maxRows + 1);
                            List<DatasetRow> rows = new ArrayList<DatasetRow>();
                            try (ResultSet resultSet = statement.executeQuery()) {
                                while (resultSet.next()) {
                                    if (rows.size() == maxRows) {
                                        throw tooManyRows(datasetId, maxRows);
                                    }
                                    rows.add(rowMapper.map(resultSet));
                                }
                            }
                            return Collections.unmodifiableList(rows);
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
