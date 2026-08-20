package com.xn.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.entry.ExecutionStatus;
import com.xn.report.entry.ReportExecutionResult;
import com.xn.report.entry.ReportWarning;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportErrorDetail;
import com.xn.report.execution.ExecutionMetrics;
import com.xn.report.execution.ExecutionStage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class GenerateReportTest {

    @Test
    void printsSuccessfulArtifactsAndReturnsZero() throws Exception {
        ReportExecutionResult result = result(
                ExecutionStatus.SUCCESS,
                Collections.<ReportWarning>emptyList(),
                null,
                null,
                null);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = GenerateReport.printResult(
                result, utf8(stdout), utf8(stderr));

        assertThat(exitCode).isZero();
        assertThat(stdout.toString("UTF-8"))
                .contains("status=SUCCESS")
                .contains("excel=")
                .contains("word=")
                .contains("datasetRows.detail=3")
                .contains("durationMillis=1000");
        assertThat(stderr.toString("UTF-8")).isEmpty();
    }

    @Test
    void warningsRemainSuccessfulAndArePrinted() throws Exception {
        ReportWarning warning = new ReportWarning(
                "USE_DEFAULT", "dataset", "detail", "空数据已使用默认值");
        ReportExecutionResult result = result(
                ExecutionStatus.SUCCESS_WITH_WARNINGS,
                Collections.singletonList(warning),
                null,
                null,
                null);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = GenerateReport.printResult(
                result, utf8(stdout), utf8(new ByteArrayOutputStream()));

        assertThat(exitCode).isZero();
        assertThat(stdout.toString("UTF-8"))
                .contains("status=SUCCESS_WITH_WARNINGS")
                .contains("warning=空数据已使用默认值");
    }

    @Test
    void failedResultPrintsStructuredErrorAndReturnsOne() throws Exception {
        IllegalStateException failure = new IllegalStateException("query failed");
        ReportErrorDetail error = new ReportErrorDetail(
                ReportErrorCode.SQL_004,
                "execution-1",
                "QUERY",
                "api-design",
                "detail",
                "SQL执行失败");
        ReportExecutionResult result = result(
                ExecutionStatus.FAILED,
                Collections.<ReportWarning>emptyList(),
                error,
                failure,
                ExecutionStage.QUERY);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = GenerateReport.printResult(
                result, utf8(new ByteArrayOutputStream()), utf8(stderr));

        assertThat(exitCode).isEqualTo(1);
        assertThat(stderr.toString("UTF-8"))
                .contains("status=FAILED")
                .contains("failedStage=QUERY")
                .contains("errorCode=SQL-004")
                .contains("message=SQL执行失败")
                .contains("failureType=java.lang.IllegalStateException");
    }

    private static ReportExecutionResult result(
            ExecutionStatus status,
            java.util.List<ReportWarning> warnings,
            ReportErrorDetail error,
            Throwable failure,
            ExecutionStage failedStage) {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant finished = Instant.parse("2026-01-01T00:00:01Z");
        return new ReportExecutionResult(
                "execution-1",
                "api-design",
                status,
                started,
                finished,
                Paths.get("output/report.xlsx"),
                Paths.get("output/report.docx"),
                Collections.singletonMap("detail", 3L),
                warnings,
                error,
                failure,
                failedStage,
                ExecutionMetrics.begin(started).snapshot(finished));
    }

    private static PrintStream utf8(ByteArrayOutputStream output) throws Exception {
        return new PrintStream(output, true, "UTF-8");
    }
}
