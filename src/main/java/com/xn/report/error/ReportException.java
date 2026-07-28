package com.xn.report.error;

import java.util.Objects;

public class ReportException extends RuntimeException {

    private final ReportErrorCode errorCode;
    private final String executionId;
    private final String stage;
    private final String reportCode;
    private final String componentId;

    public ReportException(ReportErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ReportException(
            ReportErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, null, null, null, cause);
    }

    public ReportException(
            ReportErrorCode errorCode,
            String message,
            String executionId,
            String stage,
            String reportCode,
            String componentId,
            Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.executionId = executionId;
        this.stage = stage;
        this.reportCode = reportCode;
        this.componentId = componentId;
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
}
