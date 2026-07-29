package com.xn.report.error;

import java.util.Objects;

public final class ReportErrorDetail {

    private final ReportErrorCode errorCode;
    private final String executionId;
    private final String stage;
    private final String reportCode;
    private final String componentId;
    private final String message;

    public ReportErrorDetail(
            ReportErrorCode errorCode,
            String executionId,
            String stage,
            String reportCode,
            String componentId,
            String message) {
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.executionId = executionId;
        this.stage = stage;
        this.reportCode = reportCode;
        this.componentId = componentId;
        this.message = Objects.requireNonNull(message, "message");
    }

    public ReportErrorCode getErrorCode() {
        return errorCode;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getStage() {
        return stage;
    }

    public String getReportCode() {
        return reportCode;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReportErrorDetail)) {
            return false;
        }
        ReportErrorDetail that = (ReportErrorDetail) other;
        return errorCode == that.errorCode
                && Objects.equals(executionId, that.executionId)
                && Objects.equals(stage, that.stage)
                && Objects.equals(reportCode, that.reportCode)
                && Objects.equals(componentId, that.componentId)
                && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                errorCode, executionId, stage, reportCode, componentId, message);
    }
}
