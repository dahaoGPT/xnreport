package com.xn.report.entry;

import com.xn.report.error.ReportErrorDetail;
import com.xn.report.execution.ExecutionMetrics;
import com.xn.report.execution.ExecutionStage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReportExecutionResult {

    private final String executionId;
    private final String reportCode;
    private final ExecutionStatus status;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final Path excelPath;
    private final Path wordPath;
    private final Map<String, Long> datasetRowCounts;
    private final List<ReportWarning> warnings;
    private final ReportErrorDetail error;
    private final Throwable failure;
    private final ExecutionStage failedStage;
    private final ExecutionMetrics metrics;

    public ReportExecutionResult(
            String executionId,
            String reportCode,
            ExecutionStatus status,
            Instant startedAt,
            Instant finishedAt,
            Path excelPath,
            Path wordPath,
            Map<String, Long> datasetRowCounts,
            List<ReportWarning> warnings,
            ReportErrorDetail error,
            Throwable failure,
            ExecutionStage failedStage,
            ExecutionMetrics metrics) {
        this.executionId = Objects.requireNonNull(executionId, "executionId");
        this.reportCode = reportCode;
        this.status = Objects.requireNonNull(status, "status");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        this.excelPath = normalize(excelPath);
        this.wordPath = normalize(wordPath);
        this.datasetRowCounts = Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(
                        datasetRowCounts == null
                                ? Collections.<String, Long>emptyMap()
                                : datasetRowCounts));
        this.warnings = Collections.unmodifiableList(
                new ArrayList<ReportWarning>(
                        warnings == null
                                ? Collections.<ReportWarning>emptyList()
                                : warnings));
        this.error = error;
        this.failure = failure;
        this.failedStage = failedStage;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getReportCode() {
        return reportCode;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Path getExcelPath() {
        return excelPath;
    }

    public Path getWordPath() {
        return wordPath;
    }

    public Map<String, Long> getDatasetRowCounts() {
        return datasetRowCounts;
    }

    public List<ReportWarning> getWarnings() {
        return warnings;
    }

    public ReportErrorDetail getError() {
        return error;
    }

    public Throwable getFailure() {
        return failure;
    }

    public ExecutionStage getFailedStage() {
        return failedStage;
    }

    public ExecutionMetrics getMetrics() {
        return metrics;
    }

    public long getTotalDurationMillis() {
        return metrics.getTotalDurationMillis();
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }
}
