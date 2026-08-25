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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 具备只读事务隔离与依赖拓扑调度的数据集查询核心服务实现。
 * <p>
 * 执行流水线：
 * <ol>
 *   <li>开启单次报表生成的 {@code REPEATABLE_READ} 只读事务，确保多次查询处于同一数据库一致性快照点。</li>
 *   <li>利用 {@link DatasetPlanner} 分析依赖并规划拓扑执行顺序。</li>
 *   <li>逐个加载 SQL（内联或外部文件），通过 {@link ReadOnlySqlGuard} 进行只读安全校验。</li>
 *   <li>解析 SQL 命名参数（支持前置数据集级联传参），通过 {@link NamedSqlExecutor} 安全执行。</li>
 *   <li>通过 {@link DatasetResultValidator} 进行 Schema 校验、空数据降级与类型强制约束，构建不可变 {@link DatasetResult} 并存入 {@link DatasetContext}。</li>
 * </ol>
 * </p>
 */
public class TransactionalDatasetQueryService
        implements DatasetQueryService {

    /** 默认 SQL 查询超时时间（60 秒）。 */
    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 60;

    /** 默认单数据集最大返回行数限制（10000 行）。 */
    public static final int DEFAULT_MAX_ROWS = 10000;

    /** 空操作执行观察者。 */
    private static final DatasetExecutionObserver NO_OP_OBSERVER =
            new DatasetExecutionObserver() {
                @Override
                public void afterExecution(
                        DatasetDefinition definition, DatasetResult result) {
                    // 默认空实现
                }
            };

    /** 依赖拓扑排序规划器。 */
    private final DatasetPlanner planner;

    /** SQL 外部脚本文件仓储。 */
    private final SqlFileRepository sqlFileRepository;

    /** 只读 SQL 安全检查防护器。 */
    private final ReadOnlySqlGuard sqlGuard;

    /** SQL 命名参数动态解析器。 */
    private final SqlParameterResolver parameterResolver;

    /** 命名参数 JDBC 执行器。 */
    private final NamedSqlExecutor executor;

    /** 数据集结果校验与降级治理器。 */
    private final DatasetResultValidator resultValidator;

    /** 执行生命周期观察者。 */
    private final DatasetExecutionObserver executionObserver;

    /**
     * 构造事务型数据集查询服务。
     */
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
                NO_OP_OBSERVER);
    }

    TransactionalDatasetQueryService(
            DatasetPlanner planner,
            SqlFileRepository sqlFileRepository,
            ReadOnlySqlGuard sqlGuard,
            SqlParameterResolver parameterResolver,
            NamedSqlExecutor executor,
            DatasetResultValidator resultValidator,
            DatasetExecutionObserver executionObserver) {
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
            executionObserver.afterExecution(dataset, result);
        }
        return context.build();
    }

    /**
     * 执行单个数据集并校验输出结果。
     */
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

    /**
     * 解析获取内嵌 SQL 或外部文件 SQL 文本。
     */
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
