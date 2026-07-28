# 效能报表自动生成组件需求规格说明书

文档版本：V1.0  
文档状态：需求确认稿  
编制日期：2026-07-27  
适用技术栈：Java 1.8、Spring Boot 2.7、MySQL 5.7

## 1. 文档目的

本文档定义一个配置驱动的效能报表自动生成组件。组件从 MySQL 5.7 执行多条 SQL，形成不依赖固定业务实体的通用数据集，在明细数据上执行异常识别与文字生成，并按照 Excel、Word 模板依次输出带有数据、图表和分析文字的 `.xlsx`、`.docx` 文件。

本文档作为设计、开发、测试和验收的共同依据。

## 2. 项目背景与目标

### 2.1 背景

效能报表通常包含数十项指标、不同粒度的明细数据、同期或基准值对比、异常项分析以及多种图表。指标字段、统计口径和展示版式会持续变化，如果将 SQL 结果绑定到固定 Java 实体，或将图表和表格写死在代码中，每次调整都需要修改和发布程序。

### 2.2 建设目标

- 使用 YAML/JSON 描述报表、数据集、异常规则、文字和图表绑定。
- SQL 可独立存放在 `.sql` 文件中，修改查询字段时不要求新增固定实体类。
- 单份报表支持执行几十条具名 SQL，并支持数据集依赖关系。
- 支持基于明细数据和标准值的嵌套 `AND/OR` 异常筛选。
- 先生成 Excel，再生成 Word；两份文件使用同一批查询结果和分析结果。
- 支持 Excel 常用图表、堆积图、组合图及模板中已有的原生图表。
- 每条 SQL（数据集）生成一个独立且可见的 Excel Sheet，用户可通过“选择数据”查看图表引用的对应数据页范围。
- Word 模板控制封面、字体、页眉页脚和基础样式，YAML/JSON 配置动态章节树和章节内容。
- Word 使用真实标题样式生成可更新的自动目录，目录包含页码和引导点。
- 分析文字同时支持固定模板占位符和规则计算结果，不依赖大模型。
- 对空数据、字段缺失、类型不匹配和 SQL 失败提供明确、可配置的处理策略。

### 2.3 成功标准

新增或调整一个字段、SQL、规则、文字或常规图表时，原则上只需要修改 SQL、YAML/JSON 和模板文件，不修改 Java 业务代码。一次入口调用成功后，应在输出目录得到一份可打开的 Excel 和一份可打开的 Word，且图表、表格和分析文字与查询数据一致。

## 3. 范围

### 3.1 本期范围

- MySQL 5.7 数据源。
- YAML、JSON 报表配置。
- 外部 SQL 文件与少量内联 SQL。
- 统一 Java 入口类直接调用。
- 命名参数、集合参数和运行参数。
- 多数据集执行、依赖排序和通用结果模型。
- 数据筛选、排序、分组、去重、格式化和异常规则。
- Excel、Word 模板填充。
- Excel 原生图表、模板原生复杂图表、Word 高清图表图片。
- Word 封面变量、动态章节树、自动编号、自动目录、场景说明、构成要素、图表说明、单位和附件信息。
- 同比/基准对比、趋势方向、极值月份、异常月份和可配置区间分布的确定性分析文字。
- 本地或挂载文件目录输出。
- 执行日志、告警、失败清理和原子发布。

### 3.2 本期不包含

- REST API。
- 定时任务。
- Web 配置管理页面。
- 多租户、用户权限和审批流程。
- 非 MySQL 数据库兼容。
- 在线预览、邮件或即时消息发送。
- 自然语言大模型生成分析结论。

## 4. 总体设计

### 4.1 设计原则

- 配置驱动：业务口径、字段绑定、规则和展示关系外置。
- 结果通用：SQL 结果使用通用行数据，不绑定团队、项目、人员等固定对象。
- 数据只查一次：Excel、Word 和图表共享同一执行上下文，避免两份文件数据不一致。
- 先校验后执行：配置、依赖、参数、字段和模板占位符在生成前尽可能校验。
- 先临时生成后发布：任一步失败时不得在正式输出目录留下半成品。
- 可追溯：日志可定位报表、数据集、规则、组件和输出文件。

### 4.2 逻辑组件

1. `ReportEntry`：统一入口，接收配置路径和运行参数。
2. `ConfigLoader`：读取 YAML/JSON，解析引用的 SQL 和模板。
3. `ConfigValidator`：验证必填项、唯一标识、依赖环、字段引用和策略值。
4. `DatasetPlanner`：按依赖关系对数据集进行拓扑排序。
5. `SqlExecutor`：使用命名参数安全执行只读 SQL。
6. `DatasetContext`：保存标量、单行和列表数据集。
7. `TransformEngine`：完成筛选、排序、分组、去重和派生值。
8. `RuleEngine`：执行嵌套条件，产生异常记录和分析结果。
9. `TextRenderer`：根据模板生成摘要、逐条描述和分组段落。
10. `ChartEngine`：生成 Excel 原生图表或图表图片，并处理模板原生图表数据源。
11. `ExcelGenerator`：填充 Excel 数据、表格、指标和图表。
12. `WordGenerator`：填充 Word 指标、表格、文字和图表图片。
13. `OutputPublisher`：校验文件后原子移动至输出目录。
14. `ExecutionReporter`：返回执行状态、耗时、警告和文件路径。

### 4.3 执行流程

1. 入口类接收报表配置路径、模板根目录、输出目录和运行参数。
2. 加载主配置、SQL 文件、Excel 模板和 Word 模板。
3. 完成静态配置校验和数据集依赖排序。
4. 建立只读数据库访问上下文。
5. 按排序结果执行所有具名 SQL，将结果写入 `DatasetContext`。
6. 校验结果字段、类型、空值和数据形态。
7. 执行数据转换、异常规则和文字生成。
8. 复制并填充 Excel 模板，更新可见数据页和图表。
9. 基于相同数据和图表配置生成 Word 所需图像。
10. 复制并填充 Word 模板。
11. 打开级校验两个文件，计算最终文件名并发布到输出目录。
12. 返回 `ReportExecutionResult`。

## 5. 入口与目录

### 5.1 统一入口

入口类应提供一个稳定方法，调用方不需要了解内部生成步骤。建议概念接口如下：

```java
ReportExecutionResult generate(
    String reportConfigPath,
    Map<String, Object> runtimeParameters
);
```

目录可以在 Spring Boot 配置中定义默认值，也允许报表配置覆盖：

```yaml
report-engine:
  config-root: ./config
  sql-root: ./config/sql
  template-root: ./templates
  output-root: ./output
  temp-root: ./temp
```

### 5.2 入口返回结果

`ReportExecutionResult` 至少包含：

- 执行编号。
- 报表编码。
- 开始时间、结束时间和总耗时。
- 成功、部分跳过或失败状态。
- Excel 最终路径。
- Word 最终路径。
- 数据集行数摘要。
- 警告列表。
- 失败阶段、错误编码和可读错误信息。

## 6. 配置模型

### 6.1 配置组织

每份报表使用一个主 YAML/JSON 文件，可引用多个独立 SQL 文件。主配置中的标识必须在报表范围内唯一。

```yaml
report:
  code: efficiency
  name: 效能报告
  excelTemplate: efficiency.xlsx
  wordTemplate: efficiency.docx
  excelFileName: "效能报告_${period}.xlsx"
  wordFileName: "效能报告_${period}.docx"

datasets:
  - id: centerMonthly
    sheetName: 中心-每月
    sqlFile: sql/center-monthly.sql
    resultType: list
    parameters: [startTime, endTimeExclusive, centerNames]
    expectedFields:
      nodeName: STRING
      centerName: STRING
      statMonth: STRING
      avgHours: DECIMAL
      baselineHours: DECIMAL

rules: []
charts: []
excel: {}
word:
  cover:
    title: 研发效能报告
    organization: 软件开发二中心
    reportPeriod: 2026年6月
    preparedBy: 效能小组
    preparedDate: 2026年7月23日
  toc:
    enabled: true
    maxLevel: 3
    updateOnOpen: true
  sections:
    - id: deliverySpeed
      title: 交付速率
      level: 1
      emptyStrategy: KEEP
      children:
        - id: apiApprovalDuration
          title: 设计平台审批时长
          level: 2
          components:
            - { type: SCENARIO, text: "反映审核人员在周期内审批节点的时长。" }
            - { type: KEY_FACTORS, text: "API设计平台审批时长、数据库表设计平台审批时长。" }
            - { type: CHART, chartId: apiApprovalTrend }
            - { type: RULE_TEXT, narrativeId: apiApprovalAnnualSummary }
            - { type: UNIT, text: 小时 }
            - { type: ATTACHMENT, text: 附件信息 }
policies: {}
```

### 6.2 配置校验

系统必须校验：

- 报表编码、数据集、规则、图表和占位符标识是否重复。
- 配置引用的 SQL 和模板是否存在且可读。
- 数据集依赖是否引用有效标识，是否存在循环依赖。
- 参数是否声明、是否提供、是否允许为空。
- 图表分类字段和系列字段是否存在于数据集声明中。
- 规则字段、比较值来源和文字占位符是否合法。
- Word 封面变量、章节标识、父子层级、组件引用和标题级别是否合法。
- Word 模板是否包含约定的 `Heading 1` 至 `Heading 4` 样式和真实目录域。
- 输出文件名是否包含非法路径字符或目录穿越。
- 策略、图表类型、格式化器和操作符是否受支持。

## 7. 数据查询与通用数据集

### 7.1 SQL 要求

- 支持外部 `.sql` 文件和可选内联 SQL，优先使用外部文件。
- SQL 必须使用命名参数，不允许用字符串拼接用户输入。
- 集合参数应支持 `IN (:centerNames)` 等写法。
- 首版只允许单条只读查询；禁止 DDL、DML、多语句和存储过程调用。
- SQL 文件必须使用 UTF-8，不能包含 `&gt;`、`&#39;` 等 HTML 转义。
- 复杂表达式和聚合字段必须提供稳定、语义化的别名。

### 7.2 结果形态

数据集支持：

- `scalar`：单值。
- `single`：单行 `Map<String, Object>`。
- `list`：多行 `List<Map<String, Object>>`。

字段名默认使用 SQL 别名。字段匹配是否区分大小写由全局配置统一决定，默认不区分大小写并保留声明名称。

### 7.3 数据集依赖

数据集可通过 `dependsOn` 声明依赖。系统必须执行拓扑排序；出现循环依赖时应在连接数据库前失败。依赖数据集的标量或单行字段可以作为后续 SQL 的参数，但列表展开必须显式声明并受最大数量限制。

### 7.4 转换能力

配置可在 SQL 结果上执行：

- 条件筛选。
- 多字段排序。
- 分组。
- 去重。
- 取前 N 条。
- 字段重命名与显示格式化。
- 简单派生字段，如差值、比率和状态标签。

复杂统计口径仍应放在 SQL 中，避免在配置中形成难以维护的脚本语言。

## 8. 示例 SQL 适配要求

现有“部门-每月、中心-全年、中心-每月、中心-个人-全年、中心-个人-每月”等 SQL 可以纳入配置模型，但必须满足以下规范：

- `round(avg(...),2)` 必须设置别名，例如 `avgHours`。
- 年度基准值使用 `baselineHours`，不使用以数字开头的 `2025jzz`。
- 月份使用 `DATE_FORMAT(APRV_END_TM, '%Y-%m') AS statMonth`。
- 日期范围使用左闭右开参数：`>= :startTime AND < :endTimeExclusive`。
- 禁止使用不存在的 `2026-06-31` 和不清晰的 `24:00:00`。
- 中心级基准子查询必须同时按 `CENTR_NM`、`NOD_NM` 关联。
- 个人级基准子查询必须同时按 `APRV_PSN_NO`、`NOD_NM` 关联。
- 在 MySQL 5.7 `ONLY_FULL_GROUP_BY` 模式下，非聚合字段必须进入 `GROUP BY`，或使用语义明确的聚合。
- 组织清单应使用集合参数，不在多条 SQL 中重复硬编码。
- 应过滤审批开始或结束时间为空、结束早于开始、未达到纳入条件的记录。

推荐查询结构示例：

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
GROUP BY
    t.NOD_NM,
    t.CENTR_NM,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m')
ORDER BY
    t.CENTR_NM,
    statMonth;
```

## 9. 异常规则与文字生成

### 9.1 条件模型

规则支持任意层级的 `AND`、`OR` 条件组。叶子条件支持：

- `EQ`、`NE`。
- `GT`、`GE`、`LT`、`LE`。
- `IN`、`NOT_IN`。
- `BETWEEN`。
- `CONTAINS`、`STARTS_WITH`、`ENDS_WITH`。
- `IS_NULL`、`IS_NOT_NULL`。
- 日期区间和布尔判断。

右侧比较值可以来自：

- 配置常量。
- 运行参数。
- 当前记录的另一个字段。
- 其他标量或单行数据集字段。

### 9.2 规则结果处理

异常记录可继续执行：

- 多字段排序。
- 按中心、节点、月份或其他字段分组。
- 去重。
- 限制最大输出条数。
- 计算异常数量、占比和最大值。

### 9.3 文字模板

文字模板支持字段占位符、默认值和格式化：

```yaml
rules:
  - id: approvalTimeout
    dataset: personAnnual
    condition:
      operator: AND
      children:
        - field: avgHours
          operator: GT
          valueFrom: baselineSummary.standardHours
        - operator: OR
          children:
            - { field: onJob, operator: EQ, value: true }
            - { field: groupCategory, operator: IN, value: [A, B] }
    result:
      sort:
        - { field: avgHours, direction: DESC }
      maxItems: 10
      message: "${personName}平均审批耗时${avgHours|0.00}小时，超过标准${standardHours|0.00}小时"
      separator: "；"
      emptyMessage: "本期未发现超过标准的异常数据。"
```

系统支持单条总结、逐条描述和按字段分组的多段描述。文字必须来源可追溯，不允许在字段缺失或转换失败时静默输出错误内容。

### 9.4 分析文字的两种来源

分析文字仅使用以下两种确定性来源，可在同一章节中组合使用：

1. `FIXED_TEMPLATE`：配置固定句式，通过占位符引用运行参数、数据集字段、聚合值和规则结果。
2. `RULE_GENERATED`：根据配置规则计算同比/基准差异、上升或下降趋势、最大值、最小值、异常月份、异常人员数量及区间分布，再套用受控文本模板。

首版不调用大模型。每段分析文字应记录来源数据集、规则或分析器标识，确保结果可复核。

### 9.5 趋势与区间分析

- 支持当前值与上年同期、全年基准或配置标准值比较，并输出增长、下降、持平及差值/比例。
- 支持识别最大值、最小值及其月份，识别连续上升、连续下降或波动趋势。
- 支持“全年变化趋势”和“当月分析”分别配置规则和句式。
- 支持配置区间边界及开闭规则，例如 `1天之内`、`1天以上且7天之内`、`7天以上`。
- 区间统计支持输出数量、占比或“数量 + 占比”，并可同时作为饼图/圆环图数据和说明文字来源。

## 10. 图表需求

### 10.1 图表能力

动态生成模式至少支持：

- 簇状柱形图。
- 堆积柱形图。
- 百分比堆积柱形图。
- 折线图。
- 条形图及堆积条形图。
- 饼图、圆环图。
- 面积图及堆积面积图。
- XY 散点图、气泡图。
- 雷达图。
- 组合图。

模板原生模式应保留 Excel 模板中可由 Excel 打开的其他原生图表，包括股价图等特殊图表。对无法由动态引擎生成的类型，必须使用模板原生模式或可插拔渲染器，不得静默降级为另一种图表。

### 10.2 系列级配置

每个系列可以独立配置：

- 数据字段和显示名称。
- 柱形、堆积柱形、折线、面积等系列类型。
- 主坐标轴或次坐标轴。
- 堆积组。
- 颜色、线型、线宽、标记。
- 数据标签、数值格式和空值处理。
- 饼图/圆环图标签显示数量、百分比或二者同时显示。
- 图例顺序。

```yaml
charts:
  - id: centerEventChart
    title: 中心事件数
    mode: templateNative
    dataset: centerMonthly
    excelSheet: 中心-每月
    excelTable: tbl_center_monthly
    categoryField: statMonth
    groupByField: nodeName
    series:
      - field: uncertainCount
        name: 不定责事件
        type: stackedColumn
        stackGroup: event
      - field: certainCount
        name: 定责事件
        type: stackedColumn
        stackGroup: event
      - field: baseline
        name: 中心基准值
        type: line
        axis: primary
        marker: true
```

### 10.3 Excel 图表

- 每条 SQL（数据集）必须生成一个独立数据 Sheet，不把多条 SQL 结果合并到统一“图表数据”页。
- 数据 Sheet 必须保持可见，不自动隐藏。
- Sheet 名称由数据集配置指定；必须唯一，最长 31 个字符，且不得包含 Excel 非法字符 `\ / ? * [ ] :`。
- 模板复杂图表应引用具名 Excel 表或明确的单元格区域。
- 程序填充或扩展数据区域后，图表引用必须同步更新。
- 用户选中图表并执行“图表设计 → 选择数据”时，必须能看到分类轴和各系列引用的数据页及范围。
- 普通图表可以由程序按配置创建为可编辑的 Excel 原生图表。
- 复杂组合图优先在模板中预设为原生图表，程序只维护数据源。

### 10.4 Word 图表

Word 中的图表以高清图片形式插入，用于阅读、打印和版式稳定。图片必须与 Excel 图表使用同一数据集、分类字段、系列字段、标题、图例和颜色配置。配置可控制图片宽高、分辨率、对齐和标题。

## 11. Excel 模板与输出

### 11.1 模板绑定

Excel 支持：

- 单元格占位符绑定标量和单行字段。
- 具名 Excel 表绑定列表数据。
- 每条 SQL 对应的可见数据 Sheet 承载该数据集完整查询结果。
- 模板原生图表绑定具名表或数据范围。
- 数字、百分比、日期、时长和空值格式。

### 11.2 生成行为

- 复制模板后再修改，不覆盖原模板。
- 写入数据时保留模板样式、列宽、冻结窗格和打印设置。
- 列表扩展时复制数据行样式和公式策略。
- 数据量变化时更新 Excel 表范围和图表系列范围。
- 生成完成后校验工作簿能被重新打开，工作表、表和图表标识有效。

## 12. Word 模板与输出

### 12.1 占位符

建议约定：

```text
{{value:teamSummary.avgHours}}
{{table:defectDetails}}
{{chart:centerEventChart}}
{{text:approvalTimeout}}
```

- `value`：标量或单行字段。
- `table`：动态明细表。
- `chart`：高清图表图片。
- `text`：异常分析或摘要文字。

### 12.2 生成行为

- 复制模板后再修改，不覆盖原模板。
- Word 模板负责封面版式、字体、页眉、页脚、页面设置和基础样式；配置负责内容结构和数据绑定。
- 封面支持报告标题、机构/中心、报告周期、编制单位/人员和编制日期变量。
- 配置中的 `sections` 构成可递归的动态章节树，支持至少四级标题及自动编号。
- 章节组件按配置顺序渲染，支持场景说明、构成要素、固定文字、规则文字、图表、表格、单位和附件信息。
- 章节空数据策略支持 `KEEP`（保留章节）、`SHOW_EMPTY`（显示空态文字）和 `SKIP`（跳过章节及其目录项）。
- 保留模板中的页眉、页脚、标题样式和段落格式。
- 动态表格应支持表头、列宽、对齐、分页重复表头和空数据提示。
- 图片按占位符所在段落插入，避免覆盖、拉伸和越界。
- 文本替换必须能够处理占位符被 Word 拆分为多个文本运行的情况。
- 模板必须包含真实 Word 目录域；程序根据实际生成的标题更新目录范围，并在 `word/settings.xml` 设置 `<w:updateFields w:val="true"/>`，使 Word 打开文档时更新目录域。
- 目录默认收录 1—3 级标题，最大级别可配置，目录页码和点状引导线由 Word 自动生成。
- 生成器不添加水印，输出校验确认文档中不存在程序生成的水印对象。
- 生成完成后校验文档能被重新打开，不存在未解析的必填占位符。

## 13. 数据异常与处理策略

支持全局默认策略，并允许在数据集、规则或组件级覆盖：

```yaml
policies:
  emptyData: USE_DEFAULT
  missingField: FAIL
  typeMismatch: FAIL
  nullValue: RULE_NOT_MATCHED
```

### 13.1 空数据

- `SKIP`：跳过对应表格、图表或文字。
- `USE_DEFAULT`：使用配置默认值。
- `OUTPUT_MESSAGE`：输出“本期无数据”等提示。
- `FAIL`：终止生成。

### 13.2 字段缺失

- `FAIL`：立即失败，默认用于发现 SQL 别名或模板绑定错误。
- `USE_DEFAULT`：使用字段默认值。
- `WARN_AND_SKIP`：记录警告并跳过当前记录或组件。

### 13.3 类型不匹配

- `FAIL`：终止生成。
- `SAFE_CONVERT`：只允许可验证的安全转换，例如数值字符串转数值、指定格式字符串转日期。
- `WARN_AND_SKIP`：记录原字段、目标类型和原因后跳过。

### 13.4 空值

- `RULE_NOT_MATCHED`：普通比较条件判定为不成立。
- `USE_DEFAULT`：使用字段默认值后继续。
- `ALLOW`：仅用于空值操作符或允许空值的展示字段。
- `FAIL`：关键指标为空时终止。

默认策略为：空数据输出友好提示，字段缺失失败，类型只允许显式启用的安全转换，普通空值不命中异常规则。

## 14. 错误处理与日志

### 14.1 错误阶段

错误应按阶段分类：

- 配置加载错误。
- 配置校验错误。
- 数据库连接错误。
- SQL 执行错误。
- 结果校验错误。
- 规则执行错误。
- 图表生成错误。
- Excel 生成错误。
- Word 生成错误。
- 文件发布错误。

### 14.2 日志要求

日志至少包含执行编号、报表编码、数据集或组件标识、阶段、耗时、行数、警告和错误摘要。SQL 参数中的敏感值应脱敏；默认不打印完整明细数据。日志不得记录数据库密码。

### 14.3 失败清理

所有文件先写入本次执行专属临时目录。Excel 或 Word 任一步失败时，应删除本次临时文件；正式输出目录不得出现仅有 Excel、缺少 Word 的半套结果。发布时先校验目标路径，再使用同文件系统内的原子移动或安全替换。

## 15. 非功能需求

### 15.1 兼容性

- Java 1.8。
- Spring Boot 2.7.x。
- MySQL 5.7。
- 生成文件应可由 Microsoft Excel、Microsoft Word 的常用现代版本打开。
- 配置、SQL 和模板路径应兼容 Windows，并避免写死绝对路径。

### 15.2 性能与资源

- 单次执行的 SQL 数量至少支持 50 条。
- SQL 应顺序或按依赖分层执行；首版以正确性为先，不要求并行查询。
- 大明细数据应采用数据库端聚合、查询超时、最大行数和内存上限保护。
- 图片应控制分辨率和文件大小，避免 Word 体积失控。
- 所有数据库、工作簿、文档和流资源必须可靠关闭。

### 15.3 安全

- 数据库账号应使用只读权限。
- 数据库密码不得明文写入报表 YAML/JSON，使用 Spring Boot 外部配置或环境注入。
- 禁止路径穿越和写出允许目录之外。
- SQL 仅允许单条 `SELECT` 或 `WITH...SELECT`；MySQL 5.7 不支持通用 CTE，实际配置以 MySQL 5.7 语法为准。
- 命名参数必须通过 JDBC 参数绑定。
- 输出日志和异常信息避免泄露个人敏感数据。

### 15.4 可维护性

- 核心模块通过接口隔离，图表渲染器、格式化器和数据转换器可扩展。
- 配置应提供 JSON Schema 或等价校验说明。
- 错误编码、操作符、图表类型和策略值集中定义。
- 相同数据集不得为 Excel 和 Word 重复查询。

## 16. 测试要求

### 16.1 单元测试

- YAML/JSON 解析与默认值。
- 数据集依赖排序和循环检测。
- 命名参数及集合参数。
- 通用类型转换。
- 嵌套 `AND/OR` 规则。
- 文字格式化和空数据文本。
- 各策略分支。
- 图表系列配置和主次坐标轴。
- 固定模板文字、规则生成文字、同比/基准趋势、极值月份和区间分布。
- 动态章节树、组件顺序、四级标题、自动编号和章节空数据策略。
- Word 目录域及打开时自动更新标记。
- 占位符识别和残留检查。

### 16.2 集成测试

- 使用 MySQL 5.7 兼容环境执行示例 SQL。
- 一次执行不少于 50 个数据集。
- 验证中心、月份、个人等多粒度数据。
- 验证堆积柱形加折线组合图。
- 验证审批时长趋势图、区间分布饼图及数量/占比标签。
- 验证模板原生图表在数据行数变化后仍引用正确范围。
- 验证 Word 封面、动态章节、目录、说明文字、单位和附件信息完整生成。
- 验证 Excel 生成成功后 Word 失败时不发布半成品。
- 验证重复文件名、无写权限、磁盘不足等文件异常。

### 16.3 文档验收测试

- Excel、Word 均可正常打开且无修复提示。
- Excel 数据页可见。
- SQL 数据集与 Excel Sheet 一一对应，Sheet 名称、列名和数据行与配置及查询结果一致。
- Excel 中选中图表并“选择数据”可看到正确引用范围。
- 图表分类、系列、图例、标签、坐标轴与配置一致。
- Word 图表与 Excel 使用相同数据。
- Word 封面字段正确，标题层级和自动编号与配置一致。
- Word 目录为真实目录域，打开时可自动更新页码和引导点。
- 场景说明、构成要素、全年趋势、当月分析、图表说明、单位和附件组件顺序正确。
- 区间分布图与说明文字中的数量、占比一致。
- 文档中不存在生成器添加的水印。
- 表格无错列、截断和样式破坏。
- 异常文字与规则筛选结果逐项一致。
- 不存在未替换的必填占位符。

## 17. 验收标准

1. 调用统一入口类，无需 REST 服务即可完成一次报表生成。
2. 入口按配置执行数十条 SQL，并正确处理命名参数和集合参数。
3. SQL 结果不依赖固定 Java 业务实体。
4. 支持标量、单行和列表数据集以及依赖排序。
5. 支持嵌套 `AND/OR` 异常规则和来源于数据集的动态标准值。
6. 按“Excel → Word”顺序生成两个文件。
7. 每条 SQL 生成一个独立可见 Sheet，图表“选择数据”可追溯到对应 SQL 的 Sheet。
8. 支持堆积柱形、折线及二者组合，模板原生模式可保留其他 Excel 图表类型。
9. Word 正确绑定封面，按配置生成动态章节树、自动编号、真实自动目录、表格、两类分析文字、单位、附件和高清图表。
10. 空数据、字段缺失、类型不匹配和空值按配置策略执行。
11. 任一步失败时不在输出目录留下半套或损坏文件。
12. 修改 SQL 字段后，只需同步调整别名声明、绑定配置和模板，不要求新增固定实体类。
13. Word 模板缺少约定标题样式或目录域时明确失败；输出中不添加水印。

## 18. 实施优先级

### 18.1 必须实现

- 配置加载与校验。
- MySQL 5.7 命名参数查询。
- 通用数据集和依赖排序。
- 嵌套规则与文字生成。
- Excel、Word 模板绑定。
- 每条 SQL 一个可见 Excel Sheet。
- 原生普通图表与模板原生复杂图表。
- 组合图、堆积图和 Word 图表图片。
- Word 封面、动态章节树、四级标题、自动编号和自动目录。
- 固定模板文字、规则生成文字、趋势/基准分析和区间分布分析。
- 策略、日志、临时文件和原子发布。

### 18.2 后续可选扩展

- REST API 和定时任务。
- Web 配置管理。
- 更多数据库适配。
- 查询并行化和结果缓存。
- 在线预览、邮件和消息发送。
- 大模型辅助撰写综合分析。

## 19. 最终确认

本需求确认以下关键决策：

- 首版数据库为 MySQL 5.7。
- 技术栈为 Java 1.8、Spring Boot 2.7。
- 使用 YAML/JSON、SQL 文件、Excel 模板和 Word 模板。
- 取消 REST API，仅提供统一 Java 入口类。
- 一份报表支持几十条 SQL。
- 异常条件支持嵌套 `AND/OR`。
- Excel 先生成，Word 后生成。
- 每条 SQL 生成一个独立可见 Excel Sheet，不额外生成统一图表数据页或图表关系页。
- 图表引用可通过 Excel“选择数据”查看。
- 普通图表动态生成，复杂图表优先复用模板原生图表。
- Word 图表使用同数据、同配置生成的高清图片。
- Word 模板控制版式，配置控制动态章节树和组件顺序。
- Word 目录由真实标题样式自动生成，打开文档时更新目录域。
- 分析文字采用固定模板和规则生成两种来源，不使用大模型。
- 支持趋势、基准、极值、异常月份及可配置区间分布分析。
- 不生成水印。
- 对空数据、字段缺失、类型不匹配和空值采用可配置策略。
