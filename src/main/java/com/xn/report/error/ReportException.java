package com.xn.report.error;

import java.util.Objects;

public class ReportException extends RuntimeException {

    private final ReportErrorDetail detail;

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
        this(new ReportErrorDetail(
                errorCode,
                executionId,
                stage,
                reportCode,
                componentId,
                message), cause);
    }

    public ReportException(ReportErrorDetail detail) {
        this(detail, null);
    }

    public ReportException(ReportErrorDetail detail, Throwable cause) {
        super(Objects.requireNonNull(detail, "detail").getMessage(), cause);
        this.detail = detail;
    }

    public ReportErrorCode getErrorCode() {
        return detail.getErrorCode();
    }

    public String getExecutionId() {
        return detail.getExecutionId();
    }

    public String getStage() {
        return detail.getStage();
    }

    public String getReportCode() {
        return detail.getReportCode();
    }

    public String getComponentId() {
        return detail.getComponentId();
    }

    public ReportErrorDetail getDetail() {
        return detail;
    }
}
