package com.xn.report.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NamedSqlExecutorTest {

    @Test
    void expandsCollectionParametersAndAppliesStatementLimits()
            throws Exception {
        JdbcFixture fixture = new JdbcFixture(
                new String[] {"id"}, new int[] {Types.BIGINT});
        when(fixture.resultSet.next()).thenReturn(false);
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("names", Arrays.asList("A", "B"));
        values.put("minimumId", 7L);

        new NamedSqlExecutor(fixture.dataSource).query(
                "centerMonthly",
                "SELECT id FROM approval_record "
                        + "WHERE center_name IN (:names) AND id >= :minimumId",
                new ResolvedSqlParameters(values),
                9,
                4);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(fixture.connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("IN (?, ?)");
        verify(fixture.statement).setString(1, "A");
        verify(fixture.statement).setString(2, "B");
        verify(fixture.statement).setObject(3, 7L);
        verify(fixture.statement).setQueryTimeout(9);
        verify(fixture.statement).setMaxRows(5);
    }

    @Test
    void preservesMetadataWhenQueryReturnsNoRows() throws Exception {
        JdbcFixture fixture = new JdbcFixture(
                new String[] {"centerName", "avgHours"},
                new int[] {Types.VARCHAR, Types.DECIMAL});
        when(fixture.resultSet.next()).thenReturn(false);

        SqlQueryResult result = new NamedSqlExecutor(fixture.dataSource).query(
                "SELECT center_name AS centerName, "
                        + "avg_hours AS avgHours FROM approval_record",
                parameters(),
                5,
                10);

        assertThat(result.rows()).isEmpty();
        assertThat(result.schema().fieldNames())
                .containsExactly("centerName", "avgHours");
        assertThat(result.schema().typeOf("centerName"))
                .isEqualTo(String.class);
        assertThat(result.schema().typeOf("avgHours"))
                .isEqualTo(java.math.BigDecimal.class);
    }

    @Test
    void readsOneExtraRowThenFailsAtConfiguredMaximum() throws Exception {
        JdbcFixture fixture = new JdbcFixture(
                new String[] {"id"}, new int[] {Types.BIGINT});
        when(fixture.resultSet.next()).thenReturn(true, true);
        when(fixture.resultSet.getObject(1)).thenReturn(1L);

        assertThatThrownBy(() -> new NamedSqlExecutor(fixture.dataSource).query(
                "centerMonthly",
                "SELECT id FROM approval_record",
                parameters(),
                5,
                1))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_004))
                .hasMessageContaining("centerMonthly")
                .hasMessageContaining("1");

        verify(fixture.statement).setMaxRows(2);
        verify(fixture.resultSet).close();
        verify(fixture.statement).close();
        verify(fixture.connection).close();
    }

    @Test
    void wrapsJdbcFailuresAndClosesStatementAndConnection() throws Exception {
        JdbcFixture fixture = new JdbcFixture(
                new String[] {"id"}, new int[] {Types.BIGINT});
        when(fixture.statement.executeQuery())
                .thenThrow(new SQLException("database unavailable", "08001"));

        assertThatThrownBy(() -> new NamedSqlExecutor(fixture.dataSource).query(
                "centerMonthly",
                "SELECT id FROM approval_record",
                parameters(),
                5,
                10))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.SQL_004))
                .hasMessageContaining("centerMonthly")
                .hasRootCauseMessage("database unavailable");

        verify(fixture.statement).close();
        verify(fixture.connection, atLeastOnce()).close();
    }

    @Test
    void exposesConfiguredLobLimitsThroughExecutor() throws Exception {
        JdbcFixture fixture = new JdbcFixture(
                new String[] {"payload"}, new int[] {Types.VARBINARY});
        when(fixture.resultSet.next()).thenReturn(true, false);
        when(fixture.resultSet.getObject(1)).thenReturn(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> new NamedSqlExecutor(
                fixture.dataSource, 10, 2).query(
                        "SELECT payload FROM approval_record",
                        parameters(),
                        5,
                        10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload")
                .hasMessageContaining("2");
    }

    private static ResolvedSqlParameters parameters() {
        return new ResolvedSqlParameters(
                Collections.<String, Object>emptyMap());
    }

    private static final class JdbcFixture {

        private final DataSource dataSource = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement statement =
                mock(PreparedStatement.class);
        private final ResultSet resultSet = mock(ResultSet.class);

        private JdbcFixture(String[] labels, int[] columnTypes)
                throws Exception {
            DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
            ResultSetMetaData resultSetMetaData =
                    mock(ResultSetMetaData.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName())
                    .thenReturn("MySQL");
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
            when(resultSetMetaData.getColumnCount()).thenReturn(labels.length);
            for (int index = 0; index < labels.length; index++) {
                when(resultSetMetaData.getColumnLabel(index + 1))
                        .thenReturn(labels[index]);
                when(resultSetMetaData.getColumnType(index + 1))
                        .thenReturn(columnTypes[index]);
            }
        }
    }
}
