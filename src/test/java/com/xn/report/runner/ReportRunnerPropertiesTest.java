package com.xn.report.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.chart.ChartModel;
import com.xn.report.chart.ChartModelBuilder;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportDefinitionLoader;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.entry.ReportExecutionRequest;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ReportRunnerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "report-runner.root=.",
                    "report-runner.report-config=config/api-design-efficiency.yml",
                    "report-runner.config-root=config",
                    "report-runner.sql-root=config",
                    "report-runner.template-root=templates",
                    "report-runner.output-root=output",
                    "report-runner.temp-root=temp",
                    "report-runner.runtime.start-time=2026-01-01T00:00:00",
                    "report-runner.runtime.end-time-exclusive=2026-07-01T00:00:00",
                    "report-runner.runtime.baseline-start-time=2025-01-01T00:00:00",
                    "report-runner.runtime.baseline-end-time-exclusive=2026-01-01T00:00:00",
                    "report-runner.runtime.center-names[0]=开发一中心",
                    "report-runner.runtime.center-names[1]=研发中心",
                    "report-runner.runtime.report-period=2026年6月",
                    "report-runner.runtime.prepared-date=2026年7月23日");

    @Test
    void bindsRuntimeParametersAndBuildsExecutionRequest() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ReportRunnerProperties properties = context.getBean(ReportRunnerProperties.class);

            assertThat(properties.getRuntime().getStartTime())
                    .isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(properties.getRuntime().getCenterNames())
                    .containsExactly("开发一中心", "研发中心");

            ReportExecutionRequest request = properties.toRequest();
            assertThat(request.getReportConfigPath())
                    .isEqualTo(Paths.get("config/api-design-efficiency.yml").toAbsolutePath().normalize());
            assertThat(request.getRuntimeParameters().get("centerNames"))
                    .isEqualTo(Arrays.asList("开发一中心", "研发中心"));
            assertThat(request.getRuntimeParameters().get("reportPeriod"))
                    .isEqualTo("2026年6月");
            assertThat(request.getRuntimeParameters().get("preparedDate"))
                    .isEqualTo("2026年7月23日");
        });
    }

    @Test
    void exampleDatasetSqlFilesResolveUnderConfiguredSqlRoot() {
        contextRunner.run(context -> {
            ReportExecutionRequest request = context
                    .getBean(ReportRunnerProperties.class)
                    .toRequest();
            ReportDefinition definition = ReportDefinitionLoader.createDefault()
                    .load(request.getReportConfigPath());

            for (DatasetDefinition dataset : definition.getDatasets()) {
                assertThat(request.getSqlRoot().resolve(dataset.getSqlFile()).normalize())
                        .as("SQL file for dataset %s", dataset.getId())
                        .isRegularFile();
            }
        });
    }

    @Test
    void exampleChartsSeparateRowsThatShareAMonthAcrossBusinessGroups() {
        contextRunner.run(context -> {
            ReportExecutionRequest request = context
                    .getBean(ReportRunnerProperties.class)
                    .toRequest();
            ReportDefinition definition = ReportDefinitionLoader.createDefault()
                    .load(request.getReportConfigPath());
            ChartModelBuilder builder = new ChartModelBuilder();

            ChartDefinition centerChart = chart(definition, "centerEventCombo");
            DatasetResult centers = DatasetResult.list("centerMonthly", Arrays.asList(
                    DatasetRow.of(
                            "chartGroup", "开发一中心 / API设计",
                            "statMonth", "2026-01",
                            "overStandardCount", 1,
                            "withinStandardCount", 2,
                            "baselineHours", 20),
                    DatasetRow.of(
                            "chartGroup", "研发中心 / API设计",
                            "statMonth", "2026-01",
                            "overStandardCount", 2,
                            "withinStandardCount", 1,
                            "baselineHours", 30)));
            assertThat(builder.buildAll(centerChart, centers))
                    .extracting(ChartModel::getGroupKey)
                    .containsExactly("开发一中心 / API设计", "研发中心 / API设计");

            ChartDefinition trendChart = chart(definition, "approvalTrend");
            DatasetResult nodes = DatasetResult.list("departmentMonthly", Arrays.asList(
                    DatasetRow.of(
                            "nodeName", "API设计", "statMonth", "2026-01",
                            "avgHours", 20, "baselineHours", 18),
                    DatasetRow.of(
                            "nodeName", "数据库设计", "statMonth", "2026-01",
                            "avgHours", 30, "baselineHours", 25)));
            assertThat(builder.buildAll(trendChart, nodes))
                    .extracting(ChartModel::getGroupKey)
                    .containsExactly("API设计", "数据库设计");
        });
    }

    private static ChartDefinition chart(ReportDefinition definition, String id) {
        for (ChartDefinition chart : definition.getCharts()) {
            if (id.equals(chart.getId())) {
                return chart;
            }
        }
        throw new AssertionError("Missing chart " + id);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReportRunnerProperties.class)
    static class TestConfiguration {
    }
}
