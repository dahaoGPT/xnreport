# xnreport 效能报表生成器

基于 Java 8、Spring Boot 2.7.18、Spring JDBC、Apache POI 和 JFreeChart 的配置驱动报表组件。它从 MySQL 5.7 执行多条只读 SQL，先生成 Excel，再基于同一份不可变数据快照生成 Word；不提供 REST API，由 Java 入口类直接调用。

## 快速开始

目录约定：

```text
config/
  api-design-efficiency.yml
  sql/*.sql
templates/
  api-design-efficiency.xlsx
  api-design-efficiency.docx
output/
temp/
```

宿主程序负责创建 `DatasetQueryService`（通常使用项目中的 `TransactionalDatasetQueryService` 并注入 DataSource），然后调用唯一入口：

```java
Path root = Paths.get("D:/reports").toAbsolutePath();
ReportEntry entry = DefaultReportEntry.create(datasetQueryService);

Map<String, Object> runtime = new LinkedHashMap<String, Object>();
runtime.put("startTime", LocalDateTime.of(2026, 1, 1, 0, 0));
runtime.put("endTimeExclusive", LocalDateTime.of(2026, 7, 1, 0, 0));
runtime.put("baselineStartTime", LocalDateTime.of(2025, 1, 1, 0, 0));
runtime.put("baselineEndTimeExclusive", LocalDateTime.of(2026, 1, 1, 0, 0));
runtime.put("centerNames", Arrays.asList("开发一中心", "开发二中心"));
runtime.put("reportPeriod", "2026年6月");
runtime.put("preparedDate", "2026年7月23日");

ReportExecutionRequest request = new ReportExecutionRequest(
        root.resolve("config/api-design-efficiency.yml"),
        root.resolve("config"),
        root.resolve("config"),
        root.resolve("templates"),
        root.resolve("output"),
        root.resolve("temp"),
        runtime);
ReportExecutionResult result = entry.generate(request);
```

检查 `result.getStatus()`、输出路径、警告和错误明细。`SUCCESS_WITH_WARNINGS` 表示文件已成功发布，但执行过配置允许的跳过、默认值或发布后清理策略。

## 示例能力

`config/api-design-efficiency.yml` 提供 6 条 SQL 与 6 个独立可见 Sheet 的完整示例：部门-每月、中心-全年、中心-每月、个人-全年、个人-每月、时长分布。图表数据公式直接引用这些数据页，Excel 中通过“图表设计 → 选择数据”可以查看和修改关联数据。

示例包含：

- 两个堆积柱形系列加一个次坐标轴折线系列的组合图；
- 审批耗时趋势图和 1 天之内、7 天之内、7 天以上饼图；
- 嵌套 `AND/OR` 异常规则及基于规则命中数、最大值的文字；
- Word 封面运行参数、真实 TOC 域、1–4 级动态章节、图表、表格、单位和附件信息；
- 空数据、缺失字段、类型不匹配、null 和未解析占位符策略；
- 失败时不发布半成品，执行临时目录自动清理。

面向使用者的完整操作步骤见 [使用手册](docs/使用手册.md)，详细字段与模板约定见 [配置与模板使用说明](docs/配置与模板使用说明.md)。

## 构建与测试

```powershell
mvn test
mvn verify
```

MySQL 集成测试使用 MySQL 5.7 容器时需要可用的 Docker 环境。项目生产编译目标为 Java 8。
