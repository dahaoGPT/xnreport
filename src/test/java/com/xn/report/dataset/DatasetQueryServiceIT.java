package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.RootPathPolicy;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.config.definition.ParameterBindingDefinition;
import com.xn.report.config.definition.ParameterSource;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.sql.NamedSqlExecutor;
import com.xn.report.sql.ReadOnlySqlGuard;
import com.xn.report.sql.SqlFileRepository;
import com.xn.report.sql.SqlParameterResolver;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class DatasetQueryServiceIT {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withDatabaseName("xnreport")
                    .withUsername("xnreport")
                    .withPassword("xnreport");

    private static DatasetQueryService service;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void startDatabase() throws Exception {
        assumeTrue(
                dockerFixtureAvailable(),
                "Docker or required local MySQL 5.7 images are unavailable; IT skipped");
        MYSQL.start();
        createFixtureData();

        dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        service = createService(new BiConsumer<DatasetDefinition, DatasetResult>() {
            @Override
            public void accept(
                    DatasetDefinition definition, DatasetResult result) {
                // No-op production path.
            }
        });
    }

    private static DatasetQueryService createService(
            BiConsumer<DatasetDefinition, DatasetResult> executionObserver) {
        TransactionalDatasetQueryService target =
                new TransactionalDatasetQueryService(
                        new DatasetPlanner(),
                        new SqlFileRepository(new RootPathPolicy(
                                Paths.get("").toAbsolutePath())),
                        new ReadOnlySqlGuard(),
                        new SqlParameterResolver(),
                        new NamedSqlExecutor(dataSource),
                        new DatasetResultValidator(),
                        executionObserver);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(DatasetQueryService.class);
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(
                new DataSourceTransactionManager(dataSource));
        transactionInterceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource());
        transactionInterceptor.afterPropertiesSet();
        proxyFactory.addAdvice(transactionInterceptor);
        return (DatasetQueryService) proxyFactory.getProxy();
    }

    @AfterAll
    static void stopDatabase() {
        if (MYSQL.isRunning()) {
            MYSQL.stop();
        }
    }

    @Test
    void executesNamedListParametersAndReturnsAliasedRows() {
        DatasetDefinition dataset = centerMonthly();

        DatasetContext context = service.executeAll(
                TestFixtures.report(dataset),
                TestFixtures.parameters(
                        "startTime", LocalDateTime.of(2026, 1, 1, 0, 0),
                        "endTimeExclusive", LocalDateTime.of(2026, 7, 1, 0, 0),
                        "centerNames", Arrays.asList("开发一中心", "开发二中心")));

        assertThat(context.ids()).containsExactly("centerMonthly");
        assertThat(context.get("centerMonthly").list())
                .extracting(row -> row.get("centerName"))
                .containsExactly("开发一中心", "开发二中心");
        assertThat((BigDecimal) context.get("centerMonthly").list()
                .get(0).get("avgHours"))
                .isEqualByComparingTo("12.0000");
    }

    @Test
    void rejectsResultsThatExceedConfiguredMaximumRows() {
        DatasetDefinition dataset = centerMonthly();
        dataset.setMaxRows(1);

        assertThatThrownBy(() -> service.executeAll(
                TestFixtures.report(dataset),
                TestFixtures.parameters(
                        "startTime", LocalDateTime.of(2026, 1, 1, 0, 0),
                        "endTimeExclusive", LocalDateTime.of(2026, 7, 1, 0, 0),
                        "centerNames", Arrays.asList("开发一中心", "开发二中心"))))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.DATA_004))
                .hasMessageContaining("centerMonthly")
                .hasMessageContaining("1");
    }

    @Test
    void rejectsAmbiguousInlineAndFileSqlSources() {
        DatasetDefinition dataset = centerMonthly();
        dataset.setSql("SELECT 1 AS value");

        assertThatThrownBy(() -> service.executeAll(
                TestFixtures.report(dataset),
                TestFixtures.parameters(
                        "startTime", LocalDateTime.of(2026, 1, 1, 0, 0),
                        "endTimeExclusive", LocalDateTime.of(2026, 7, 1, 0, 0),
                        "centerNames", Arrays.asList("开发一中心"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void plansDependenciesResolvesDatasetParametersAndKeepsRepeatableReadSnapshot()
            throws Exception {
        resetSnapshotState("before", 1L);
        CountDownLatch updateRequested = new CountDownLatch(1);
        CountDownLatch updateCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> updaterFailure = new AtomicReference<Throwable>();
        AtomicBoolean transactionActive = new AtomicBoolean();
        AtomicBoolean readOnly = new AtomicBoolean();
        AtomicInteger isolation = new AtomicInteger();
        Thread updater = updateSnapshotAfterSignal(
                updateRequested, updateCommitted, updaterFailure);
        DatasetQueryService observedService = createService(
                new BiConsumer<DatasetDefinition, DatasetResult>() {
                    @Override
                    public void accept(
                            DatasetDefinition definition, DatasetResult result) {
                        if (!"snapshotBefore".equals(definition.getId())) {
                            return;
                        }
                        transactionActive.set(
                                TransactionSynchronizationManager
                                        .isActualTransactionActive());
                        try {
                            Connection transactional =
                                    DataSourceUtils.getConnection(dataSource);
                            readOnly.set(transactional.isReadOnly());
                            isolation.set(transactional.getTransactionIsolation());
                            updateRequested.countDown();
                            if (!updateCommitted.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError(
                                        "Independent update did not commit");
                            }
                            if (updaterFailure.get() != null) {
                                throw new AssertionError(
                                        "Independent update failed",
                                        updaterFailure.get());
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        } catch (java.sql.SQLException exception) {
                            throw new AssertionError(exception);
                        }
                    }
                });

        updater.start();
        DatasetContext context = observedService.executeAll(
                TestFixtures.report(snapshotAfter(), snapshotBefore()),
                TestFixtures.parameters("snapshotId", 1L));
        updater.join(10000L);

        assertThat(transactionActive.get()).isTrue();
        assertThat(readOnly.get()).isTrue();
        assertThat(isolation.get())
                .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
        assertThat(context.ids())
                .containsExactly("snapshotBefore", "snapshotAfter");
        assertThat(context.get("snapshotBefore").single().get("stateValue"))
                .isEqualTo("before");
        assertThat(context.get("snapshotAfter").single().get("observedValue"))
                .isEqualTo("before");
        assertThat(context.get("snapshotAfter").single().get("observedVersion"))
                .isEqualTo(1L);
        assertThat(readSnapshotValueFromNewConnection()).isEqualTo("after");
    }

    @Test
    void rollsBackTransactionBoundaryWhenDependentQueryFails() throws Exception {
        resetSnapshotState("stable", 1L);
        AtomicInteger completionStatus = new AtomicInteger(-1);
        DatasetQueryService observedService = createService(
                new BiConsumer<DatasetDefinition, DatasetResult>() {
                    @Override
                    public void accept(
                            DatasetDefinition definition, DatasetResult result) {
                        if ("snapshotBefore".equals(definition.getId())) {
                            TransactionSynchronizationManager.registerSynchronization(
                                    new TransactionSynchronization() {
                                        @Override
                                        public void afterCompletion(int status) {
                                            completionStatus.set(status);
                                        }
                                    });
                        }
                    }
                });

        assertThatThrownBy(() -> observedService.executeAll(
                TestFixtures.report(brokenSnapshotAfter(), snapshotBefore()),
                TestFixtures.parameters("snapshotId", 1L)))
                .isInstanceOfSatisfying(ReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.SQL_004));

        assertThat(completionStatus.get())
                .isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(readSnapshotValueFromNewConnection()).isEqualTo("stable");
    }

    private static DatasetDefinition centerMonthly() {
        DatasetDefinition dataset = TestFixtures.dataset(
                "centerMonthly",
                "src/test/resources/fixtures/sql/center-monthly.sql",
                null,
                new String[0]);
        dataset.setTimeoutSeconds(15);
        dataset.setMaxRows(10);
        Map<String, ParameterBindingDefinition> parameters =
                new LinkedHashMap<String, ParameterBindingDefinition>();
        parameters.put("startTime", runtime("startTime"));
        parameters.put("endTimeExclusive", runtime("endTimeExclusive"));
        parameters.put("centerNames", runtime("centerNames"));
        dataset.setParameters(parameters);
        Map<String, FieldDefinition> fields =
                new LinkedHashMap<String, FieldDefinition>();
        fields.put("centerName", field("STRING", true));
        fields.put("statMonth", field("STRING", true));
        fields.put("avgHours", field("DECIMAL", true));
        dataset.setExpectedFields(fields);
        return dataset;
    }

    private static DatasetDefinition snapshotBefore() {
        DatasetDefinition dataset = inlineDataset(
                "snapshotBefore",
                "SELECT state_value AS stateValue, "
                        + "state_version AS stateVersion "
                        + "FROM snapshot_state WHERE id = :id");
        dataset.setResultType(DatasetType.SINGLE);
        dataset.setParameters(Collections.singletonMap(
                "id", runtime("snapshotId")));
        Map<String, FieldDefinition> fields =
                new LinkedHashMap<String, FieldDefinition>();
        fields.put("stateValue", field("STRING", true));
        fields.put("stateVersion", field("INTEGER", true));
        dataset.setExpectedFields(fields);
        return dataset;
    }

    private static DatasetDefinition snapshotAfter() {
        DatasetDefinition dataset = inlineDataset(
                "snapshotAfter",
                "SELECT state_value AS observedValue, "
                        + "state_version AS observedVersion "
                        + "FROM snapshot_state "
                        + "WHERE id = :id AND state_version >= :expectedVersion",
                "snapshotBefore");
        dataset.setResultType(DatasetType.SINGLE);
        Map<String, ParameterBindingDefinition> parameters =
                new LinkedHashMap<String, ParameterBindingDefinition>();
        parameters.put("id", runtime("snapshotId"));
        parameters.put(
                "expectedVersion",
                dataset("snapshotBefore", "stateVersion"));
        dataset.setParameters(parameters);
        Map<String, FieldDefinition> fields =
                new LinkedHashMap<String, FieldDefinition>();
        fields.put("observedValue", field("STRING", true));
        fields.put("observedVersion", field("INTEGER", true));
        dataset.setExpectedFields(fields);
        return dataset;
    }

    private static DatasetDefinition inlineDataset(
            String id, String sql, String... dependsOn) {
        DatasetDefinition dataset = new DatasetDefinition();
        dataset.setId(id);
        dataset.setSheetName("Sheet-" + id);
        dataset.setSql(sql);
        dataset.setResultType(DatasetType.LIST);
        dataset.setDependsOn(Arrays.asList(dependsOn));
        return dataset;
    }

    private static DatasetDefinition brokenSnapshotAfter() {
        DatasetDefinition dataset = snapshotAfter();
        dataset.setId("brokenSnapshotAfter");
        dataset.setDependsOn(Collections.singletonList("snapshotBefore"));
        dataset.setSql(
                "SELECT missing_column AS brokenValue FROM snapshot_state "
                        + "WHERE id = :id AND state_version >= :expectedVersion");
        dataset.setExpectedFields(Collections.singletonMap(
                "brokenValue", field("STRING", true)));
        return dataset;
    }

    private static ParameterBindingDefinition runtime(String key) {
        ParameterBindingDefinition binding = new ParameterBindingDefinition();
        binding.setFrom(ParameterSource.RUNTIME);
        binding.setKey(key);
        return binding;
    }

    private static ParameterBindingDefinition dataset(
            String datasetId, String field) {
        ParameterBindingDefinition binding = new ParameterBindingDefinition();
        binding.setFrom(ParameterSource.DATASET);
        binding.setDataset(datasetId);
        binding.setField(field);
        return binding;
    }

    private static FieldDefinition field(String type, boolean required) {
        FieldDefinition field = new FieldDefinition();
        field.setType(type);
        field.setRequired(required);
        return field;
    }

    private static boolean dockerFixtureAvailable() {
        try {
            DockerClientFactory factory = DockerClientFactory.instance();
            if (!factory.isDockerAvailable()) {
                return false;
            }
            DockerClientFactory.lazyClient()
                    .inspectImageCmd("mysql:5.7.44").exec();
            DockerClientFactory.lazyClient()
                    .inspectImageCmd("testcontainers/ryuk:0.3.4").exec();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void createFixtureData() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE approval_record ("
                            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                            + "center_name VARCHAR(100) NOT NULL,"
                            + "approved_at DATETIME NOT NULL,"
                            + "approval_hours DECIMAL(12,4) NOT NULL)");
            statement.execute(
                    "INSERT INTO approval_record "
                            + "(center_name, approved_at, approval_hours) VALUES "
                            + "('开发一中心', '2026-01-10 09:00:00', 10.0000),"
                            + "('开发一中心', '2026-01-11 09:00:00', 14.0000),"
                            + "('开发二中心', '2026-02-12 09:00:00', 20.0000),"
                            + "('不应返回中心', '2026-03-12 09:00:00', 30.0000),"
                            + "('开发一中心', '2025-12-31 23:59:59', 99.0000)");
            statement.execute(
                    "CREATE TABLE snapshot_state ("
                            + "id BIGINT PRIMARY KEY,"
                            + "state_value VARCHAR(100) NOT NULL,"
                            + "state_version BIGINT NOT NULL)");
            statement.execute(
                    "INSERT INTO snapshot_state "
                            + "(id, state_value, state_version) "
                            + "VALUES (1, 'initial', 1)");
        }
    }

    private static Thread updateSnapshotAfterSignal(
            CountDownLatch updateRequested,
            CountDownLatch updateCommitted,
            AtomicReference<Throwable> failure) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!updateRequested.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError(
                                "First snapshot query did not complete");
                    }
                    try (Connection connection = DriverManager.getConnection(
                                    MYSQL.getJdbcUrl(),
                                    MYSQL.getUsername(),
                                    MYSQL.getPassword());
                            Statement statement = connection.createStatement()) {
                        connection.setAutoCommit(false);
                        statement.executeUpdate(
                                "UPDATE snapshot_state "
                                        + "SET state_value = 'after', "
                                        + "state_version = 2 WHERE id = 1");
                        connection.commit();
                    }
                } catch (Throwable exception) {
                    failure.set(exception);
                } finally {
                    updateCommitted.countDown();
                }
            }
        }, "snapshot-updater");
    }

    private static void resetSnapshotState(String value, long version)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                java.sql.PreparedStatement statement = connection.prepareStatement(
                        "UPDATE snapshot_state "
                                + "SET state_value = ?, state_version = ? "
                                + "WHERE id = 1")) {
            statement.setString(1, value);
            statement.setLong(2, version);
            statement.executeUpdate();
        }
    }

    private static String readSnapshotValueFromNewConnection() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement();
                java.sql.ResultSet resultSet = statement.executeQuery(
                        "SELECT state_value FROM snapshot_state WHERE id = 1")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
