package com.xn.report.dataset;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.sql.NamedSqlExecutor;
import com.xn.report.sql.ReadOnlySqlGuard;
import com.xn.report.sql.ResolvedSqlParameters;
import com.xn.report.sql.SqlFileRepository;
import com.xn.report.sql.SqlParameterResolver;
import com.xn.report.sql.SqlQueryResult;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalDatasetQueryService
        implements DatasetQueryService {

    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_MAX_ROWS = 10000;

    private final DatasetPlanner planner;
    private final SqlFileRepository sqlFileRepository;
    private final ReadOnlySqlGuard sqlGuard;
    private final SqlParameterResolver parameterResolver;
    private final NamedSqlExecutor executor;
    private final DatasetResultValidator resultValidator;
    private final BiConsumer<DatasetDefinition, DatasetResult> executionObserver;

    public TransactionalDatasetQueryService(
            DatasetPlanner planner,
            SqlFileRepository sqlFileRepository,
            ReadOnlySqlGuard sqlGuard,
            SqlParameterResolver parameterResolver,
            NamedSqlExecutor executor,
            DatasetResultValidator resultValidator) {
        this(
                planner,
                sqlFileRepository,
                sqlGuard,
                parameterResolver,
                executor,
                resultValidator,
                new BiConsumer<DatasetDefinition, DatasetResult>() {
                    @Override
                    public void accept(
                            DatasetDefinition definition,
                            DatasetResult result) {
                        // No-op by default.
                    }
                });
    }

    public TransactionalDatasetQueryService(
            DatasetPlanner planner,
            SqlFileRepository sqlFileRepository,
            ReadOnlySqlGuard sqlGuard,
            SqlParameterResolver parameterResolver,
            NamedSqlExecutor executor,
            DatasetResultValidator resultValidator,
            BiConsumer<DatasetDefinition, DatasetResult> executionObserver) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.sqlFileRepository =
                Objects.requireNonNull(sqlFileRepository, "sqlFileRepository");
        this.sqlGuard = Objects.requireNonNull(sqlGuard, "sqlGuard");
        this.parameterResolver =
                Objects.requireNonNull(parameterResolver, "parameterResolver");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.resultValidator =
                Objects.requireNonNull(resultValidator, "resultValidator");
        this.executionObserver =
                Objects.requireNonNull(executionObserver, "executionObserver");
    }

    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public DatasetContext executeAll(
            ReportDefinition definition,
            Map<String, Object> runtimeParameters) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(runtimeParameters, "runtimeParameters");
        DatasetContext.Builder context = DatasetContext.builder();
        for (DatasetDefinition dataset : planner.plan(definition.getDatasets())) {
            DatasetResult result = executeOne(
                    dataset, runtimeParameters, context.buildView());
            context.put(result);
            executionObserver.accept(dataset, result);
        }
        return context.build();
    }

    private DatasetResult executeOne(
            DatasetDefinition definition,
            Map<String, Object> runtimeParameters,
            DatasetContext contextSnapshot) {
        String sql = resolveSql(definition);
        sqlGuard.validate(sql);
        ResolvedSqlParameters parameters = parameterResolver.resolve(
                definition, runtimeParameters, contextSnapshot);
        SqlQueryResult queryResult = executor.query(
                definition.getId(),
                sql,
                parameters,
                positiveOrDefault(
                        definition.getTimeoutSeconds(),
                        DEFAULT_QUERY_TIMEOUT_SECONDS,
                        "timeoutSeconds"),
                positiveOrDefault(
                        definition.getMaxRows(),
                        DEFAULT_MAX_ROWS,
                        "maxRows"));
        return resultValidator.validate(definition, queryResult);
    }

    private String resolveSql(DatasetDefinition definition) {
        Objects.requireNonNull(definition, "dataset definition");
        boolean hasInlineSql = hasText(definition.getSql());
        boolean hasSqlFile = hasText(definition.getSqlFile());
        if (hasInlineSql == hasSqlFile) {
            throw new IllegalArgumentException(
                    "Exactly one of sql and sqlFile is required for dataset "
                            + definition.getId());
        }
        return hasInlineSql
                ? definition.getSql()
                : sqlFileRepository.read(definition.getSqlFile());
    }

    private static int positiveOrDefault(
            Integer configured, int defaultValue, String description) {
        if (configured == null) {
            return defaultValue;
        }
        if (configured.intValue() < 1) {
            throw new IllegalArgumentException(
                    description + " must be positive");
        }
        return configured.intValue();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
