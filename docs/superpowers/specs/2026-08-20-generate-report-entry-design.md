# GenerateReport 可执行入口设计

## 1. 目标

新增可直接运行的 `com.xn.report.GenerateReport`，从 Spring Boot `application.yml` 读取数据库、目录和报表运行参数，调用现有 `DefaultReportEntry` 同步生成 Excel 和 Word。项目打包后执行以下命令即可生成报表：

```powershell
java -jar target/xnreport-1.0.0-SNAPSHOT.jar
```

本功能不增加 REST API、定时任务或交互式输入。

## 2. 入口结构

- `GenerateReport` 是打包清单中的主类，负责启动、执行、输出结果、关闭容器和设置进程退出码。
- `XnReportApplication` 保留 `@SpringBootApplication`，仅作为 Spring 配置源，不自动执行报表。
- `GenerateReport` 通过 `SpringApplicationBuilder(XnReportApplication.class)` 以 `WebApplicationType.NONE` 启动，避免创建 Web 服务。
- 从 Spring 容器获取自动配置的 `DataSource`，通过 `DefaultReportEntry.create(dataSource).generate(request)` 调用现有流水线。

## 3. 配置模型

### 3.1 数据源

`application.yml` 新增 `spring.datasource`。默认值连接当前测试环境，同时允许环境变量覆盖：

```yaml
spring:
  datasource:
    url: ${XNREPORT_JDBC_URL:jdbc:mysql://127.0.0.1:3307/xnreport?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${XNREPORT_DB_USERNAME:xnreport}
    password: ${XNREPORT_DB_PASSWORD:xnreport}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

生产环境通过环境变量提供连接信息，避免修改源码。

### 3.2 报表执行配置

新增 `report-runner`：

```yaml
report-runner:
  root: ${XNREPORT_ROOT:.}
  report-config: config/api-design-efficiency.yml
  config-root: config
  sql-root: config/sql
  template-root: templates
  output-root: output
  temp-root: temp
  runtime:
    start-time: 2026-01-01T00:00:00
    end-time-exclusive: 2026-07-01T00:00:00
    baseline-start-time: 2025-01-01T00:00:00
    baseline-end-time-exclusive: 2026-01-01T00:00:00
    center-names:
      - 开发一中心
      - 开发二中心
      - 开发三中心
      - 开发四中心
      - 开发五中心
      - 开发六中心
      - 开发七中心
      - 开发八中心
      - 开发九中心
      - 研发中心
    report-period: 2026年6月
    prepared-date: 2026年7月23日
```

路径相对于 `root` 解析并规范化，再构造 `ReportExecutionRequest`。日期时间使用 ISO-8601 格式并绑定为 `LocalDateTime`。中心列表必须非空。

## 4. Java 类型设计

### 4.1 `GenerateReport`

职责：

- `main(String[] args)`：调用可测试的运行方法，并仅在非零结果时调用 `System.exit`；
- 启动非 Web Spring 容器；
- 绑定并校验 `report-runner`；
- 创建 `ReportExecutionRequest`；
- 调用 `DefaultReportEntry`；
- 输出执行摘要；
- 在 `finally` 中关闭 Spring 容器。

### 4.2 `ReportRunnerProperties`

新增独立配置类，使用 `@ConfigurationProperties(prefix = "report-runner")`，包含目录配置和嵌套的 `RuntimeProperties`。独立类型比入口类中的大量字符串读取更易测试，也能利用 Spring Boot 的类型转换和宽松命名规则。

配置类不承担报表执行，只负责表达配置。入口类显式检查必填路径、时间、中心列表、报告月份和编制日期；错误信息包含缺失字段名。

## 5. 执行与退出码

执行结果处理：

- `SUCCESS`：打印状态、Excel 路径、Word 路径、数据集行数和总耗时，退出码 `0`；
- `SUCCESS_WITH_WARNINGS`：额外逐条打印警告，退出码 `0`；
- `FAILED`：打印失败阶段、错误码、错误信息和根因，退出码 `1`；
- Spring 启动、配置绑定或入口级异常：打印异常类型与信息，退出码 `1`。

日志不得打印数据库密码。输出目录仍由现有流水线负责原子发布。

## 6. 打包

在 `spring-boot-maven-plugin` 中明确配置：

```xml
<mainClass>com.xn.report.GenerateReport</mainClass>
```

这样 `java -jar` 进入 `GenerateReport`，而不会只启动一个空闲的 Spring 容器。

## 7. 测试

- 配置绑定测试：验证 YAML 中的路径、ISO 时间、中心列表和中文值正确绑定。
- 请求构造测试：验证相对路径基于 `root` 解析，运行参数名称与报表配置一致。
- 结果处理测试：验证成功、带警告和失败结果对应的退出码及摘要输出。
- 打包测试：读取可执行 JAR 清单，确认启动类最终指向 `GenerateReport`。
- 端到端验证：在当前 MySQL 5.7 测试数据上执行 `java -jar`，确认生成 Excel 和 Word，六个数据集行数符合测试数据。
- 完整回归：执行 `mvn clean verify`。

## 8. 文档

更新使用手册，增加：

- `application.yml` 配置说明；
- 环境变量覆盖方式；
- Maven 打包和 `java -jar` 命令；
- 成功、警告和失败退出码；
- 输出文件位置。

## 9. 不在本次范围

- 不删除 `XnReportApplication`；
- 不增加 REST API、调度器或多报表批量入口；
- 不改变现有报表配置模型、SQL、Excel/Word 生成流程；
- 不在源码中硬编码数据库密码和绝对项目路径。

