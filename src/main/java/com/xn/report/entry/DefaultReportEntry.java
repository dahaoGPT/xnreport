package com.xn.report.entry;

import com.xn.report.dataset.DatasetQueryService;
import com.xn.report.dataset.DataSourceDatasetQueryServiceFactory;
import com.xn.report.execution.DefaultReportPipeline;
import com.xn.report.execution.ReportPipeline;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;

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
     * Practical single-entry factory. SQL files are resolved from each
     * request's sqlRoot and queries run in a real read-only repeatable-read
     * transaction.
     */
    public static DefaultReportEntry create(DataSource dataSource) {
        return new DefaultReportEntry(DefaultReportPipeline.createDefault(
                new DataSourceDatasetQueryServiceFactory(dataSource)));
    }

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
