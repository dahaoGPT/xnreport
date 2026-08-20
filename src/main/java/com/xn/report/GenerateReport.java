package com.xn.report;

import com.xn.report.entry.DefaultReportEntry;
import com.xn.report.entry.ReportExecutionResult;
import com.xn.report.entry.ReportWarning;
import com.xn.report.runner.ReportRunnerProperties;
import java.io.PrintStream;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Command-line entry that generates one Excel report followed by one Word report. */
public final class GenerateReport {

    private GenerateReport() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(XnReportApplication.class)
                    .web(WebApplicationType.NONE)
                    .registerShutdownHook(false)
                    .run(args);
            ReportRunnerProperties properties = context.getBean(ReportRunnerProperties.class);
            DataSource dataSource = context.getBean(DataSource.class);
            ReportExecutionResult result = DefaultReportEntry.create(dataSource)
                    .generate(properties.toRequest());
            return printResult(result, out, err);
        } catch (Throwable failure) {
            err.println("status=FAILED");
            err.println("errorType=" + failure.getClass().getName());
            err.println("message=" + safeMessage(failure.getMessage()));
            return 1;
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    static int printResult(ReportExecutionResult result, PrintStream out, PrintStream err) {
        if (result.getStatus() == com.xn.report.entry.ExecutionStatus.FAILED) {
            err.println("status=" + result.getStatus());
            if (result.getFailedStage() != null) {
                err.println("failedStage=" + result.getFailedStage());
            }
            if (result.getError() != null) {
                err.println("errorCode=" + result.getError().getErrorCode().getCode());
                err.println("message=" + safeMessage(result.getError().getMessage()));
            } else if (result.getFailure() != null) {
                err.println("message=" + safeMessage(result.getFailure().getMessage()));
            }
            if (result.getFailure() != null) {
                err.println("failureType=" + result.getFailure().getClass().getName());
            }
            return 1;
        }

        out.println("status=" + result.getStatus());
        if (result.getExcelPath() != null) {
            out.println("excel=" + result.getExcelPath());
        }
        if (result.getWordPath() != null) {
            out.println("word=" + result.getWordPath());
        }
        for (Map.Entry<String, Long> entry : result.getDatasetRowCounts().entrySet()) {
            out.println("datasetRows." + entry.getKey() + "=" + entry.getValue());
        }
        out.println("durationMillis=" + result.getTotalDurationMillis());
        for (ReportWarning warning : result.getWarnings()) {
            out.println("warning=" + warning.getMessage());
        }
        return 0;
    }

    private static String safeMessage(String message) {
        return message == null ? "" : message;
    }
}
