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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

    @BeforeAll
    static void startDatabase() throws Exception {
        assumeTrue(
                dockerFixtureAvailable(),
                "Docker or required local MySQL 5.7 images are unavailable; IT skipped");
        try {
            MYSQL.start();
        } catch (RuntimeException exception) {
            assumeTrue(
                    false,
                    "MySQL 5.7 container is unavailable; IT skipped: "
                            + exception.getClass().getSimpleName());
        }
        createFixtureData();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        TransactionalDatasetQueryService target =
                new TransactionalDatasetQueryService(
                        new DatasetPlanner(),
                        new SqlFileRepository(new RootPathPolicy(
                                Paths.get("").toAbsolutePath())),
                        new ReadOnlySqlGuard(),
                        new SqlParameterResolver(),
                        new NamedSqlExecutor(dataSource),
                        new DatasetResultValidator());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(DatasetQueryService.class);
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(
                new DataSourceTransactionManager(dataSource));
        transactionInterceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource());
        transactionInterceptor.afterPropertiesSet();
        proxyFactory.addAdvice(transactionInterceptor);
        service = (DatasetQueryService) proxyFactory.getProxy();
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

    private static ParameterBindingDefinition runtime(String key) {
        ParameterBindingDefinition binding = new ParameterBindingDefinition();
        binding.setFrom(ParameterSource.RUNTIME);
        binding.setKey(key);
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
            factory.client().inspectImageCmd("mysql:5.7.44").exec();
            factory.client().inspectImageCmd("testcontainers/ryuk:0.3.4").exec();
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
        }
    }
}
