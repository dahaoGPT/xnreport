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

/**
 * 命令行报表生成入口类。
 * <p>
 * 该类通过 Spring Boot 无 Web 环境启动容器，读取 {@link ReportRunnerProperties} 运行配置与数据源，
 * 触发 {@link DefaultReportEntry} 依次生成 Excel 报表与 Word 报表，并将执行状态、耗时、输出路径、
 * 数据行数及警告/错误信息格式化输出到标准输出或标准错误流中。
 * </p>
 */
public final class GenerateReport {

    /** 私有构造函数，防止工具类实例化。 */
    private GenerateReport() {
    }

    /**
     * 命令行主程序入口。
     *
     * @param args 命令行参数（通常包含 Spring Boot 配置项覆写）
     */
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * 执行报表生成流程并返回退出状态码。
     *
     * @param args 命令行参数
     * @param out 标准输出流（用于输出成功状态、生成文件路径、耗时等）
     * @param err 错误输出流（用于输出失败状态与异常详情）
     * @return 0 表示生成成功（包括带警告成功），1 表示生成失败或抛出异常
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        ConfigurableApplicationContext context = null;
        try {
            // 启动非 Web 类型的 Spring Boot 应用程序上下文
            context = new SpringApplicationBuilder(XnReportApplication.class)
                    .web(WebApplicationType.NONE)
                    .registerShutdownHook(false)
                    .run(args);

            // 获取运行参数与数据源
            ReportRunnerProperties properties = context.getBean(ReportRunnerProperties.class);
            DataSource dataSource = context.getBean(DataSource.class);

            // 构造报表入口并执行生成流程
            ReportExecutionResult result = DefaultReportEntry.create(dataSource)
                    .generate(properties.toRequest());

            // 打印生成结果并返回状态码
            return printResult(result, out, err);
        } catch (Throwable failure) {
            // 捕获未受检异常并格式化输出到错误流
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

    /**
     * 格式化并打印报表执行结果。
     *
     * @param result 报表执行结果对象
     * @param out 标准输出流
     * @param err 错误输出流
     * @return 0 成功，1 失败
     */
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

        // 输出成功信息：状态、文件路径、数据集行数、总耗时及警告信息
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

    /**
     * 将 null 转换为安全空字符串。
     *
     * @param message 待转换的异常消息
     * @return 非 null 字符串
     */
    private static String safeMessage(String message) {
        return message == null ? "" : message;
    }
}
