# 效能报表自动生成组件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个 Java 1.8、Spring Boot 2.7 的配置驱动报表组件，从 MySQL 5.7 执行数十条 SQL，先生成带可追溯原生图表的 Excel，再生成带表格、文字和图表图片的 Word。

**Architecture:** 使用单 Maven 模块和分阶段流水线。配置加载、数据查询、转换/规则、图表模型、Excel、Word、输出发布相互隔离；每次调用创建独立执行上下文，查询阶段在只读事务中产生不可变数据集，两个文档共享同一数据和分析结果。

**Tech Stack:** Java 1.8、Spring Boot 2.7.18、Spring JDBC、Jackson YAML/JSON、MySQL Connector/J、Apache POI 5.2.5、JFreeChart 1.5.4、JUnit 5、Mockito、Testcontainers 1.17.6、Maven。

---

## 执行前提

- 工作目录：`D:\3-workspace\xnprojects\xnreport`
- 需求规格：`docs/superpowers/specs/2026-07-27-efficiency-report-generator-design.md`
- 详细设计：`docs/效能报表自动生成组件-详细设计说明书.md`
- 所有命令均从仓库根目录执行。
- 每个任务必须先运行指定失败测试，再写最小实现，再运行通过测试。
- 当前目录尚未初始化 Git。Task 1 将初始化仓库并提交现有文档。
- 集成测试需要 Docker 能运行 `mysql:5.7.44`。

## 目标文件结构

```text
pom.xml
src/main/java/com/xn/report/
├─ XnReportApplication.java
├─ entry/
├─ config/
├─ execution/
├─ analysis/
├─ dataset/
├─ sql/
├─ transform/
├─ rule/
├─ text/
├─ chart/
├─ excel/
├─ word/
├─ output/
├─ policy/
└─ error/
src/main/resources/
├─ application.yml
└─ schema/report-definition.schema.json
src/test/java/com/xn/report/
src/test/resources/fixtures/
```

---

### Task 1: 初始化 Git、Maven 和 Spring Boot 工程

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/com/xn/report/XnReportApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/java/com/xn/report/XnReportApplicationTest.java`

- [ ] **Step 1: 初始化 Git 并记录当前设计基线**

Run:

```powershell
git init
git add docs tools
git commit -m "docs: add report requirements and detailed design"
```

Expected: 创建 Git 仓库并产生首个文档提交。

- [ ] **Step 2: 创建 Maven 配置**

`pom.xml` 使用以下依赖和构建约束：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.xn</groupId>
    <artifactId>xnreport</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>xnreport</name>

    <properties>
        <java.version>1.8</java.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <apache-poi.version>5.2.5</apache-poi.version>
        <jfreechart.version>1.5.4</jfreechart.version>
        <testcontainers.version>1.17.6</testcontainers.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>${apache-poi.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jfree</groupId>
            <artifactId>jfreechart</artifactId>
            <version>${jfreechart.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>3.4.1</version>
                <executions>
                    <execution>
                        <id>enforce-java</id>
                        <goals><goal>enforce</goal></goals>
                        <configuration>
                            <rules>
                                <requireJavaVersion><version>[1.8,1.9)</version></requireJavaVersion>
                                <dependencyConvergence/>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 写启动失败测试**

```java
package com.xn.report;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties =
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class XnReportApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

Run:

```powershell
mvn -Dtest=XnReportApplicationTest test
```

Expected: FAIL，提示 `XnReportApplication` 或 Spring Boot 配置类不存在。

- [ ] **Step 4: 创建最小 Spring Boot 应用**

```java
package com.xn.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class XnReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(XnReportApplication.class, args);
    }
}
```

`src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: xnreport

report-engine:
  config-root: ./config
  sql-root: ./config/sql
  template-root: ./templates
  output-root: ./output
  temp-root: ./temp
```

`.gitignore`：

```gitignore
target/
.idea/
*.iml
.vscode/
output/
temp/
*.log
```

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn test
git add pom.xml .gitignore src
git commit -m "build: initialize Java 8 Spring Boot project"
```

Expected: `BUILD SUCCESS`，测试 1 个、失败 0 个。

---

### Task 2: 配置定义和 YAML/JSON 加载

**Files:**
- Create: `src/main/java/com/xn/report/config/ReportDefinition.java`
- Create: `src/main/java/com/xn/report/config/ReportMetadata.java`
- Create: `src/main/java/com/xn/report/config/ParameterDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/DatasetDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/FieldDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/PolicyDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/WordDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/WordCoverDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/WordTocDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/WordSectionDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/WordComponentDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/NarrativeDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/DistributionDefinition.java`
- Create: `src/main/java/com/xn/report/dataset/DatasetType.java`
- Create: `src/main/java/com/xn/report/config/ReportDefinitionLoader.java`
- Test: `src/test/java/com/xn/report/config/ReportDefinitionLoaderTest.java`
- Test fixture: `src/test/resources/fixtures/configs/minimal-report.yml`
- Test fixture: `src/test/resources/fixtures/configs/minimal-report.json`
- Test fixture: `src/test/resources/fixtures/configs/unknown-property.yml`

- [ ] **Step 1: 写 YAML/JSON 等价解析测试**

```java
package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ReportDefinitionLoaderTest {

    private final ReportDefinitionLoader loader = ReportDefinitionLoader.createDefault();

    @Test
    void loadsYamlAndJsonIntoSameDefinition() {
        ReportDefinition yaml = loader.load(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.yml"));
        ReportDefinition json = loader.load(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.json"));

        assertThat(yaml.getSchemaVersion()).isEqualTo("1.0");
        assertThat(json.getReport().getCode()).isEqualTo(yaml.getReport().getCode());
        assertThat(json.getDatasets()).hasSameSizeAs(yaml.getDatasets());
        assertThat(json.getWord().getSections())
                .hasSameSizeAs(yaml.getWord().getSections());
    }

    @Test
    void rejectsUnknownProperty() {
        assertThatThrownBy(() -> loader.load(Paths.get(
                "src/test/resources/fixtures/configs/unknown-property.yml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknownField");
    }
}
```

- [ ] **Step 2: 创建三个配置夹具并验证失败**

`minimal-report.yml`：

```yaml
schemaVersion: "1.0"
report:
  code: sample-report
  name: 示例报表
  excelTemplate: sample.xlsx
  wordTemplate: sample.docx
datasets:
  - id: summary
    sqlFile: sql/summary.sql
    resultType: SINGLE
word:
  cover:
    title: 示例报表
  toc:
    enabled: true
    maxLevel: 3
    updateOnOpen: true
  sections:
    - id: summarySection
      title: 总体情况
      level: 1
      emptyStrategy: KEEP
```

`minimal-report.json`：

```json
{
  "schemaVersion": "1.0",
  "report": {
    "code": "sample-report",
    "name": "示例报表",
    "excelTemplate": "sample.xlsx",
    "wordTemplate": "sample.docx"
  },
  "datasets": [
    {
      "id": "summary",
      "sqlFile": "sql/summary.sql",
      "resultType": "SINGLE"
    }
  ],
  "word": {
    "cover": {"title": "示例报表"},
    "toc": {"enabled": true, "maxLevel": 3, "updateOnOpen": true},
    "sections": [
      {
        "id": "summarySection",
        "title": "总体情况",
        "level": 1,
        "emptyStrategy": "KEEP"
      }
    ]
  }
}
```

`unknown-property.yml`：

```yaml
schemaVersion: "1.0"
unknownField: invalid
report:
  code: sample-report
```

Run:

```powershell
mvn -Dtest=ReportDefinitionLoaderTest test
```

Expected: FAIL，配置类和加载器尚不存在。

- [ ] **Step 3: 实现配置 POJO 和加载器**

`ReportDefinition` 的最小完整结构：

```java
package com.xn.report.config;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.WordDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportDefinition {
    private String schemaVersion;
    private ReportMetadata report;
    private Map<String, ParameterDefinition> parameters =
            new LinkedHashMap<String, ParameterDefinition>();
    private List<DatasetDefinition> datasets = new ArrayList<DatasetDefinition>();
    private WordDefinition word = new WordDefinition();

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public ReportMetadata getReport() { return report; }
    public void setReport(ReportMetadata report) { this.report = report; }
    public Map<String, ParameterDefinition> getParameters() { return parameters; }
    public void setParameters(Map<String, ParameterDefinition> parameters) {
        this.parameters = parameters;
    }
    public List<DatasetDefinition> getDatasets() { return datasets; }
    public void setDatasets(List<DatasetDefinition> datasets) { this.datasets = datasets; }
    public WordDefinition getWord() { return word; }
    public void setWord(WordDefinition word) { this.word = word; }
}
```

`DatasetDefinition` 至少包含 `id`、`sqlFile`、`sql`、`resultType`、`dependsOn`、`parameters`、`expectedFields`、`timeoutSeconds`、`maxRows`。所有配置类使用标准 JavaBean getter/setter，不使用 Lombok。

`ReportDefinitionLoader`：

```java
package com.xn.report.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class ReportDefinitionLoader {
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    private ReportDefinitionLoader(ObjectMapper yamlMapper, ObjectMapper jsonMapper) {
        this.yamlMapper = yamlMapper;
        this.jsonMapper = jsonMapper;
    }

    public static ReportDefinitionLoader createDefault() {
        ObjectMapper yaml = configured(new ObjectMapper(new YAMLFactory()));
        ObjectMapper json = configured(new ObjectMapper());
        return new ReportDefinitionLoader(yaml, json);
    }

    private static ObjectMapper configured(ObjectMapper mapper) {
        return mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ReportDefinition load(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        ObjectMapper mapper = name.endsWith(".yml") || name.endsWith(".yaml")
                ? yamlMapper : jsonMapper;
        try {
            return mapper.readValue(path.toFile(), ReportDefinition.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "Cannot load report definition " + path + ": " + ex.getMessage(), ex);
        }
    }
}
```

- [ ] **Step 4: 验证并提交**

Run:

```powershell
mvn -Dtest=ReportDefinitionLoaderTest test
git add src/main/java/com/xn/report/config src/main/java/com/xn/report/dataset/DatasetType.java src/test/java/com/xn/report/config src/test/resources/fixtures/configs
git commit -m "feat: load report definitions from YAML and JSON"
```

Expected: 测试 2 个、失败 0 个。

---

### Task 3: 配置校验、JSON Schema 和路径约束

**Files:**
- Create: `src/main/java/com/xn/report/config/ReportDefinitionValidator.java`
- Create: `src/main/java/com/xn/report/config/ValidationIssue.java`
- Create: `src/main/java/com/xn/report/config/ValidationResult.java`
- Create: `src/main/java/com/xn/report/config/RootPathPolicy.java`
- Create: `src/main/resources/schema/report-definition.schema.json`
- Create: `src/test/java/com/xn/report/support/TestFixtures.java`
- Test: `src/test/java/com/xn/report/config/ReportDefinitionValidatorTest.java`
- Test: `src/test/java/com/xn/report/config/RootPathPolicyTest.java`

- [ ] **Step 1: 写配置交叉校验测试**

```java
@Test
void reportsDuplicateIdsMissingSqlAndDependencyCycleTogether() {
    ReportDefinition definition = TestFixtures.report(
            TestFixtures.dataset("a", null, null, "b"),
            TestFixtures.dataset("a", "a.sql", null, "a"));

    ValidationResult result = new ReportDefinitionValidator().validate(definition);

    assertThat(result.codes()).contains(
            "CFG-DUPLICATE-DATASET",
            "CFG-SQL-SOURCE",
            "CFG-DEPENDENCY-CYCLE");
}
```

路径测试：

```java
@Test
void rejectsPathOutsideConfiguredRoot() {
    RootPathPolicy policy = new RootPathPolicy(Paths.get("config").toAbsolutePath());

    assertThatThrownBy(() -> policy.resolve("../outside.sql"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outside configured root");
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -Dtest=ReportDefinitionValidatorTest,RootPathPolicyTest test
```

Expected: FAIL，校验器和路径策略不存在。

- [ ] **Step 3: 实现完整校验规则**

`ReportDefinitionValidator.validate()` 必须一次收集：

```java
public ValidationResult validate(ReportDefinition definition) {
    ValidationResult result = new ValidationResult();
    require(result, definition.getSchemaVersion(), "CFG-SCHEMA-VERSION");
    require(result, definition.getReport(), "CFG-REPORT");
    validateDatasetIds(definition.getDatasets(), result);
    validateSqlSources(definition.getDatasets(), result);
    validateDependencyReferences(definition.getDatasets(), result);
    validateDependencyCycles(definition.getDatasets(), result);
    validateWordSectionTree(definition.getWord(), result);
    validateWordComponentReferences(definition, result);
    return result;
}
```

`RootPathPolicy.resolve()` 必须先 `normalize()`，再验证 `candidate.startsWith(root)`。

Word 配置校验必须覆盖章节 ID 唯一、1—4 级标题、父子层级递增、组件类型、图表/表格/文字引用、三种空章节策略、目录最大级别以及区间边界不重叠。

`TestFixtures` 在本任务先提供：

```java
package com.xn.report.support;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportMetadata;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetType;
import java.util.Arrays;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static ReportDefinition report(DatasetDefinition... datasets) {
        ReportMetadata metadata = new ReportMetadata();
        metadata.setCode("test-report");
        metadata.setName("Test Report");
        ReportDefinition definition = new ReportDefinition();
        definition.setSchemaVersion("1.0");
        definition.setReport(metadata);
        definition.setDatasets(Arrays.asList(datasets));
        return definition;
    }

    public static DatasetDefinition dataset(String id, String... dependsOn) {
        return dataset(id, id + ".sql", null, dependsOn);
    }

    public static DatasetDefinition dataset(
            String id, String sqlFile, String sql, String... dependsOn) {
        DatasetDefinition dataset = new DatasetDefinition();
        dataset.setId(id);
        dataset.setSqlFile(sqlFile);
        dataset.setSql(sql);
        dataset.setResultType(DatasetType.LIST);
        dataset.setDependsOn(Arrays.asList(dependsOn));
        return dataset;
    }
}
```

后续任务只向该类追加对应领域的测试数据构造方法。

JSON Schema 必须声明：

- `schemaVersion`、`report`、`datasets` 为必填。
- `additionalProperties: false`。
- `sqlFile` 与 `sql` 使用 `oneOf`。
- 数据集 `id` 使用 `^[A-Za-z][A-Za-z0-9_-]*$`。
- `word.sections` 使用递归 Schema，`level` 限定为 1—4，组件类型使用枚举。
- `narrative.sourceType` 仅允许 `FIXED_TEMPLATE`、`RULE_GENERATED`。

- [ ] **Step 4: 增加 Schema 解析测试**

```java
@Test
void schemaFileIsValidJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode schema = mapper.readTree(Paths.get(
            "src/main/resources/schema/report-definition.schema.json").toFile());
    assertThat(schema.path("$schema").asText()).contains("json-schema");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
}
```

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=ReportDefinitionValidatorTest,RootPathPolicyTest test
git add src/main/java/com/xn/report/config src/main/resources/schema src/test/java/com/xn/report/config src/test/java/com/xn/report/support/TestFixtures.java
git commit -m "feat: validate report definitions and root paths"
```

Expected: 所有配置校验测试通过。

---

### Task 4: 通用数据集模型和依赖规划

**Files:**
- Create: `src/main/java/com/xn/report/dataset/DatasetRow.java`
- Create: `src/main/java/com/xn/report/dataset/DatasetSchema.java`
- Create: `src/main/java/com/xn/report/dataset/DatasetResult.java`
- Create: `src/main/java/com/xn/report/dataset/DatasetContext.java`
- Create: `src/main/java/com/xn/report/dataset/DatasetPlanner.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Test: `src/test/java/com/xn/report/dataset/DatasetResultTest.java`
- Test: `src/test/java/com/xn/report/dataset/DatasetPlannerTest.java`

- [ ] **Step 1: 写结果形态和大小写字段测试**

```java
@Test
void resolvesFieldsCaseInsensitivelyButKeepsOriginalOrder() {
    DatasetRow row = DatasetRow.of(
            "nodeName", "API设计",
            "avgHours", new BigDecimal("25.27"));

    assertThat(row.get("NODENAME")).isEqualTo("API设计");
    assertThat(row.fieldNames()).containsExactly("nodeName", "avgHours");
}

@Test
void singleRejectsMoreThanOneRow() {
    assertThatThrownBy(() -> DatasetResult.single(
            "summary", Arrays.asList(DatasetRow.empty(), DatasetRow.empty())))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: 写稳定拓扑排序测试**

```java
@Test
void keepsConfigurationOrderInsideSameDependencyLevel() {
    List<DatasetDefinition> input = Arrays.asList(
            TestFixtures.dataset("summary"),
            TestFixtures.dataset("detail"),
            TestFixtures.dataset("analysis", "summary", "detail"));

    assertThat(new DatasetPlanner().plan(input))
            .extracting(DatasetDefinition::getId)
            .containsExactly("summary", "detail", "analysis");
}
```

Run:

```powershell
mvn -Dtest=DatasetResultTest,DatasetPlannerTest test
```

Expected: FAIL，数据集类型尚不存在。

- [ ] **Step 3: 实现不可变数据集**

`DatasetRow` 内部使用两个 `LinkedHashMap`：

```java
public Object get(String field) {
    String original = lowerCaseToOriginal.get(field.toLowerCase(Locale.ROOT));
    if (original == null) {
        throw new IllegalArgumentException("Missing field: " + field);
    }
    return values.get(original);
}
```

`DatasetResult` 提供静态工厂 `scalar`、`single`、`list`，构造时复制并包装不可变集合。`DatasetContext` 按数据集 ID 保存结果，重复 ID 立即失败。

`DatasetContext.Builder` 必须提供：

```java
public static Builder builder();
public Builder put(DatasetResult result);
public DatasetContext buildView();
public DatasetContext build();
```

`buildView()` 返回当前已执行数据集的不可变快照，用于解析后续数据集参数。

- [ ] **Step 4: 实现 Kahn 拓扑排序**

排序器必须：

- 保持配置顺序。
- 拒绝未知依赖。
- 输出数量不足时报告环中的数据集 ID。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=DatasetResultTest,DatasetPlannerTest test
git add src/main/java/com/xn/report/dataset src/test/java/com/xn/report/dataset src/test/java/com/xn/report/support/TestFixtures.java
git commit -m "feat: add immutable datasets and dependency planner"
```

Expected: 数据集和规划测试全部通过。

---

### Task 5: SQL 读取、只读防护和命名参数

**Files:**
- Create: `src/main/java/com/xn/report/sql/SqlFileRepository.java`
- Create: `src/main/java/com/xn/report/sql/ReadOnlySqlGuard.java`
- Create: `src/main/java/com/xn/report/sql/SqlParameterResolver.java`
- Create: `src/main/java/com/xn/report/sql/ResolvedSqlParameters.java`
- Test: `src/test/java/com/xn/report/sql/SqlFileRepositoryTest.java`
- Test: `src/test/java/com/xn/report/sql/ReadOnlySqlGuardTest.java`
- Test: `src/test/java/com/xn/report/sql/SqlParameterResolverTest.java`

- [ ] **Step 1: 写 SQL 防护参数化测试**

```java
@ParameterizedTest
@ValueSource(strings = {
        "select * from t",
        " -- report\n SELECT ';' AS semicolon",
        "/* report */ SELECT col FROM t WHERE name = :name"
})
void acceptsSingleSelect(String sql) {
    new ReadOnlySqlGuard().validate(sql);
}

@ParameterizedTest
@ValueSource(strings = {
        "update t set a = 1",
        "select 1; delete from t",
        "call rebuild_report()",
        "with x as (select 1) select * from x"
})
void rejectsNonSupportedSql(String sql) {
    assertThatThrownBy(() -> new ReadOnlySqlGuard().validate(sql))
            .isInstanceOf(IllegalArgumentException.class);
}
```

参数测试必须覆盖 `LocalDateTime -> Timestamp`、`BigDecimal`、非空集合和空集合失败。

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -Dtest=SqlFileRepositoryTest,ReadOnlySqlGuardTest,SqlParameterResolverTest test
```

Expected: FAIL，SQL 组件不存在。

- [ ] **Step 3: 实现 SQL 字符扫描器**

`ReadOnlySqlGuard` 逐字符跟踪：

```java
enum State { NORMAL, SINGLE_QUOTE, DOUBLE_QUOTE, LINE_COMMENT, BLOCK_COMMENT }
```

只在 `NORMAL` 状态识别分号和危险关键字。清除注释后的首个关键字必须为 `SELECT`。MySQL 5.7 首版拒绝 `WITH`。

`SqlFileRepository`：

```java
public String read(Path path) {
    try {
        byte[] bytes = Files.readAllBytes(path);
        String sql = new String(bytes, StandardCharsets.UTF_8);
        return sql.startsWith("\uFEFF") ? sql.substring(1) : sql;
    } catch (IOException ex) {
        throw new IllegalArgumentException("Cannot read SQL file " + path, ex);
    }
}
```

- [ ] **Step 4: 实现参数解析**

`SqlParameterResolver.resolve()` 只接受配置声明的参数来源：

```java
switch (binding.getFrom()) {
    case RUNTIME:
        value = runtimeParameters.get(binding.getKey());
        break;
    case CONSTANT:
        value = binding.getValue();
        break;
    case DATASET:
        value = datasetContext.get(binding.getDataset()).single()
                .get(binding.getField());
        break;
    default:
        throw new IllegalArgumentException("Unsupported parameter source");
}
```

空集合在进入 `MapSqlParameterSource` 前失败。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=SqlFileRepositoryTest,ReadOnlySqlGuardTest,SqlParameterResolverTest test
git add src/main/java/com/xn/report/sql src/test/java/com/xn/report/sql
git commit -m "feat: secure SQL loading and parameter resolution"
```

Expected: SQL 防护、文件和参数测试全部通过。

---

### Task 6: JDBC 行映射、查询限制和 MySQL 5.7 查询阶段

**Files:**
- Create: `src/main/java/com/xn/report/sql/ResultSetRowMapper.java`
- Create: `src/main/java/com/xn/report/sql/NamedSqlExecutor.java`
- Create: `src/main/java/com/xn/report/dataset/DatasetQueryService.java`
- Create: `src/main/java/com/xn/report/dataset/TransactionalDatasetQueryService.java`
- Create: `src/main/java/com/xn/report/dataset/DatasetResultValidator.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Create: `src/test/java/com/xn/report/sql/ResultSetRowMapperTest.java`
- Create: `src/test/java/com/xn/report/dataset/DatasetResultValidatorTest.java`
- Create: `src/test/java/com/xn/report/dataset/DatasetQueryServiceIT.java`
- Create: `src/test/resources/fixtures/sql/center-monthly.sql`

- [ ] **Step 1: 写 JDBC 类型标准化测试**

```java
@Test
void mapsLabelsAndNormalizesJdbcTypes() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData meta = mock(ResultSetMetaData.class);
    when(rs.getMetaData()).thenReturn(meta);
    when(meta.getColumnCount()).thenReturn(2);
    when(meta.getColumnLabel(1)).thenReturn("avgHours");
    when(meta.getColumnLabel(2)).thenReturn("statMonth");
    when(rs.getObject(1)).thenReturn(25.27d);
    when(rs.getObject(2)).thenReturn("2026-01");

    DatasetRow row = new ResultSetRowMapper().map(rs);

    assertThat(row.get("avgHours")).isEqualTo(new BigDecimal("25.27"));
    assertThat(row.get("statMonth")).isEqualTo("2026-01");
}
```

结果校验测试：

```java
@Test
void failsWhenRequiredAliasIsMissing() {
    DatasetResult result = DatasetResult.list(
            "centerMonthly",
            Collections.singletonList(DatasetRow.of("centerName", "开发一中心")));
    assertThatThrownBy(() -> validator.validate(result, expectedSchema()))
            .isInstanceOf(ReportException.class)
            .hasMessageContaining("avgHours");
}
```

- [ ] **Step 2: 写 MySQL 5.7 集成测试**

使用 `MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")`，启动后建立审批表和测试记录。测试入口：

```java
@Test
void executesNamedListParametersAndReturnsAliasedRows() {
    DatasetContext context = service.executeAll(
            definition,
            TestFixtures.parameters(
                    "startTime", LocalDateTime.of(2026, 1, 1, 0, 0),
                    "endTimeExclusive", LocalDateTime.of(2026, 7, 1, 0, 0),
                    "centerNames", Arrays.asList("开发一中心", "开发二中心")));

    assertThat(context.get("centerMonthly").list())
            .extracting(row -> row.get("centerName"))
            .contains("开发一中心");
}
```

本任务向 `TestFixtures` 增加参数构造器：

```java
public static Map<String, Object> parameters(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
        throw new IllegalArgumentException("keyValues must be pairs");
    }
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    for (int index = 0; index < keyValues.length; index += 2) {
        values.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
    }
    return values;
}
```

Run:

```powershell
mvn -Dtest=ResultSetRowMapperTest,DatasetResultValidatorTest,DatasetQueryServiceIT test
```

Expected: FAIL，查询服务尚不存在。

- [ ] **Step 3: 实现行映射和查询器**

`NamedSqlExecutor.query()` 必须：

- 以 Spring JDBC `NamedParameterJdbcTemplate` 为命名参数入口。
- 使用 `NamedParameterUtils` 展开集合参数。
- 对 `PreparedStatement` 设置 `queryTimeout`。
- 设置 `maxRows + 1`。
- 读取超过 `maxRows` 时抛出 `DATA-004`。
- 使用 `ResultSetRowMapper` 构建 `DatasetRow`。

`ResultSetRowMapper` 按详细设计第 10.6 节转换 JDBC 类型，浮点数通过 `String.valueOf(value)` 构造 `BigDecimal`。

`DatasetResultValidator` 在结果进入 `DatasetContext` 前验证结果形态、必填字段、字段类型和 null 策略；`SCALAR`/`SINGLE` 多行以及超过最大行数均失败。

- [ ] **Step 4: 实现只读事务查询阶段**

```java
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ,
        rollbackFor = Exception.class)
public DatasetContext executeAll(
        ReportDefinition definition,
        Map<String, Object> runtimeParameters) {
    DatasetContext.Builder context = DatasetContext.builder();
    for (DatasetDefinition dataset : planner.plan(definition.getDatasets())) {
        DatasetResult result = executeOne(dataset, runtimeParameters, context.buildView());
        context.put(result);
    }
    return context.build();
}
```

Excel 和 Word 生成不得放进该事务方法。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=ResultSetRowMapperTest,DatasetResultValidatorTest test
mvn -Dtest=DatasetQueryServiceIT test
git add src/main/java/com/xn/report/sql src/main/java/com/xn/report/dataset src/test
git commit -m "feat: execute MySQL datasets in a read-only snapshot"
```

Expected: 单元测试和 MySQL 5.7 集成测试均通过。

---

### Task 7: 数据转换引擎

**Files:**
- Create: `src/main/java/com/xn/report/transform/Transform.java`
- Create: `src/main/java/com/xn/report/transform/TransformEngine.java`
- Create: `src/main/java/com/xn/report/transform/FilterTransform.java`
- Create: `src/main/java/com/xn/report/transform/SortTransform.java`
- Create: `src/main/java/com/xn/report/transform/DistinctTransform.java`
- Create: `src/main/java/com/xn/report/transform/LimitTransform.java`
- Create: `src/main/java/com/xn/report/transform/DerivedFieldTransform.java`
- Create: `src/main/java/com/xn/report/config/definition/TransformDefinition.java`
- Modify: `src/main/java/com/xn/report/config/definition/DatasetDefinition.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Test: `src/test/java/com/xn/report/transform/TransformEngineTest.java`

- [ ] **Step 1: 写顺序、去重和派生字段测试**

```java
@Test
void appliesTransformsInDeclaredOrderWithoutMutatingSource() {
    DatasetResult source = TestFixtures.people(
            TestFixtures.person("A", "8.00"),
            TestFixtures.person("A", "8.00"),
            TestFixtures.person("B", "12.00"));

    DatasetResult result = engine.apply(source, Arrays.asList(
            new DistinctTransform(Arrays.asList("personName")),
            new DerivedFieldTransform("overHours", "avgHours",
                    ArithmeticOperator.SUBTRACT, new BigDecimal("5.00"), 2),
            new SortTransform("avgHours", Direction.DESC),
            new LimitTransform(1)));

    assertThat(result.list()).hasSize(1);
    assertThat(result.list().get(0).get("personName")).isEqualTo("B");
    assertThat(result.list().get(0).get("overHours"))
            .isEqualTo(new BigDecimal("7.00"));
    assertThat(source.list()).hasSize(3);
}
```

本任务向 `TestFixtures` 增加：

```java
public static DatasetRow person(String name, String avgHours) {
    return DatasetRow.of(
            "personName", name,
            "avgHours", new BigDecimal(avgHours));
}

public static DatasetResult people(DatasetRow... rows) {
    return DatasetResult.list("people", Arrays.asList(rows));
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -Dtest=TransformEngineTest test
```

Expected: FAIL，转换类不存在。

- [ ] **Step 3: 实现白名单转换**

```java
public interface Transform {
    DatasetResult apply(DatasetResult input);
}

public final class TransformEngine {
    public DatasetResult apply(DatasetResult source, List<Transform> transforms) {
        DatasetResult current = source;
        for (Transform transform : transforms) {
            current = transform.apply(current);
        }
        return current;
    }
}
```

`DerivedFieldTransform` 仅实现加、减、乘、除；除数为 0 时按显式策略处理，默认失败。所有数值使用 `BigDecimal` 和 `RoundingMode.HALF_UP`。

`DatasetDefinition` 增加有序 `List<TransformDefinition> transforms`，配置解析测试必须证明转换顺序与 YAML 声明顺序一致。

- [ ] **Step 4: 验证并提交**

Run:

```powershell
mvn -Dtest=TransformEngineTest test
git add src/main/java/com/xn/report/transform src/main/java/com/xn/report/config/definition/TransformDefinition.java src/main/java/com/xn/report/config/definition/DatasetDefinition.java src/test/java/com/xn/report/transform src/test/java/com/xn/report/support/TestFixtures.java
git commit -m "feat: add deterministic dataset transforms"
```

Expected: 转换测试通过，原数据集保持不变。

---

### Task 8: 嵌套 AND/OR 规则引擎

**Files:**
- Create: `src/main/java/com/xn/report/rule/ConditionNode.java`
- Create: `src/main/java/com/xn/report/rule/LogicalCondition.java`
- Create: `src/main/java/com/xn/report/rule/ComparisonCondition.java`
- Create: `src/main/java/com/xn/report/rule/ComparisonOperator.java`
- Create: `src/main/java/com/xn/report/rule/ValueReference.java`
- Create: `src/main/java/com/xn/report/rule/RuleEvaluationContext.java`
- Create: `src/main/java/com/xn/report/rule/RuleEngine.java`
- Create: `src/main/java/com/xn/report/rule/RuleResult.java`
- Create: `src/main/java/com/xn/report/rule/RuleGroupResult.java`
- Create: `src/main/java/com/xn/report/config/definition/RuleDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/ConditionDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/ValueReferenceDefinition.java`
- Modify: `src/main/java/com/xn/report/config/ReportDefinition.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Test: `src/test/java/com/xn/report/rule/RuleEngineTest.java`

- [ ] **Step 1: 写嵌套条件和动态标准测试**

```java
@Test
void matchesNestedAndOrAgainstDatasetStandard() {
    ConditionNode condition = TestFixtures.and(
            TestFixtures.compare(
                    TestFixtures.field("avgHours"),
                    ComparisonOperator.GT,
                    TestFixtures.datasetField("baseline", "standardHours")),
            TestFixtures.or(
                    TestFixtures.compare(
                            TestFixtures.field("onJob"),
                            ComparisonOperator.EQ,
                            TestFixtures.literal(true)),
                    TestFixtures.compare(
                            TestFixtures.field("groupCategory"),
                            ComparisonOperator.IN,
                            TestFixtures.literal(Arrays.asList("A", "B")))));

    RuleResult result = engine.evaluate(
            "approvalTimeout",
            TestFixtures.personAnnual(),
            condition,
            TestFixtures.contextWithBaseline("10.00"));

    assertThat(result.getMatchedRows())
            .extracting(row -> row.get("personName"))
            .containsExactly("张三");
}
```

本任务为 `ValueReference` 实现静态工厂
`literal`、`currentField`、`datasetField`、`runtimeParameter`，并向
`TestFixtures` 增加对应转发方法及以下数据夹具：

```java
public static DatasetResult personAnnual() {
    return DatasetResult.list("personAnnual", Arrays.asList(
            DatasetRow.of(
                    "personName", "张三",
                    "avgHours", new BigDecimal("12.50"),
                    "onJob", true,
                    "groupCategory", "C"),
            DatasetRow.of(
                    "personName", "李四",
                    "avgHours", new BigDecimal("8.00"),
                    "onJob", true,
                    "groupCategory", "A")));
}

public static RuleEvaluationContext contextWithBaseline(String standardHours) {
    DatasetContext datasets = DatasetContext.builder()
            .put(DatasetResult.single(
                    "baseline",
                    Collections.singletonList(DatasetRow.of(
                            "standardHours", new BigDecimal(standardHours)))))
            .build();
    return new RuleEvaluationContext(datasets, Collections.<String, Object>emptyMap());
}
```

增加 null 测试：普通 `GT` 遇到 null 返回 false，`IS_NULL` 返回 true。

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -Dtest=RuleEngineTest test
```

Expected: FAIL，规则模型不存在。

- [ ] **Step 3: 实现条件树**

```java
public final class LogicalCondition implements ConditionNode {
    private final LogicalOperator operator;
    private final List<ConditionNode> children;

    @Override
    public boolean evaluate(RuleEvaluationContext context, DatasetRow row) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException("Logical condition requires children");
        }
        if (operator == LogicalOperator.AND) {
            for (ConditionNode child : children) {
                if (!child.evaluate(context, row)) {
                    return false;
                }
            }
            return true;
        }
        for (ConditionNode child : children) {
            if (child.evaluate(context, row)) {
                return true;
            }
        }
        return false;
    }
}
```

`ComparisonCondition` 实现详细设计第 12.3 节全部操作符，并使用类型转换器统一数值、日期和集合比较。

`ReportDefinition` 增加 `List<RuleDefinition> rules`；配置到运行时条件树的转换必须在规则执行前完成，转换失败归类为 `RULE-001`。

- [ ] **Step 4: 实现规则结果管道**

命中后按以下固定顺序执行：

```text
filter -> distinct -> sort -> group -> limit -> summary
```

`RuleResult` 保存不可变命中行、分组和汇总值。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=RuleEngineTest test
git add src/main/java/com/xn/report/rule src/main/java/com/xn/report/config src/test/java/com/xn/report/rule src/test/java/com/xn/report/support/TestFixtures.java
git commit -m "feat: evaluate nested anomaly rules"
```

Expected: 嵌套条件、null、动态标准和结果处理测试通过。

---

### Task 9: 安全文字模板、趋势/分布分析和确定性叙述

**Files:**
- Create: `src/main/java/com/xn/report/text/TextRenderer.java`
- Create: `src/main/java/com/xn/report/text/PlaceholderParser.java`
- Create: `src/main/java/com/xn/report/text/ValueFormatter.java`
- Create: `src/main/java/com/xn/report/text/FormatterRegistry.java`
- Create: `src/main/java/com/xn/report/text/FormulaInjectionGuard.java`
- Create: `src/main/java/com/xn/report/text/NarrativeEngine.java`
- Create: `src/main/java/com/xn/report/text/NarrativeResult.java`
- Create: `src/main/java/com/xn/report/text/TrendAnalyzer.java`
- Create: `src/main/java/com/xn/report/text/TrendResult.java`
- Create: `src/main/java/com/xn/report/text/DistributionAnalyzer.java`
- Create: `src/main/java/com/xn/report/text/DistributionResult.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Test: `src/test/java/com/xn/report/text/TextRendererTest.java`
- Test: `src/test/java/com/xn/report/text/FormulaInjectionGuardTest.java`
- Test: `src/test/java/com/xn/report/text/NarrativeEngineTest.java`
- Test: `src/test/java/com/xn/report/text/TrendAnalyzerTest.java`
- Test: `src/test/java/com/xn/report/text/DistributionAnalyzerTest.java`

- [ ] **Step 1: 写格式化和作用域测试**

```java
@Test
void rendersRowSummaryRuntimeAndDatasetValues() {
    String template =
            "${personName}平均耗时${avgHours|number:0.00}小时，"
            + "标准${dataset.baseline.standardHours|number:0.00}小时，"
            + "周期${runtime.period}";

    String text = renderer.render(template, TestFixtures.textContext());

    assertThat(text).isEqualTo(
            "张三平均耗时12.50小时，标准10.00小时，周期2026H1");
}

@Test
void failsOnUnresolvedPlaceholder() {
    assertThatThrownBy(() -> renderer.render(
            "${missingField}", TestFixtures.textContext()))
            .hasMessageContaining("missingField");
}
```

`TestFixtures.textContext()` 必须提供当前行 `personName=张三`、
`avgHours=12.50`，运行参数 `period=2026H1`，以及数据集
`baseline.standardHours=10.00`。

- [ ] **Step 2: 写公式注入测试并运行失败**

```java
@ParameterizedTest
@ValueSource(strings = {"=SUM(A1:A2)", "+1+1", "-2+3", "@cmd"})
void prefixesDangerousExcelText(String input) {
    assertThat(new FormulaInjectionGuard().asPlainText(input))
            .isEqualTo("'" + input);
}
```

- [ ] **Step 3: 写两类文字、趋势和区间分布测试**

测试必须覆盖：

- `FIXED_TEMPLATE` 使用数据集和运行参数占位符。
- `RULE_GENERATED` 输出来源数据集、分析器 ID 和摘要值。
- 当前值与上年同期/基准值的差值、变化率和 `UP/DOWN/FLAT`。
- 最大、最小及异常月份。
- `1天之内`、`7天之内`、`7天以上` 的互斥边界。
- 区间数量、占比及 `COUNT_AND_PERCENT` 标签。
- 数据不足时执行组件空数据策略。

Run:

```powershell
mvn -Dtest=TextRendererTest,FormulaInjectionGuardTest,NarrativeEngineTest,TrendAnalyzerTest,DistributionAnalyzerTest test
```

Expected: FAIL，文字组件不存在。

- [ ] **Step 4: 实现受限占位符和确定性分析**

`PlaceholderParser` 只接受：

```text
${name}
${name|formatter:argument}
```

不使用 SpEL、脚本或反射。`FormatterRegistry` 注册 `number`、`percent`、`date`、`datetime`、`durationHours`、`default`、`join`。

`NarrativeEngine` 仅支持 `FIXED_TEMPLATE` 和 `RULE_GENERATED`，不调用大模型。`TrendAnalyzer` 全程使用 `BigDecimal`；`DistributionAnalyzer` 按配置的开闭边界生成同一份 `DistributionResult`，供图表和说明文字共享。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=TextRendererTest,FormulaInjectionGuardTest,NarrativeEngineTest,TrendAnalyzerTest,DistributionAnalyzerTest test
git add src/main/java/com/xn/report/text src/test/java/com/xn/report/text
git commit -m "feat: render safe analysis text"
```

Expected: 格式化、两类文字来源、趋势、极值、区间统计、未解析字段和公式注入测试通过。

---

### Task 10: 统一图表模型和 Word 图片渲染

**Files:**
- Create: `src/main/java/com/xn/report/chart/ChartModel.java`
- Create: `src/main/java/com/xn/report/chart/ChartSeriesModel.java`
- Create: `src/main/java/com/xn/report/chart/ChartType.java`
- Create: `src/main/java/com/xn/report/chart/ChartModelBuilder.java`
- Create: `src/main/java/com/xn/report/chart/ChartImageRenderer.java`
- Create: `src/main/java/com/xn/report/chart/JFreeChartImageRenderer.java`
- Create: `src/main/java/com/xn/report/chart/ChartRenderOptions.java`
- Create: `src/main/java/com/xn/report/chart/RenderedChart.java`
- Create: `src/main/java/com/xn/report/config/definition/ChartDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/ChartSeriesDefinition.java`
- Create: `src/main/java/com/xn/report/chart/ChartDataLabelMode.java`
- Modify: `src/main/java/com/xn/report/config/ReportDefinition.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Test: `src/test/java/com/xn/report/chart/ChartModelBuilderTest.java`
- Test: `src/test/java/com/xn/report/chart/JFreeChartImageRendererTest.java`

- [ ] **Step 1: 写堆积柱形加折线模型测试**

```java
@Test
void buildsStackedColumnAndLineCombo() {
    ChartModel model = builder.build(
            TestFixtures.comboChartDefinition(),
            TestFixtures.centerEvents());

    assertThat(model.getCategories())
            .containsExactly("2026年1月", "2026年2月", "2026年3月");
    assertThat(model.getSeries())
            .extracting(ChartSeriesModel::getType)
            .containsExactly(
                    ChartType.STACKED_COLUMN,
                    ChartType.STACKED_COLUMN,
                    ChartType.LINE);
    assertThat(model.getSeries().get(0).getStackGroup()).isEqualTo("event");
}
```

- [ ] **Step 2: 写 PNG 和饼图区间标签测试并运行失败**

```java
@Test
void rendersReadablePng() throws Exception {
    RenderedChart rendered = renderer.render(
            TestFixtures.comboChartModel(),
            new ChartRenderOptions(1600, 850, 180));

    BufferedImage image = ImageIO.read(rendered.getPath().toFile());
    assertThat(image.getWidth()).isEqualTo(1600);
    assertThat(image.getHeight()).isEqualTo(850);
    assertThat(Files.size(rendered.getPath())).isGreaterThan(1000L);
}
```

`TestFixtures.comboChartDefinition()`、`centerEvents()` 和
`comboChartModel()` 必须使用完全相同的三个月、两个堆积柱形系列和一个折线系列，确保模型测试和图片测试共享同一组确定数据。

同一测试阶段使用 Task 9 的 `DistributionResult` 构建饼图，断言三个扇区标签分别包含数量和百分比，且数量合计等于输入总数。

Run:

```powershell
mvn -Dtest=ChartModelBuilderTest,JFreeChartImageRendererTest test
```

Expected: FAIL，图表组件不存在。

- [ ] **Step 3: 实现图表模型构建**

构建器必须：

- 按类别字段稳定排序。
- 按 `groupByField` 拆分模型。
- 校验每个系列点数与类别数相同。
- 支持主次坐标轴。
- 支持空点 `GAP`、`ZERO`、`SKIP_CATEGORY`。
- 支持饼图/圆环图 `COUNT`、`PERCENT`、`COUNT_AND_PERCENT` 数据标签。

`ReportDefinition` 增加 `List<ChartDefinition> charts`；配置校验必须验证分类字段、系列字段、堆积组和主次坐标轴。

- [ ] **Step 4: 实现 JFreeChart 渲染器**

使用 `CategoryPlot`、`XYPlot`、`PiePlot`、`SpiderWebPlot`、`CandlestickRenderer` 和组合 Plot 覆盖设计目录。字体从配置候选中选择，输出 PNG，渲染后 `Graphics2D.dispose()`。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=ChartModelBuilderTest,JFreeChartImageRendererTest test
git add src/main/java/com/xn/report/chart src/main/java/com/xn/report/config src/test/java/com/xn/report/chart src/test/java/com/xn/report/support/TestFixtures.java
git commit -m "feat: build and render reusable chart models"
```

Expected: 组合图模型和 PNG 测试通过。

---

### Task 11: Excel 标量、列表和一条 SQL 一个可见 Sheet

**Files:**
- Create: `src/main/java/com/xn/report/excel/ExcelTemplateLoader.java`
- Create: `src/main/java/com/xn/report/excel/ExcelValueBinder.java`
- Create: `src/main/java/com/xn/report/excel/ExcelTableWriter.java`
- Create: `src/main/java/com/xn/report/excel/ExcelOutputValidator.java`
- Create: `src/main/java/com/xn/report/excel/ExcelDatasetSheetWriter.java`
- Create: `src/main/java/com/xn/report/excel/ExcelSheetNameValidator.java`
- Create: `src/main/java/com/xn/report/config/definition/ExcelDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/ExcelValueBinding.java`
- Create: `src/main/java/com/xn/report/config/definition/ExcelTableBinding.java`
- Modify: `src/main/java/com/xn/report/config/ReportDefinition.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Create: `src/test/java/com/xn/report/excel/ExcelTableWriterTest.java`
- Create: `src/test/resources/fixtures/templates/report-template.xlsx`

- [ ] **Step 1: 创建最小 Excel 模板夹具**

模板必须包含：

- `报表首页`。
- 两个 SQL 数据集对应的可见工作表 `中心-每月`、`个人-全年`。
- Excel 表 `tbl_center_monthly`。
- 表头：月份、不定责事件、定责事件、中心基准值。
- 一行带样式的原型数据。

- [ ] **Step 2: 写表扩展和缩减测试**

```java
@Test
void writesEachDatasetToItsOwnVisibleSheet() throws Exception {
    Path output = tempDir.resolve("report.xlsx");
    try (XSSFWorkbook workbook = new XSSFWorkbook(
            Files.newInputStream(template));
         OutputStream stream = Files.newOutputStream(output)) {
        writer.write(
                workbook,
                "中心-每月",
                "tbl_center_monthly",
                TestFixtures.centerEvents().list(),
                Arrays.asList(
                        "statMonth",
                        "uncertainCount",
                        "certainCount",
                        "baseline"));
        workbook.write(stream);
    }

    try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(output))) {
        XSSFSheet sheet = workbook.getSheet("中心-每月");
        XSSFTable table = sheet.getTables().stream()
                .filter(t -> "tbl_center_monthly".equals(t.getName()))
                .findFirst().orElseThrow(IllegalStateException::new);
        assertThat(sheet.getSheetState()).isEqualTo(SheetVisibility.VISIBLE);
        assertThat(table.getArea().getLastCell().getRow()).isEqualTo(4);
        assertThat(sheet.getRow(2).getCell(0).getStringCellValue())
                .isEqualTo("2026年1月");
    }
}
```

- [ ] **Step 3: 运行失败测试**

Run:

```powershell
mvn -Dtest=ExcelTableWriterTest test
```

Expected: FAIL，Excel 组件不存在。

- [ ] **Step 4: 实现模板写入**

`ExcelTableWriter` 必须：

- 按配置字段顺序写单元格。
- 复制原型行样式。
- 删除模板遗留的多余数据行。
- 更新 `CTTable.ref` 和 AutoFilter。
- 为每个 SQL 数据集创建或定位配置的 `sheetName`，写入完整结果并保持可见。
- 拒绝重复、空白、超过 31 字符或包含 `\ / ? * [ ] :` 的 Sheet 名。
- 不创建统一“图表数据”页；模板中的封面、汇总等非 SQL Sheet 可以保留。
- 普通字符串通过 `FormulaInjectionGuard`。

`DatasetDefinition` 增加 `sheetName`；`ReportDefinition` 增加 `ExcelDefinition excel`。加载与校验测试必须覆盖数据集/Sheet 一一对应、列表字段顺序、Sheet 名唯一性及合法性。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=ExcelTableWriterTest test
git add src/main/java/com/xn/report/excel src/main/java/com/xn/report/config src/test/java/com/xn/report/excel src/test/java/com/xn/report/support/TestFixtures.java src/test/resources/fixtures/templates
git commit -m "feat: populate visible Excel data tables"
```

Expected: 表范围、值和可见性断言通过。

---

### Task 12: Excel 动态图表和模板原生图表绑定

**Files:**
- Create: `src/main/java/com/xn/report/chart/ExcelNativeChartWriter.java`
- Create: `src/main/java/com/xn/report/chart/TemplateChartBinder.java`
- Create: `src/main/java/com/xn/report/chart/ChartFormulaRange.java`
- Create: `src/main/java/com/xn/report/chart/ChartLocator.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Test: `src/test/java/com/xn/report/chart/ExcelNativeChartWriterTest.java`
- Test: `src/test/java/com/xn/report/chart/TemplateChartBinderTest.java`
- Modify: `src/test/resources/fixtures/templates/report-template.xlsx`

- [ ] **Step 1: 在模板加入组合图标记**

在 Excel 模板中创建：

- 两个堆积柱形系列。
- 一个折线系列。
- 图表替代文字 `REPORT_CHART:centerEventChart`。
- 数据源指向 SQL 数据集对应的 `中心-每月` Sheet。

- [ ] **Step 2: 写图表 OOXML 测试**

```java
@Test
void preservesComboTypesAndUpdatesSeriesRanges() throws Exception {
    Path output = binder.bind(template, TestFixtures.comboChartModel());

    try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(output))) {
        XSSFChart chart = ChartLocator.findByMarker(
                workbook, "REPORT_CHART:centerEventChart");
        String xml = chart.getCTChart().xmlText();
        assertThat(xml).contains("barChart");
        assertThat(xml).contains("lineChart");
        assertThat(xml).contains("'中心-每月'!$A$2:$A$4");
        assertThat(xml).contains("'中心-每月'!$B$2:$B$4");
    }
}
```

动态生成测试必须断言 grouping 为 `stacked`、overlap 为 `100`，折线和柱形位于同一 PlotArea。

- [ ] **Step 3: 运行失败测试**

Run:

```powershell
mvn -Dtest=ExcelNativeChartWriterTest,TemplateChartBinderTest test
```

Expected: FAIL，图表绑定器不存在。

- [ ] **Step 4: 实现两个图表路径**

`ExcelNativeChartWriter` 使用 XDDF 创建标准和组合图。

`TemplateChartBinder`：

- 处理 `excelMode: TEMPLATE_NATIVE`。
- 按替代文字匹配图表。
- 0 个或多个匹配均失败。
- 更新类别、系列公式。
- 更新 `strCache`、`numCache`、`ptCount`。
- 不改变模板图表类型和样式。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=ExcelNativeChartWriterTest,TemplateChartBinderTest test
git add src/main/java/com/xn/report/chart src/test/java/com/xn/report/chart src/test/resources/fixtures/templates
git commit -m "feat: create and bind editable Excel charts"
```

Expected: 动态堆积组合图和模板图表范围测试通过。

---

### Task 13: Word 封面、动态章节、自动目录、表格和图表

**Files:**
- Create: `src/main/java/com/xn/report/word/WordTemplateLoader.java`
- Create: `src/main/java/com/xn/report/word/WordRunTextReplacer.java`
- Create: `src/main/java/com/xn/report/word/WordCoverBinder.java`
- Create: `src/main/java/com/xn/report/word/WordSectionRenderer.java`
- Create: `src/main/java/com/xn/report/word/WordComponentRenderer.java`
- Create: `src/main/java/com/xn/report/word/WordTocManager.java`
- Create: `src/main/java/com/xn/report/word/WordNumberingManager.java`
- Create: `src/main/java/com/xn/report/word/WordTableWriter.java`
- Create: `src/main/java/com/xn/report/word/WordImageWriter.java`
- Create: `src/main/java/com/xn/report/word/WordAttachmentWriter.java`
- Create: `src/main/java/com/xn/report/word/WordWatermarkValidator.java`
- Create: `src/main/java/com/xn/report/word/WordOutputValidator.java`
- Modify: `src/main/java/com/xn/report/config/definition/WordDefinition.java`
- Create: `src/main/java/com/xn/report/config/definition/WordTableBinding.java`
- Modify: `src/main/java/com/xn/report/config/ReportDefinition.java`
- Modify: `src/test/java/com/xn/report/support/TestFixtures.java`
- Create: `src/test/java/com/xn/report/word/WordRunTextReplacerTest.java`
- Create: `src/test/java/com/xn/report/word/WordCoverBinderTest.java`
- Create: `src/test/java/com/xn/report/word/WordSectionRendererTest.java`
- Create: `src/test/java/com/xn/report/word/WordTocManagerTest.java`
- Create: `src/test/java/com/xn/report/word/WordTableWriterTest.java`
- Create: `src/test/java/com/xn/report/word/WordImageWriterTest.java`
- Create: `src/test/java/com/xn/report/word/WordWatermarkValidatorTest.java`
- Create: `src/test/resources/fixtures/templates/report-template.docx`

- [ ] **Step 1: 创建符合契约的 Word 模板**

模板包含：

- 封面五类变量：标题、机构/中心、报告周期、编制单位/人员、编制日期。
- `Heading 1` 至 `Heading 4` 样式。
- 真实目录域和动态内容锚点 `{{sections}}`。
- 被拆成多个 Run 的 `{{value:summary.avgHours}}`。
- `{{text:approvalTimeout}}`。
- 一张含 `{{row:personName}}` 和 `{{row:avgHours|number:0.00}}` 的原型表格。
- 独占段落的 `{{chart:centerEventChart}}`。
- 不包含水印。

- [ ] **Step 2: 写封面、跨 Run 和表格测试**

```java
@Test
void replacesPlaceholderSplitAcrossRuns() {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.createRun().setText("{{value:summary.");
    paragraph.createRun().setText("avgHours}}");

    replacer.replace(paragraph, "{{value:summary.avgHours}}", "25.27");

    assertThat(paragraph.getText()).isEqualTo("25.27");
}

@Test
void repeatsPrototypeRowAndRemovesMarker() {
    writer.write(table, TestFixtures.people());
    assertThat(table.getNumberOfRows()).isEqualTo(3);
    assertThat(table.getText()).doesNotContain("{{row:");
}
```

同时断言封面五类变量全部替换；缺少必填封面变量、标题样式或章节锚点时明确失败。

- [ ] **Step 3: 写动态章节、编号和目录测试**

测试建立四级章节树，混合 `SCENARIO`、`KEY_FACTORS`、`FIXED_TEXT`、`RULE_TEXT`、`CHART`、`TABLE`、`UNIT`、`ATTACHMENT`，断言：

- 深度优先顺序与配置一致。
- 标题使用 `Heading 1` 至 `Heading 4`，编号来自多级编号定义。
- `KEEP`、`SHOW_EMPTY`、`SKIP` 行为正确，`SKIP` 章节不进入目录。
- 目录是 `TOC \o "1-3" \h \z \u` 真实域，且 `word/settings.xml` 存在 `<w:updateFields w:val="true"/>`。
- 模板缺少标题样式或目录域时生成失败。

- [ ] **Step 4: 写图片、附件和无水印测试并运行失败**

```java
@Test
void insertsPngAtChartParagraph() throws Exception {
    imageWriter.replaceChart(document, "centerEventChart", chartPng, 16.0);
    assertThat(document.getAllPictures()).hasSize(1);
    assertThat(document.getParagraphs().stream()
            .map(XWPFParagraph::getText))
            .noneMatch(text -> text.contains("{{chart:"));
}
```

附件测试断言附件标题、说明和清单按配置顺序出现。水印测试检查 DOCX 包内 VML/Drawing 水印对象；普通文档通过，含生成器水印对象的夹具失败。

Run:

```powershell
mvn -Dtest=WordCoverBinderTest,WordRunTextReplacerTest,WordSectionRendererTest,WordTocManagerTest,WordTableWriterTest,WordImageWriterTest,WordWatermarkValidatorTest test
```

Expected: FAIL，Word 组件不存在。

- [ ] **Step 5: 实现 Word 写入**

必须遍历正文、表格、页眉和页脚。跨 Run 替换使用拼接文本和偏移映射；原型行复制底层 `CTRow`；图表图片宽度不超过页面可用宽度。

`WordSectionRenderer` 在 `{{sections}}` 位置递归生成章节；`WordNumberingManager` 创建/复用多级编号；`WordComponentRenderer` 严格按配置顺序分派组件。`WordTocManager` 不写静态目录文本，只更新真实目录域的级别并设置打开文档时更新。

`WordOutputValidator` 重新打开 DOCX 并检查：

- 封面字段、标题层级、章节顺序和编号正确。
- 目录域与 `w:updateFields` 更新标记存在。
- 无未解析必填占位符。
- 图片 relation 可读取。
- 动态表格行数符合预期。
- 附件信息存在。
- 不存在程序生成的水印对象。

`ReportDefinition` 增加 `WordDefinition word`；加载测试必须覆盖封面、目录、递归章节、空章节策略和全部组件类型。

- [ ] **Step 6: 验证并提交**

Run:

```powershell
mvn -Dtest=WordCoverBinderTest,WordRunTextReplacerTest,WordSectionRendererTest,WordTocManagerTest,WordTableWriterTest,WordImageWriterTest,WordWatermarkValidatorTest test
git add src/main/java/com/xn/report/word src/main/java/com/xn/report/config src/test/java/com/xn/report/word src/test/java/com/xn/report/support/TestFixtures.java src/test/resources/fixtures/templates
git commit -m "feat: render structured Word reports"
```

Expected: 封面、动态章节、标题编号、目录域、跨 Run、动态表格、附件、图片和无水印测试通过。

---

### Task 14: 错误码、策略、临时工作区和原子发布

**Files:**
- Create: `src/main/java/com/xn/report/error/ReportErrorCode.java`
- Create: `src/main/java/com/xn/report/error/ReportException.java`
- Create: `src/main/java/com/xn/report/error/ReportErrorDetail.java`
- Create: `src/main/java/com/xn/report/policy/EmptyDataPolicy.java`
- Create: `src/main/java/com/xn/report/policy/MissingFieldPolicy.java`
- Create: `src/main/java/com/xn/report/policy/TypeMismatchPolicy.java`
- Create: `src/main/java/com/xn/report/policy/NullValuePolicy.java`
- Create: `src/main/java/com/xn/report/policy/PolicyResolver.java`
- Modify: `src/main/java/com/xn/report/config/definition/PolicyDefinition.java`
- Modify: `src/main/java/com/xn/report/config/ReportDefinition.java`
- Test: `src/test/java/com/xn/report/policy/PolicyResolverTest.java`
- Create: `src/main/java/com/xn/report/output/ExecutionWorkspace.java`
- Create: `src/main/java/com/xn/report/output/OutputNameRenderer.java`
- Create: `src/main/java/com/xn/report/output/OutputPublisher.java`
- Create: `src/main/java/com/xn/report/output/CollisionPolicy.java`
- Test: `src/test/java/com/xn/report/output/OutputPublisherTest.java`

- [ ] **Step 1: 写无半成品和路径安全测试**

```java
@Test
void rollsBackExcelWhenWordPublishFails() throws Exception {
    publisher = publisherWithSecondMoveFailure();

    assertThatThrownBy(() -> publisher.publish(excel, word, targets))
            .isInstanceOf(ReportException.class);
    assertThat(Files.exists(targets.getExcel())).isFalse();
    assertThat(Files.exists(targets.getWord())).isFalse();
}

@Test
void rejectsFileNameTraversal() {
    assertThatThrownBy(() -> renderer.render("../outside.xlsx", values))
            .isInstanceOf(ReportException.class)
            .hasMessageContaining("path");
}
```

策略优先级测试：

```java
@Test
void resolvesComponentBeforeRuleDatasetGlobalAndDefault() {
    PolicyResolver resolver = new PolicyResolver(PolicyDefinition.systemDefaults());
    EmptyDataPolicy result = resolver.resolveEmptyData(
            PolicyDefinition.global(EmptyDataPolicy.OUTPUT_MESSAGE),
            PolicyDefinition.dataset(EmptyDataPolicy.SKIP),
            PolicyDefinition.rule(EmptyDataPolicy.FAIL),
            PolicyDefinition.component(EmptyDataPolicy.USE_DEFAULT));
    assertThat(result).isEqualTo(EmptyDataPolicy.USE_DEFAULT);
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -Dtest=OutputPublisherTest,PolicyResolverTest test
```

Expected: FAIL，输出组件不存在。

- [ ] **Step 3: 实现错误模型和策略枚举**

错误码按详细设计第 20.2 节完整定义。`ReportException` 必须携带错误码、执行编号、阶段、组件 ID 和 cause。

`PolicyResolver` 按“组件 > 规则 > 数据集 > 报表 > 系统默认”返回第一个非空策略值；策略执行必须记录跳过或默认值产生的 `ReportWarning`。

- [ ] **Step 4: 实现工作区和发布回滚**

发布算法：

```java
Path stagedExcel = stage(excel, targets.getExcel());
Path stagedWord = stage(word, targets.getWord());
try {
    move(stagedExcel, targets.getExcel());
    move(stagedWord, targets.getWord());
} catch (IOException ex) {
    Files.deleteIfExists(targets.getExcel());
    Files.deleteIfExists(targets.getWord());
    throw ReportException.outputPublish(ex);
}
```

`VERSIONED` 为默认冲突策略；输出名去除非法字符和目录分隔符。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=OutputPublisherTest,PolicyResolverTest test
git add src/main/java/com/xn/report/error src/main/java/com/xn/report/policy src/main/java/com/xn/report/output src/main/java/com/xn/report/config src/test/java/com/xn/report/output src/test/java/com/xn/report/policy
git commit -m "feat: publish report files atomically"
```

Expected: 冲突策略、回滚和路径测试通过。

---

### Task 15: 统一流水线、入口类、执行日志和结果

**Files:**
- Create: `src/main/java/com/xn/report/entry/ReportEntry.java`
- Create: `src/main/java/com/xn/report/entry/DefaultReportEntry.java`
- Create: `src/main/java/com/xn/report/entry/ReportExecutionRequest.java`
- Create: `src/main/java/com/xn/report/entry/ReportExecutionResult.java`
- Create: `src/main/java/com/xn/report/entry/ExecutionStatus.java`
- Create: `src/main/java/com/xn/report/entry/ReportWarning.java`
- Create: `src/main/java/com/xn/report/execution/ExecutionContext.java`
- Create: `src/main/java/com/xn/report/execution/ExecutionStage.java`
- Create: `src/main/java/com/xn/report/execution/ExecutionMetrics.java`
- Create: `src/main/java/com/xn/report/execution/ReportPipeline.java`
- Create: `src/main/java/com/xn/report/execution/DefaultReportPipeline.java`
- Create: `src/main/java/com/xn/report/analysis/AnalysisContext.java`
- Create: `src/main/java/com/xn/report/analysis/AnalysisService.java`
- Create: `src/main/java/com/xn/report/excel/ExcelGenerator.java`
- Create: `src/main/java/com/xn/report/word/WordGenerator.java`
- Test: `src/test/java/com/xn/report/execution/DefaultReportPipelineTest.java`

- [ ] **Step 1: 写严格执行顺序测试**

```java
@Test
void queriesOnceThenGeneratesExcelBeforeWordAndPublishesBoth() {
    pipeline.execute(request);

    InOrder order = inOrder(
            loader, validator, queryService, analysisService,
            excelGenerator, wordGenerator, publisher);
    order.verify(loader).load(request.getReportConfigPath());
    order.verify(validator).validateOrThrow(any(ReportDefinition.class));
    order.verify(queryService).executeAll(any(), anyMap());
    order.verify(analysisService).analyze(any(), any());
    order.verify(excelGenerator).generate(any(), any(), any());
    order.verify(wordGenerator).generate(any(), any(), any());
    order.verify(publisher).publish(any(), any(), any());
    verifyNoMoreInteractions(queryService);
}
```

- [ ] **Step 2: 写 Word 失败不发布测试**

```java
@Test
void returnsFailedAndDoesNotPublishWhenWordGenerationFails() {
    when(wordGenerator.generate(any(), any(), any()))
            .thenThrow(new ReportException(ReportErrorCode.DOCX_001, "word failed"));

    ReportExecutionResult result = pipeline.execute(request);

    assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    verifyNoInteractions(publisher);
}
```

- [ ] **Step 3: 运行失败测试**

Run:

```powershell
mvn -Dtest=DefaultReportPipelineTest test
```

Expected: FAIL，入口和流水线不存在。

- [ ] **Step 4: 实现阶段流水线**

`DefaultReportPipeline.execute()` 固定阶段：

```java
loadConfig();
validateConfig();
queryDatasets();
analyze();
generateExcel();
generateWord();
validateOutputs();
publish();
return successResult();
```

每个阶段更新 `ExecutionContext.stage` 和 MDC。失败时清理工作区并返回 `FAILED`，不吞掉原始 cause。

- [ ] **Step 5: 验证并提交**

Run:

```powershell
mvn -Dtest=DefaultReportPipelineTest test
git add src/main/java/com/xn/report/entry src/main/java/com/xn/report/execution src/main/java/com/xn/report/analysis src/main/java/com/xn/report/excel/ExcelGenerator.java src/main/java/com/xn/report/word/WordGenerator.java src/test/java/com/xn/report/execution
git commit -m "feat: orchestrate complete report generation"
```

Expected: 顺序、单次查询、失败清理和结果状态测试通过。

---

### Task 16: 示例配置、端到端测试和发布验收

**Files:**
- Create: `config/api-design-efficiency.yml`
- Create: `config/sql/center-monthly.sql`
- Create: `config/sql/person-annual.sql`
- Create: `templates/api-design-efficiency.xlsx`
- Create: `templates/api-design-efficiency.docx`
- Create: `src/test/java/com/xn/report/e2e/ReportGenerationE2ETest.java`
- Create: `src/test/java/com/xn/report/e2e/FiftyDatasetExecutionIT.java`
- Create: `docs/配置与模板使用说明.md`
- Create: `README.md`

- [ ] **Step 1: 编写经过修正的示例 SQL**

`center-monthly.sql` 必须使用：

```sql
SELECT
    t.NOD_NM AS nodeName,
    t.CENTR_NM AS centerName,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m') AS statMonth,
    ROUND(AVG(TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM)), 2) AS avgHours,
    MAX(b.baselineHours) AS baselineHours
FROM XN_API_DESIGN_FLOW_APPROVAL t
LEFT JOIN (
    SELECT
        NOD_NM,
        CENTR_NM,
        AVG(TIMESTAMPDIFF(HOUR, APRV_BGN_TM, APRV_END_TM)) AS baselineHours
    FROM XN_API_DESIGN_FLOW_APPROVAL
    WHERE APRV_END_TM >= :baselineStartTime
      AND APRV_END_TM < :baselineEndTimeExclusive
      AND CENTR_NM IN (:centerNames)
    GROUP BY NOD_NM, CENTR_NM
) b
  ON t.NOD_NM = b.NOD_NM
 AND t.CENTR_NM = b.CENTR_NM
WHERE t.APRV_END_TM >= :startTime
  AND t.APRV_END_TM < :endTimeExclusive
  AND t.CENTR_NM IN (:centerNames)
  AND t.APRV_BGN_TM IS NOT NULL
  AND t.APRV_END_TM >= t.APRV_BGN_TM
GROUP BY
    t.NOD_NM,
    t.CENTR_NM,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m')
ORDER BY t.CENTR_NM, statMonth
```

- [ ] **Step 2: 创建完整示例配置和模板**

配置必须包含：

- 运行参数和集合参数。
- 至少 5 个真实数据集。
- 一个嵌套 `AND/OR` 规则。
- 一个堆积柱形加折线组合图。
- 一个审批耗时趋势图和一个按 `1天之内/7天之内/7天以上` 分组的饼图。
- 每条 SQL 配置独立、可见且合法的 Sheet 名。
- Word 封面变量、真实目录域、四级标题样式和 `{{sections}}` 锚点。
- 动态章节树及场景说明、构成要素、固定文字、规则文字、图表、表格、单位和附件组件。
- 全年趋势与当月分析两类规则生成文字。
- 全局空数据、字段缺失、类型、null 策略。

- [ ] **Step 3: 写端到端测试**

```java
@Test
void createsMatchingExcelAndWordFromOneDatasetSnapshot() throws Exception {
    ReportExecutionResult result = reportEntry.generate(request());

    assertThat(result.getStatus()).isIn(
            ExecutionStatus.SUCCESS,
            ExecutionStatus.SUCCESS_WITH_WARNINGS);
    assertThat(result.getExcelPath()).exists();
    assertThat(result.getWordPath()).exists();

    try (XSSFWorkbook workbook = new XSSFWorkbook(
            Files.newInputStream(result.getExcelPath()));
         XWPFDocument document = new XWPFDocument(
            Files.newInputStream(result.getWordPath()))) {
        assertThat(workbook.getSheet("中心-每月").getSheetState())
                .isEqualTo(SheetVisibility.VISIBLE);
        assertThat(workbook.getSheet("个人-全年").getSheetState())
                .isEqualTo(SheetVisibility.VISIBLE);
        assertThat(document.getText()).contains("平均审批耗时");
        assertThat(document.getText()).contains("研发效能报告");
        assertThat(document.getText()).contains("全年变化趋势");
        assertThat(document.getText()).contains("当月分析");
        assertThat(document.getText()).contains("附件信息");
        assertThat(document.getAllPictures()).isNotEmpty();
    }
}
```

端到端夹具采用截图对应的报告结构：封面、自动目录、交付速率/交付质量章节、设计平台审批时长趋势、审批时长区间分布、年度说明、当月分析、单位和附件信息。结构测试还需检查标题层级、目录域、`w:updateFields`、组件顺序、饼图数量/占比一致性和无水印。

- [ ] **Step 4: 写 50 数据集和失败回滚测试**

生成 50 个只读查询定义并断言全部执行一次。模拟第 50 个查询失败，断言输出目录为空、临时执行目录被清理。

- [ ] **Step 5: 运行完整验证**

Run:

```powershell
mvn clean verify
```

Expected:

- `BUILD SUCCESS`
- 单元测试失败 0 个
- MySQL 5.7 集成测试失败 0 个
- 端到端生成 `.xlsx` 和 `.docx`
- 每条 SQL 均生成独立可见 Sheet
- 图表系列公式指向数据页
- Word 包含封面、动态章节、真实目录、两类分析文字、动态表格、附件和 PNG 图表
- Word 目录设置为打开时更新，标题层级和组件顺序正确
- 趋势、区间分布、数量/占比和说明文字相互一致
- Word 不包含生成器添加的水印
- 不存在未解析占位符

- [ ] **Step 6: 人工文档验收**

打开端到端生成的 Excel：

1. 选择组合图。
2. 点击“图表设计 → 选择数据”。
3. 确认分类轴和三个系列均引用对应 SQL 的 `中心-每月` Sheet。
4. 修改该 Sheet 的测试值，确认原生图表更新。

打开 Word：

1. 确认封面变量、字体、页眉、页脚和模板样式保留。
2. 打开文档后更新目录，确认页码、点状引导线和 1—3 级标题正确。
3. 确认章节树、自动编号和组件顺序与配置一致。
4. 确认趋势图、区间分布饼图清晰且未拉伸。
5. 确认固定模板文字、年度趋势、当月分析及异常文字与数据一致。
6. 确认饼图标签与说明中的数量和占比一致。
7. 确认表格无截断、单位和附件信息存在，且无水印。

- [ ] **Step 7: 更新使用说明并提交**

`docs/配置与模板使用说明.md` 必须说明：

- 目录结构。
- YAML/JSON 字段。
- SQL 别名规范。
- Excel 表和图表替代文字规范。
- Word 封面、标题样式、目录域、章节锚点和组件规范。
- 固定模板/规则生成文字及趋势、区间分布配置规范。
- 入口类调用示例。
- 常见错误码。

Run:

```powershell
git add config templates src README.md docs
git commit -m "feat: add end-to-end efficiency report example"
git status --short
```

Expected: 提交成功，`git status --short` 无输出。

---

## 需求覆盖映射

| 需求范围 | 执行任务 |
|---|---|
| Java 1.8、Spring Boot 2.7、依赖锁定 | Task 1 |
| YAML/JSON、SQL 文件、Schema、路径校验 | Task 2、Task 3 |
| 通用数据集、依赖排序、50 条 SQL | Task 4、Task 6、Task 16 |
| MySQL 5.7、命名参数、集合参数、只读事务 | Task 5、Task 6 |
| 数据转换和动态标准 | Task 7、Task 8 |
| 嵌套 AND/OR 和异常文字 | Task 8、Task 9 |
| 固定模板、规则生成、趋势和区间分析 | Task 9、Task 16 |
| 全类型图表模型、堆积图、组合图 | Task 10 |
| 每条 SQL 一个可见 Sheet 和原生图表 | Task 11、Task 12 |
| Excel“选择数据”可追溯 | Task 12、Task 16 |
| Word 封面、动态章节、自动编号和自动目录 | Task 2、Task 13、Task 16 |
| Word 文字、动态表格、附件和高清图表 | Task 9、Task 10、Task 13 |
| Word 不生成水印 | Task 13、Task 16 |
| 空数据、缺失字段、类型和 null 策略 | Task 6、Task 14 |
| Excel 后 Word、单次查询、统一入口 | Task 15 |
| 临时目录、冲突策略、无半成品发布 | Task 14、Task 15 |
| 安全、资源限制、端到端验收 | Task 5、Task 6、Task 9、Task 10、Task 16 |

---

## 最终完成标准

- [ ] `mvn clean verify` 返回 `BUILD SUCCESS`。
- [ ] Java 编译目标为 1.8。
- [ ] MySQL 5.7 集成测试通过。
- [ ] 单份报表执行 50 条 SQL 的测试通过。
- [ ] Excel 在 Word 之前生成。
- [ ] 每条 SQL 生成一个名称合法且可见的独立 Sheet。
- [ ] Excel 组合图包含两个堆积柱形系列和一个折线系列。
- [ ] Excel“选择数据”可追溯到数据页范围。
- [ ] Word 包含相同数据模型生成的图表图片。
- [ ] 嵌套 `AND/OR`、动态标准和异常文字测试通过。
- [ ] 固定模板和规则生成两类文字、趋势、极值月份及区间分布测试通过。
- [ ] Word 封面、动态章节树、四级标题和自动编号与配置一致。
- [ ] Word 包含真实目录域，设置 `w:updateFields`，打开时可更新页码和引导点。
- [ ] Word 场景说明、构成要素、图表说明、单位和附件组件顺序正确。
- [ ] Word 不包含生成器添加的水印。
- [ ] 空数据、字段缺失、类型不匹配和 null 策略测试通过。
- [ ] SQL 只读防护、命名参数、集合参数和最大行数测试通过。
- [ ] Word 失败和发布失败均不留下半成品。
- [ ] 所有输出资源、数据库资源和临时文件可靠关闭或清理。
- [ ] README、配置说明、示例 SQL 和模板与实现一致。
