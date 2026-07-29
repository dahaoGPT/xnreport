package com.xn.report.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportExceptionTest {

    @Test
    void exposesCompleteStructuredErrorWithoutBreakingLegacyConstructors() {
        IllegalStateException cause = new IllegalStateException("disk");
        ReportErrorDetail detail = new ReportErrorDetail(
                ReportErrorCode.OUT_003,
                "exec-1",
                "PUBLISH",
                "efficiency",
                "chart-1",
                "publish failed");

        ReportException exception = new ReportException(detail, cause);

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.OUT_003);
        assertThat(exception.getExecutionId()).isEqualTo("exec-1");
        assertThat(exception.getStage()).isEqualTo("PUBLISH");
        assertThat(exception.getReportCode()).isEqualTo("efficiency");
        assertThat(exception.getComponentId()).isEqualTo("chart-1");
        assertThat(exception.getMessage()).isEqualTo("publish failed");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getDetail()).isEqualTo(detail);
        assertThat(new ReportException(ReportErrorCode.DATA_001, "legacy").getMessage())
                .isEqualTo("legacy");
    }

    @Test
    void definesEveryErrorCodeFromTheDesign() {
        assertThat(ReportErrorCode.values()).extracting(ReportErrorCode::getCode)
                .containsExactlyInAnyOrder(
                        "CFG-001", "CFG-002", "CFG-003", "CFG-004",
                        "SQL-001", "SQL-002", "SQL-003", "SQL-004",
                        "DATA-001", "DATA-002", "DATA-003", "DATA-004",
                        "RULE-001", "RULE-002", "TEXT-001",
                        "CHART-001", "CHART-002", "CHART-003",
                        "XLSX-001", "XLSX-002",
                        "DOCX-001", "DOCX-002", "DOCX-003",
                        "OUT-001", "OUT-002", "OUT-003");
    }
}
