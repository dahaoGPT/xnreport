package com.xn.report.execution;

import com.xn.report.analysis.AnalysisContext;
import com.xn.report.config.ReportDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.entry.ReportExecutionRequest;
import com.xn.report.entry.ReportWarning;
import com.xn.report.output.ExecutionWorkspace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 报表单次运行流水线全局上下文模型。
 * <p>
 * 贯穿整条执行管道（{@link DefaultReportPipeline}），管理请求参数、工作空间、当前阶段、配置模型、查询快照、分析上下文及累积的告警信息。
 * </p>
 */
public final class ExecutionContext {

    private final String executionId;
    private final ReportExecutionRequest request;
    private final ExecutionWorkspace workspace;
    private final ExecutionMetrics.Mutable metrics;
    private final List<ReportWarning> warnings =
            new ArrayList<ReportWarning>();
    private ExecutionStage stage = ExecutionStage.INITIALIZE;
    private ReportDefinition definition;
    private DatasetContext querySnapshot;
    private AnalysisContext analysisContext;

    public ExecutionContext(
            String executionId,
            ReportExecutionRequest request,
            ExecutionWorkspace workspace,
            ExecutionMetrics.Mutable metrics) {
        this.executionId = Objects.requireNonNull(executionId, "executionId");
        this.request = Objects.requireNonNull(request, "request");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public String getExecutionId() {
        return executionId;
    }

    public ReportExecutionRequest getRequest() {
        return request;
    }

    public ExecutionWorkspace getWorkspace() {
        return workspace;
    }

    public ExecutionMetrics.Mutable getMutableMetrics() {
        return metrics;
    }

    public ExecutionStage getStage() {
        return stage;
    }

    public void setStage(ExecutionStage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public ReportDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(ReportDefinition definition) {
        this.definition = definition;
    }

    public DatasetContext getQuerySnapshot() {
        return querySnapshot;
    }

    public void setQuerySnapshot(DatasetContext querySnapshot) {
        this.querySnapshot = querySnapshot;
    }

    public AnalysisContext getAnalysisContext() {
        return analysisContext;
    }

    public void setAnalysisContext(AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
    }

    public void addWarning(ReportWarning warning) {
        warnings.add(Objects.requireNonNull(warning, "warning"));
    }

    public List<ReportWarning> getWarnings() {
        return Collections.unmodifiableList(
                new ArrayList<ReportWarning>(warnings));
    }
}
