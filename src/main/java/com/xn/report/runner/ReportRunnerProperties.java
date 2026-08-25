package com.xn.report.runner;

import com.xn.report.entry.ReportExecutionRequest;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 报表运行器外部配置绑定类（前缀：{@code report-runner}）。
 * <p>
 * 从 application.yml / properties 中读取根路径、配置/SQL/模板/输出/临时目录路径，
 * 以及报表执行所需的动态运行时参数（如时间区间、中心列表、统计周期等），并将其组装转换为标准请求对象 {@link ReportExecutionRequest}。
 * </p>
 */
@Component
@Validated
@ConfigurationProperties(prefix = "report-runner")
public class ReportRunnerProperties {

    /** 报表工程工作根目录。 */
    @NotBlank
    private String root;

    /** 目标报表主配置文件相对或绝对路径（如 config/api-design-efficiency.yml）。 */
    @NotBlank
    private String reportConfig;

    /** 配置文件根目录。 */
    @NotBlank
    private String configRoot;

    /** SQL 脚本文件根目录。 */
    @NotBlank
    private String sqlRoot;

    /** 模板（Excel/Word）根目录。 */
    @NotBlank
    private String templateRoot;

    /** 最终报表文件输出目录。 */
    @NotBlank
    private String outputRoot;

    /** 生成过程临时工作目录。 */
    @NotBlank
    private String tempRoot;

    /** 运行时动态参数配置。 */
    @Valid
    @NotNull
    private RuntimeProperties runtime = new RuntimeProperties();

    /**
     * 将外部配置属性解析组装为报表执行请求对象 {@link ReportExecutionRequest}。
     *
     * @return 校验并规范化后的报表执行请求
     */
    public ReportExecutionRequest toRequest() {
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("startTime", runtime.getStartTime());
        parameters.put("endTimeExclusive", runtime.getEndTimeExclusive());
        parameters.put("baselineStartTime", runtime.getBaselineStartTime());
        parameters.put("baselineEndTimeExclusive", runtime.getBaselineEndTimeExclusive());
        parameters.put("centerNames", new ArrayList<String>(runtime.getCenterNames()));
        parameters.put("reportPeriod", runtime.getReportPeriod());
        parameters.put("preparedDate", runtime.getPreparedDate());
        return new ReportExecutionRequest(
                base.resolve(Paths.get(reportConfig)).normalize(),
                base.resolve(Paths.get(configRoot)).normalize(),
                base.resolve(Paths.get(sqlRoot)).normalize(),
                base.resolve(Paths.get(templateRoot)).normalize(),
                base.resolve(Paths.get(outputRoot)).normalize(),
                base.resolve(Paths.get(tempRoot)).normalize(),
                parameters);
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getReportConfig() {
        return reportConfig;
    }

    public void setReportConfig(String reportConfig) {
        this.reportConfig = reportConfig;
    }

    public String getConfigRoot() {
        return configRoot;
    }

    public void setConfigRoot(String configRoot) {
        this.configRoot = configRoot;
    }

    public String getSqlRoot() {
        return sqlRoot;
    }

    public void setSqlRoot(String sqlRoot) {
        this.sqlRoot = sqlRoot;
    }

    public String getTemplateRoot() {
        return templateRoot;
    }

    public void setTemplateRoot(String templateRoot) {
        this.templateRoot = templateRoot;
    }

    public String getOutputRoot() {
        return outputRoot;
    }

    public void setOutputRoot(String outputRoot) {
        this.outputRoot = outputRoot;
    }

    public String getTempRoot() {
        return tempRoot;
    }

    public void setTempRoot(String tempRoot) {
        this.tempRoot = tempRoot;
    }

    public RuntimeProperties getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeProperties runtime) {
        this.runtime = runtime;
    }

    /**
     * 运行时动态入参配置类。
     */
    public static class RuntimeProperties {

        /** 统计区间开始时间（包含）。 */
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime startTime;

        /** 统计区间结束时间（不包含）。 */
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime endTimeExclusive;

        /** 基线/对比区间开始时间（包含）。 */
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime baselineStartTime;

        /** 基线/对比区间结束时间（不包含）。 */
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime baselineEndTimeExclusive;

        /** 需要统计的研发中心名称列表。 */
        @NotEmpty
        private List<String> centerNames = new ArrayList<String>();

        /** 报表周期描述文字（如 "2026年6月"）。 */
        @NotBlank
        private String reportPeriod;

        /** 报表编制日期描述文字（如 "2026年7月23日"）。 */
        @NotBlank
        private String preparedDate;

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        public LocalDateTime getEndTimeExclusive() {
            return endTimeExclusive;
        }

        public void setEndTimeExclusive(LocalDateTime endTimeExclusive) {
            this.endTimeExclusive = endTimeExclusive;
        }

        public LocalDateTime getBaselineStartTime() {
            return baselineStartTime;
        }

        public void setBaselineStartTime(LocalDateTime baselineStartTime) {
            this.baselineStartTime = baselineStartTime;
        }

        public LocalDateTime getBaselineEndTimeExclusive() {
            return baselineEndTimeExclusive;
        }

        public void setBaselineEndTimeExclusive(LocalDateTime baselineEndTimeExclusive) {
            this.baselineEndTimeExclusive = baselineEndTimeExclusive;
        }

        public List<String> getCenterNames() {
            return centerNames;
        }

        public void setCenterNames(List<String> centerNames) {
            this.centerNames = centerNames;
        }

        public String getReportPeriod() {
            return reportPeriod;
        }

        public void setReportPeriod(String reportPeriod) {
            this.reportPeriod = reportPeriod;
        }

        public String getPreparedDate() {
            return preparedDate;
        }

        public void setPreparedDate(String preparedDate) {
            this.preparedDate = preparedDate;
        }
    }
}
