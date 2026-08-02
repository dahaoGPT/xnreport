package com.xn.report.dataset;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.RootPathPolicy;
import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.policy.PolicyExecutionBridge;
import com.xn.report.policy.PolicyResolver;
import com.xn.report.policy.ReportWarning;
import com.xn.report.sql.NamedSqlExecutor;
import com.xn.report.sql.ReadOnlySqlGuard;
import com.xn.report.sql.SqlFileRepository;
import com.xn.report.sql.SqlParameterResolver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Request-scoped SQL repository and report-scoped policy query factory. */
public final class DataSourceDatasetQueryServiceFactory
        implements DatasetQueryServiceFactory {
    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    public DataSourceDatasetQueryServiceFactory(DataSource dataSource) {
        this(dataSource, new DataSourceTransactionManager(
                Objects.requireNonNull(dataSource, "dataSource")));
    }

    public DataSourceDatasetQueryServiceFactory(
            DataSource dataSource,
            PlatformTransactionManager transactionManager) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.transactionManager = Objects.requireNonNull(
                transactionManager, "transactionManager");
    }

    @Override
    public DatasetQueryService create(final Path sqlRoot) {
        Objects.requireNonNull(sqlRoot, "sqlRoot");
        return new DatasetQueryService() {
            @Override
            public DatasetContext executeAll(
                    ReportDefinition definition,
                    Map<String, Object> runtimeParameters) {
                return executeAllWithWarnings(definition, runtimeParameters)
                        .getDatasets();
            }

            @Override
            public QueryOutcome executeAllWithWarnings(
                    final ReportDefinition definition,
                    final Map<String, Object> runtimeParameters) {
                final List<ReportWarning> warnings =
                        new ArrayList<ReportWarning>();
                PolicyDefinition reportPolicies = definition.getPolicies();
                DatasetResultValidator resultValidator =
                        new DatasetResultValidator(
                                new PolicyExecutionBridge(new PolicyResolver(
                                        PolicyDefinition.systemDefaults(),
                                        warnings::add)),
                                reportPolicies);
                final TransactionalDatasetQueryService target =
                        new TransactionalDatasetQueryService(
                                new DatasetPlanner(),
                                new SqlFileRepository(new RootPathPolicy(sqlRoot)),
                                new ReadOnlySqlGuard(),
                                new SqlParameterResolver(),
                                new NamedSqlExecutor(dataSource),
                                resultValidator);
                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);
                transaction.setReadOnly(true);
                transaction.setIsolationLevel(
                        TransactionDefinition.ISOLATION_REPEATABLE_READ);
                DatasetContext datasets = transaction.execute(
                        status -> target.executeAll(
                                definition, runtimeParameters));
                return new QueryOutcome(datasets, warnings);
            }
        };
    }
}
