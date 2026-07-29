package com.xn.report.entry;

import com.xn.report.dataset.DatasetQueryService;
import com.xn.report.execution.DefaultReportPipeline;
import com.xn.report.execution.ReportPipeline;
import java.util.Objects;

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

    @Override
    public ReportExecutionResult generate(ReportExecutionRequest request) {
        return pipeline.execute(Objects.requireNonNull(request, "request"));
    }
}
