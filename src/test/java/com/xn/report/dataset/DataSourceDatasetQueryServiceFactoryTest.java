package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.policy.EmptyDataPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.Collections;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DataSourceDatasetQueryServiceFactoryTest {

    @Test
    void usesRequestSqlRootDatasetOverrideAndReturnsPolicyWarnings()
            throws Exception {
        Path root = Files.createTempDirectory(
                java.nio.file.Paths.get("target"), "query-factory-");
        Files.write(root.resolve("metric.sql"),
                "select 1 as value".getBytes(StandardCharsets.UTF_8));
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation())
                .thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(resultSet.next()).thenReturn(false);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(metadata.getColumnType(1)).thenReturn(Types.INTEGER);

        DatasetDefinition dataset = new DatasetDefinition();
        dataset.setId("metric");
        dataset.setSheetName("metric");
        dataset.setSqlFile("metric.sql");
        dataset.setResultType(DatasetType.LIST);
        PolicyDefinition local = new PolicyDefinition();
        local.setEmptyData(EmptyDataPolicy.SKIP);
        dataset.setPolicies(local);
        ReportDefinition definition = new ReportDefinition();
        definition.setDatasets(Collections.singletonList(dataset));
        PolicyDefinition global = new PolicyDefinition();
        global.setEmptyData(EmptyDataPolicy.FAIL);
        definition.setPolicies(global);

        QueryOutcome outcome = new DataSourceDatasetQueryServiceFactory(
                dataSource).create(root).executeAllWithWarnings(
                definition, Collections.emptyMap());

        assertThat(outcome.getDatasets().get("metric").list()).isEmpty();
        assertThat(outcome.getWarnings()).extracting("action")
                .containsExactly("SKIP");
        verify(connection).prepareStatement("select 1 as value");
        verify(connection).commit();
    }
}
