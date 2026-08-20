# GenerateReport Executable Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增从 `application.yml` 读取全部参数的 `GenerateReport.java`，使打包后的 Spring Boot JAR 可通过 `java -jar` 直接生成 Excel 和 Word。

**Architecture:** 使用独立的 `ReportRunnerProperties` 表达目录和运行参数，`GenerateReport` 以非 Web 模式启动现有 `XnReportApplication`，从容器取得 `DataSource` 后调用 `DefaultReportEntry`。Spring Boot 插件明确将 `GenerateReport` 写为可执行 JAR 主类，现有报表流水线保持不变。

**Tech Stack:** Java 8、Spring Boot 2.7.18、Spring JDBC、Spring Boot Configuration Properties、JUnit 5、AssertJ、MySQL 5.7。

---

## 文件结构

- Create: `src/main/java/com/xn/report/runner/ReportRunnerProperties.java` — 类型化绑定 `report-runner` 并构造执行请求。
- Create: `src/main/java/com/xn/report/GenerateReport.java` — 可执行 main、Spring 生命周期、结果输出和退出码。
- Create: `src/test/java/com/xn/report/runner/ReportRunnerPropertiesTest.java` — 配置绑定与请求构造测试。
- Create: `src/test/java/com/xn/report/GenerateReportTest.java` — 成功、警告、失败结果输出测试。
- Modify: `src/main/resources/application.yml` — 数据源和完整运行参数。
- Modify: `pom.xml` — 指定 Spring Boot 可执行主类。
- Modify: `docs/使用手册.md` — 更新为源码入口和 `java -jar` 实际用法。

### Task 1: 类型化绑定报表运行配置

**Files:**
- Create: `src/test/java/com/xn/report/runner/ReportRunnerPropertiesTest.java`
- Create: `src/main/java/com/xn/report/runner/ReportRunnerProperties.java`

- [ ] **Step 1: 写配置绑定和请求构造失败测试**

测试使用 `ApplicationContextRunner` 和 `@EnableConfigurationProperties`，至少覆盖 ISO 时间、中心列表、中文字符串及根路径解析：

```java
package com.xn.report.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.entry.ReportExecutionRequest;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ReportRunnerPropertiesTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
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
    void bindsYamlShapeAndBuildsExecutionRequest() {
        contextRunner.run(context -> {
            ReportRunnerProperties properties =
                    context.getBean(ReportRunnerProperties.class);
            ReportExecutionRequest request = properties.toRequest();

            assertThat(properties.getRuntime().getStartTime())
                    .isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(request.getReportConfigPath())
                    .isEqualTo(Paths.get(".").toAbsolutePath().normalize()
                            .resolve("config/api-design-efficiency.yml"));
            assertThat(request.getRuntimeParameters().get("centerNames"))
                    .asList().containsExactly("开发一中心", "研发中心");
            assertThat(request.getRuntimeParameters())
                    .containsEntry("reportPeriod", "2026年6月")
                    .containsEntry("preparedDate", "2026年7月23日");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReportRunnerProperties.class)
    static class TestConfiguration {}
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn -Dtest=ReportRunnerPropertiesTest test
```

Expected: 编译失败，提示 `ReportRunnerProperties` 不存在。

- [ ] **Step 3: 实现配置类**

创建 `@Component`、`@ConfigurationProperties(prefix = "report-runner")`、`@Validated` 类。顶层字段使用 `Path`，嵌套运行参数使用 `LocalDateTime`、`List<String>` 和 `String`；使用 `@NotNull`、`@NotBlank`、`@NotEmpty` 校验。`toRequest()` 必须构造以下参数映射：

```java
Map<String, Object> values = new LinkedHashMap<String, Object>();
values.put("startTime", runtime.getStartTime());
values.put("endTimeExclusive", runtime.getEndTimeExclusive());
values.put("baselineStartTime", runtime.getBaselineStartTime());
values.put("baselineEndTimeExclusive", runtime.getBaselineEndTimeExclusive());
values.put("centerNames", runtime.getCenterNames());
values.put("reportPeriod", runtime.getReportPeriod());
values.put("preparedDate", runtime.getPreparedDate());
```

根目录解析方式固定为：

```java
Path base = root.toAbsolutePath().normalize();
return new ReportExecutionRequest(
        base.resolve(reportConfig).normalize(),
        base.resolve(configRoot).normalize(),
        base.resolve(sqlRoot).normalize(),
        base.resolve(templateRoot).normalize(),
        base.resolve(outputRoot).normalize(),
        base.resolve(tempRoot).normalize(),
        values);
```

所有字段提供 JavaBean getter/setter，嵌套 `RuntimeProperties` 初始化为非 null 实例，中心列表初始化为空列表。

- [ ] **Step 4: 运行配置测试确认通过**

Run: `mvn -Dtest=ReportRunnerPropertiesTest test`

Expected: 1 test, 0 failures, 0 errors。

- [ ] **Step 5: 提交配置绑定**

```powershell
git add src/main/java/com/xn/report/runner/ReportRunnerProperties.java src/test/java/com/xn/report/runner/ReportRunnerPropertiesTest.java
git commit -m "feat: bind executable report configuration"
```

### Task 2: 实现 GenerateReport 入口和退出状态

**Files:**
- Create: `src/test/java/com/xn/report/GenerateReportTest.java`
- Create: `src/main/java/com/xn/report/GenerateReport.java`

- [ ] **Step 1: 写结果处理失败测试**

创建结果工厂，使用 `ExecutionMetrics.begin(started).snapshot(finished)` 构造真实 `ReportExecutionResult`。测试：

```java
@Test
void returnsZeroAndPrintsPublishedPathsForSuccess() {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    int code = GenerateReport.printResult(
            result(ExecutionStatus.SUCCESS, Collections.emptyList(), null),
            new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));
    assertThat(code).isZero();
    assertThat(stdout.toString()).contains("status=SUCCESS", "excel=", "word=");
}

@Test
void returnsZeroAndPrintsWarnings() {
    ReportWarning warning = new ReportWarning(
            "WARN_AND_SKIP", "dataset", "personAnnual", "测试警告");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    int code = GenerateReport.printResult(
            result(ExecutionStatus.SUCCESS_WITH_WARNINGS,
                    Collections.singletonList(warning), null),
            new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));
    assertThat(code).isZero();
    assertThat(stdout.toString()).contains("warning=测试警告");
}

@Test
void returnsOneAndPrintsStructuredFailure() {
    ReportErrorDetail error = new ReportErrorDetail(
            ReportErrorCode.SQL_004, "exec-1", "QUERY",
            "api-design-efficiency", "personAnnual", "查询失败");
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    int code = GenerateReport.printResult(
            result(ExecutionStatus.FAILED, Collections.emptyList(), error),
            new PrintStream(new ByteArrayOutputStream()), new PrintStream(stderr));
    assertThat(code).isOne();
    assertThat(stderr.toString()).contains("status=FAILED", "errorCode=SQL-004", "查询失败");
}
```

结果工厂给成功结果设置 `output/report.xlsx` 和 `output/report.docx`，给失败结果设置 `ExecutionStage.QUERY`，数据集行数包含 `personAnnual=10`。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=GenerateReportTest test`

Expected: 编译失败，提示 `GenerateReport` 不存在。

- [ ] **Step 3: 实现可执行入口**

`GenerateReport` 为 `final` 类，包含：

```java
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
        ReportRunnerProperties properties =
                context.getBean(ReportRunnerProperties.class);
        DataSource dataSource = context.getBean(DataSource.class);
        ReportExecutionResult result = DefaultReportEntry.create(dataSource)
                .generate(properties.toRequest());
        return printResult(result, out, err);
    } catch (Throwable failure) {
        err.println("status=FAILED");
        err.println("errorType=" + failure.getClass().getName());
        err.println("message=" + String.valueOf(failure.getMessage()));
        return 1;
    } finally {
        if (context != null) {
            context.close();
        }
    }
}
```

`printResult` 输出状态、Excel/Word 绝对路径、数据集行数、总耗时和逐条警告；失败时输出 `failedStage`、`errorCode`、结构化消息及原始异常类型。不得输出 `DataSource` 或配置对象，避免泄露密码。

- [ ] **Step 4: 运行入口测试确认通过**

Run: `mvn -Dtest=GenerateReportTest test`

Expected: 3 tests, 0 failures, 0 errors。

- [ ] **Step 5: 提交入口类**

```powershell
git add src/main/java/com/xn/report/GenerateReport.java src/test/java/com/xn/report/GenerateReportTest.java
git commit -m "feat: add executable report entry"
```

### Task 3: 配置默认运行环境并指定 JAR 主类

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `pom.xml`

- [ ] **Step 1: 写入 application.yml 默认配置**

保留 `spring.application.name`，增加设计规格中的 `spring.datasource` 和完整 `report-runner`。中心列表写入 10 个中心；本期为 2026-01-01 至 2026-07-01，基准期为 2025 全年。数据库 URL、用户名、密码和根目录分别支持 `XNREPORT_JDBC_URL`、`XNREPORT_DB_USERNAME`、`XNREPORT_DB_PASSWORD`、`XNREPORT_ROOT` 覆盖。

- [ ] **Step 2: 指定 Spring Boot 主类**

将插件改为：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.xn.report.GenerateReport</mainClass>
    </configuration>
</plugin>
```

- [ ] **Step 3: 验证配置绑定和打包**

Run:

```powershell
mvn clean package -DskipTests
jar xf target/xnreport-1.0.0-SNAPSHOT.jar META-INF/MANIFEST.MF
Get-Content META-INF/MANIFEST.MF
```

Expected: 构建成功，清单包含 `Start-Class: com.xn.report.GenerateReport`。检查后删除仅由验证产生的 `META-INF` 目录，不修改源码。

- [ ] **Step 4: 提交运行配置**

```powershell
git add src/main/resources/application.yml pom.xml
git commit -m "build: package executable report generator"
```

### Task 4: 更新使用手册并执行真实入口

**Files:**
- Modify: `docs/使用手册.md`

- [ ] **Step 1: 更新入口说明**

将“Java 入口调用”调整为：

- 源码入口链接 `src/main/java/com/xn/report/GenerateReport.java`；
- `application.yml` 的 `spring.datasource` 和 `report-runner` 参数表；
- 环境变量覆盖示例；
- `mvn clean package` 和 `java -jar target/xnreport-1.0.0-SNAPSHOT.jar`；
- 成功/警告退出码 0，失败退出码 1；
- Java API 嵌入式调用保留为补充内容。

- [ ] **Step 2: 执行完整回归**

Run: `mvn clean verify`

Expected: 单元测试和 MySQL 5.7 集成测试全部通过；Windows 符号链接权限测试允许按现有假设跳过。

- [ ] **Step 3: 在持久 MySQL 5.7 上执行 JAR**

Run:

```powershell
java -jar target/xnreport-1.0.0-SNAPSHOT.jar
```

Expected:

- 退出码 0；
- 状态为 `SUCCESS` 或 `SUCCESS_WITH_WARNINGS`；
- 输出数据集行数：`departmentMonthly=6`、`centerAnnual=10`、`centerMonthly=30`、`personAnnual=10`、`personMonthly=30`、`durationDistribution=3`；
- `output` 中生成非空 `.xlsx` 和 `.docx`。

- [ ] **Step 4: 检查文件和工作区**

Run:

```powershell
Get-ChildItem output/*.xlsx,output/*.docx | Select-Object Name,Length,LastWriteTime
git diff --check
git status --short
```

Expected: 两种文件均存在且大于 0 字节；只有手册为待提交修改，生成的输出文件不进入 Git。

- [ ] **Step 5: 提交手册**

```powershell
git add docs/使用手册.md
git commit -m "docs: explain executable report entry"
```

- [ ] **Step 6: 最终验证**

Run:

```powershell
git status --short
git log -5 --oneline
```

Expected: 工作区干净，最近提交包含配置绑定、入口类、打包配置和使用手册。
