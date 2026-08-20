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

@Component
@Validated
@ConfigurationProperties(prefix = "report-runner")
public class ReportRunnerProperties {

    @NotBlank
    private String root;

    @NotBlank
    private String reportConfig;

    @NotBlank
    private String configRoot;

    @NotBlank
    private String sqlRoot;

    @NotBlank
    private String templateRoot;

    @NotBlank
    private String outputRoot;

    @NotBlank
    private String tempRoot;

    @Valid
    @NotNull
    private RuntimeProperties runtime = new RuntimeProperties();

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

    public static class RuntimeProperties {

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime startTime;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime endTimeExclusive;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime baselineStartTime;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime baselineEndTimeExclusive;

        @NotEmpty
        private List<String> centerNames = new ArrayList<String>();

        @NotBlank
        private String reportPeriod;

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
