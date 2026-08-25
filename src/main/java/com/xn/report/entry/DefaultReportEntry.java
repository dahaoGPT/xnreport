package com.xn.report.entry;

import com.xn.report.dataset.DatasetQueryService;
import com.xn.report.dataset.DataSourceDatasetQueryServiceFactory;
import com.xn.report.execution.DefaultReportPipeline;
import com.xn.report.execution.ReportPipeline;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 效能报表生成组件默认门面入口实现。
 * <p>
 * 为宿主应用程序或单元测试提供轻量易用的单点生成入口：
 * <ul>
 *   <li>支持传入自定义 {@link DatasetQueryService}。</li>
 *   <li>支持传入标准 {@link DataSource}（自动包装为只读可重复读事务查询工厂）。</li>
 *   <li>支持传入 Spring {@link PlatformTransactionManager} 融入宿主事务环境。</li>
 * </ul>
 * </p>
 */
public final class DefaultReportEntry implements ReportEntry {

    private final ReportPipeline pipeline;

    public DefaultReportEntry(DatasetQueryService queryService) {
        this(DefaultReportPipeline.createDefault(
                Objects.requireNonNull(queryService, "queryService")));
    }

    public DefaultReportEntry(ReportPipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    public static DefaultReportEntry create(
            DatasetQueryService queryService) {
        return new DefaultReportEntry(queryService);
    }

    /**
     * 便捷静态工厂方法：基于 JDBC DataSource 创建报表生成入口。
     * <p>
     * SQL 脚本根据各请求的 sqlRoot 动态加载，查询运行在真正的只读（read-only）可重复读事务之中。
     * </p>
     *
     * @param dataSource 数据源
     * @return DefaultReportEntry 实例
     */
    public static DefaultReportEntry create(DataSource dataSource) {
        return new DefaultReportEntry(DefaultReportPipeline.createDefault(
                new DataSourceDatasetQueryServiceFactory(dataSource)));
    }

    /**
     * 便捷静态工厂方法：基于 JDBC DataSource 与 Spring PlatformTransactionManager 创建报表生成入口。
     *
     * @param dataSource 数据源
     * @param transactionManager Spring 事务管理器
     * @return DefaultReportEntry 实例
     */
    public static DefaultReportEntry create(
            DataSource dataSource,
            PlatformTransactionManager transactionManager) {
        return new DefaultReportEntry(DefaultReportPipeline.createDefault(
                new DataSourceDatasetQueryServiceFactory(
                        dataSource, transactionManager)));
    }

    @Override
    public ReportExecutionResult generate(ReportExecutionRequest request) {
        return pipeline.execute(Objects.requireNonNull(request, "request"));
    }
}
