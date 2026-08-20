# MySQL 5.7 Test Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增并执行一份只影响 `TEST_` 数据的 MySQL 5.7 幂等种子脚本，使当前 6 个报表 SQL 均返回可用于 Excel、Word、图表和异常文字验证的数据。

**Architecture:** 将 DML 种子脚本与 `config/sql` 下的只读报表查询隔离，放在 `database/mysql57`。脚本在事务内精确清理旧测试记录后插入固定人员、2025 基准记录和 2026 年 1—6 月记录；导入后用数据库校验查询和当前 6 个 SQL 进行验证。

**Tech Stack:** MySQL 5.7.44、UTF-8/utf8mb4、Docker、PowerShell、现有 Java 8/Spring JDBC 报表项目。

---

## 文件结构

- Create: `database/mysql57/seed-test-data.sql` — 幂等清理、人员维度、审批事实数据和校验查询。
- Modify: `docs/使用手册.md` — 增加测试数据导入、重复执行和验证方法。

### Task 1: 创建幂等测试数据脚本

**Files:**
- Create: `database/mysql57/seed-test-data.sql`

- [ ] **Step 1: 写入事务、精确清理和人员维度数据**

脚本开头使用以下结构。删除顺序先事实表、后人员表；`ESCAPE '='` 让 `_` 按普通字符匹配，避免扩大删除范围。

```sql
SET NAMES utf8mb4;
START TRANSACTION;

DELETE FROM XN_API_DESIGN_FLOW_APPROVAL
WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '=';

DELETE FROM xn_grp
WHERE empe_id LIKE 'TEST=_%' ESCAPE '=';

INSERT INTO xn_grp (empe_id, GRP_CTG, GRP_LNG, WTHR_ON_JOB) VALUES
('TEST_U01', '开发组', '测试组长甲', '1'),
('TEST_U02', '开发组', '测试组长甲', '1'),
('TEST_U03', '测试组', '测试组长乙', '1'),
('TEST_U04', '测试组', '测试组长乙', '0'),
('TEST_U05', '开发支持组', '测试组长丙', '0'),
('TEST_U06', '质量组', '测试组长丙', '1'),
('TEST_U07', '运维组', '测试组长丁', '0'),
('TEST_U08', '开发组', '测试组长丁', '1'),
('TEST_U09', '测试组', '测试组长戊', '0'),
('TEST_U10', '研发开发组', '测试组长戊', '1');
```

- [ ] **Step 2: 插入 2025 年基准记录**

插入 10 条记录，每个中心和人员各一条。固定基准耗时依次为 `20、22、18、24、16、20、30、12、26、15` 小时。每条记录使用以下完整列集合，所有测试标识以 `TEST_` 开头：

```sql
INSERT INTO XN_API_DESIGN_FLOW_APPROVAL (
  PCS_NO, PCS_NM, NOD_NO, NOD_NM, DEMD_NO,
  ITTR_ID, ITTR_NM, ITTR_SYS_NM,
  APRV_PSN_NO, APRV_PSN_NM, CENTR_NM, SYS_NM,
  APRV_BGN_TM, APRV_END_TM, APRV_STS
) VALUES
('TEST_2025_C01_01', '测试基准流程-开发一中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C01', 'TEST_I01', '测试发起人01', '测试系统', 'TEST_U01', '测试审批人01', '开发一中心', 'API设计平台', DATE_SUB('2025-06-15 18:00:00', INTERVAL 20 HOUR), '2025-06-15 18:00:00', '1'),
('TEST_2025_C02_01', '测试基准流程-开发二中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C02', 'TEST_I02', '测试发起人02', '测试系统', 'TEST_U02', '测试审批人02', '开发二中心', 'API设计平台', DATE_SUB('2025-06-16 18:00:00', INTERVAL 22 HOUR), '2025-06-16 18:00:00', '1'),
('TEST_2025_C03_01', '测试基准流程-开发三中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C03', 'TEST_I03', '测试发起人03', '测试系统', 'TEST_U03', '测试审批人03', '开发三中心', 'API设计平台', DATE_SUB('2025-06-17 18:00:00', INTERVAL 18 HOUR), '2025-06-17 18:00:00', '1'),
('TEST_2025_C04_01', '测试基准流程-开发四中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C04', 'TEST_I04', '测试发起人04', '测试系统', 'TEST_U04', '测试审批人04', '开发四中心', 'API设计平台', DATE_SUB('2025-06-18 18:00:00', INTERVAL 24 HOUR), '2025-06-18 18:00:00', '1'),
('TEST_2025_C05_01', '测试基准流程-开发五中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C05', 'TEST_I05', '测试发起人05', '测试系统', 'TEST_U05', '测试审批人05', '开发五中心', 'API设计平台', DATE_SUB('2025-06-19 18:00:00', INTERVAL 16 HOUR), '2025-06-19 18:00:00', '1'),
('TEST_2025_C06_01', '测试基准流程-开发六中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C06', 'TEST_I06', '测试发起人06', '测试系统', 'TEST_U06', '测试审批人06', '开发六中心', 'API设计平台', DATE_SUB('2025-06-20 18:00:00', INTERVAL 20 HOUR), '2025-06-20 18:00:00', '1'),
('TEST_2025_C07_01', '测试基准流程-开发七中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C07', 'TEST_I07', '测试发起人07', '测试系统', 'TEST_U07', '测试审批人07', '开发七中心', 'API设计平台', DATE_SUB('2025-06-21 18:00:00', INTERVAL 30 HOUR), '2025-06-21 18:00:00', '1'),
('TEST_2025_C08_01', '测试基准流程-开发八中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C08', 'TEST_I08', '测试发起人08', '测试系统', 'TEST_U08', '测试审批人08', '开发八中心', 'API设计平台', DATE_SUB('2025-06-22 18:00:00', INTERVAL 12 HOUR), '2025-06-22 18:00:00', '1'),
('TEST_2025_C09_01', '测试基准流程-开发九中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C09', 'TEST_I09', '测试发起人09', '测试系统', 'TEST_U09', '测试审批人09', '开发九中心', 'API设计平台', DATE_SUB('2025-06-23 18:00:00', INTERVAL 26 HOUR), '2025-06-23 18:00:00', '1'),
('TEST_2025_C10_01', '测试基准流程-研发中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C10', 'TEST_I10', '测试发起人10', '测试系统', 'TEST_U10', '测试审批人10', '研发中心', 'API设计平台', DATE_SUB('2025-06-24 18:00:00', INTERVAL 15 HOUR), '2025-06-24 18:00:00', '1');
```

- [ ] **Step 3: 插入 2026 年 1—6 月当期记录**

按以下矩阵写入 30 条记录，每行都沿用 Step 2 的完整列集合。流程编号格式为 `TEST_2026_C<中心序号>_<月份>`，审批结束时间落在矩阵月份的 20 日 18:00，开始时间使用 `DATE_SUB(APRV_END_TM, INTERVAL <耗时> HOUR)`。

| 人员 | 中心 | 月份/耗时（小时） | 当期均值与基准关系 | 异常规则预期 |
|---|---|---|---|---|
| TEST_U01 | 开发一中心 | 01/25、03/30、05/35 | 30 > 20 | 命中 |
| TEST_U02 | 开发二中心 | 01/10、03/18、05/20 | 16 < 22 | 不命中 |
| TEST_U03 | 开发三中心 | 01/20、03/22、05/24 | 22 > 18 | 命中 |
| TEST_U04 | 开发四中心 | 01/30、03/40、05/50 | 40 > 24 | 因离岗且非开发组不命中 |
| TEST_U05 | 开发五中心 | 01/40、03/50、05/60 | 50 > 16 | 通过开发组条件命中 |
| TEST_U06 | 开发六中心 | 02/8、04/10、06/12 | 10 < 20 | 不命中 |
| TEST_U07 | 开发七中心 | 02/200、04/180、06/190 | 190 > 30 | 因离岗且非开发组不命中 |
| TEST_U08 | 开发八中心 | 02/15、04/20、06/25 | 20 > 12 | 命中 |
| TEST_U09 | 开发九中心 | 02/20、04/24、06/28 | 24 < 26 | 不命中 |
| TEST_U10 | 研发中心 | 02/60、04/80、06/100 | 80 > 15 | 命中 |

每条记录固定：`NOD_NO='TEST_NODE_API'`、`NOD_NM='API设计'`、`ITTR_SYS_NM='测试系统'`、`SYS_NM='API设计平台'`、`APRV_STS='1'`；姓名和人员编号按中心序号填写。

- [ ] **Step 4: 提交事务并加入校验查询**

脚本结尾写入：

```sql
COMMIT;

SELECT
  COUNT(*) AS totalCount,
  SUM(APRV_END_TM >= '2025-01-01' AND APRV_END_TM < '2026-01-01') AS baselineCount,
  SUM(APRV_END_TM >= '2026-01-01' AND APRV_END_TM < '2026-07-01') AS currentCount
FROM XN_API_DESIGN_FLOW_APPROVAL
WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '=';

SELECT CENTR_NM AS centerName, COUNT(*) AS recordCount
FROM XN_API_DESIGN_FLOW_APPROVAL
WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '='
GROUP BY CENTR_NM
ORDER BY FIELD(CENTR_NM,
  '开发一中心', '开发二中心', '开发三中心', '开发四中心', '开发五中心',
  '开发六中心', '开发七中心', '开发八中心', '开发九中心', '研发中心');

SELECT
  SUM(hours <= 24) AS within1Day,
  SUM(hours > 24 AND hours <= 168) AS within7Days,
  SUM(hours > 168) AS over7Days
FROM (
  SELECT TIMESTAMPDIFF(HOUR, APRV_BGN_TM, APRV_END_TM) AS hours
  FROM XN_API_DESIGN_FLOW_APPROVAL
  WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '='
    AND APRV_END_TM >= '2026-01-01'
    AND APRV_END_TM < '2026-07-01'
) d;

SELECT COUNT(*) AS testPersonCount
FROM xn_grp
WHERE empe_id LIKE 'TEST=_%' ESCAPE '=';
```

- [ ] **Step 5: 静态检查脚本**

Run:

```powershell
rg -n "TRUNCATE|DROP TABLE|DELETE FROM|START TRANSACTION|COMMIT|TEST=_" database/mysql57/seed-test-data.sql
```

Expected: 不包含 `TRUNCATE` 或 `DROP TABLE`；只有两个受 `TEST=_%` 条件限制的 `DELETE`；包含事务边界。

- [ ] **Step 6: 提交脚本**

```powershell
git add database/mysql57/seed-test-data.sql
git commit -m "test: add idempotent MySQL report fixtures"
```

### Task 2: 导入并验证 MySQL 5.7 数据

**Files:**
- Test: `database/mysql57/seed-test-data.sql`
- Test: `config/sql/*.sql`

- [ ] **Step 1: 检查持久 MySQL 5.7 容器**

Run:

```powershell
docker ps --filter "name=xnreport-mysql57" --format "{{.Names}} {{.Image}} {{.Status}} {{.Ports}}"
docker exec xnreport-mysql57 mysql -uxnreport -pxnreport -N -e "SELECT VERSION(), @@character_set_server, @@collation_server" xnreport
```

Expected: 容器为 `mysql:5.7.44` 且运行中，服务端字符集为 `utf8mb4`。

- [ ] **Step 2: 第一次导入**

Run:

```powershell
Get-Content -Raw -Encoding utf8 database/mysql57/seed-test-data.sql |
  docker exec -i xnreport-mysql57 mysql --default-character-set=utf8mb4 -uxnreport -pxnreport xnreport
```

Expected:

```text
totalCount baselineCount currentCount
40         10            30
...
within1Day within7Days over7Days
13         14          3
testPersonCount
10
```

- [ ] **Step 3: 第二次导入验证幂等性**

再次运行 Step 2 的相同命令。

Expected: 仍为 40 条审批测试记录和 10 条人员记录，无重复增长。

- [ ] **Step 4: 验证六个报表 SQL**

对 `config/sql/*.sql` 逐个执行。替换顺序必须先处理 `:baselineStartTime` 和 `:baselineEndTimeExclusive`，再处理 `:startTime` 和 `:endTimeExclusive`，防止部分参数名被提前替换。

```powershell
$centers = "'开发一中心','开发二中心','开发三中心','开发四中心','开发五中心','开发六中心','开发七中心','开发八中心','开发九中心','研发中心'"
Get-ChildItem config/sql/*.sql | Sort-Object Name | ForEach-Object {
  $query = Get-Content -Raw -Encoding utf8 $_.FullName
  $query = $query.Replace(':baselineStartTime', "'2025-01-01 00:00:00'")
  $query = $query.Replace(':baselineEndTimeExclusive', "'2026-01-01 00:00:00'")
  $query = $query.Replace(':startTime', "'2026-01-01 00:00:00'")
  $query = $query.Replace(':endTimeExclusive', "'2026-07-01 00:00:00'")
  $query = $query.Replace(':centerNames', $centers)
  Write-Output "SQL_FILE=$($_.Name)"
  $query | docker exec -i xnreport-mysql57 mysql --default-character-set=utf8mb4 -uxnreport -pxnreport -t xnreport
}
```

Expected:

- 6 个文件均执行成功；
- `department-monthly.sql` 返回 6 个月；
- `center-annual.sql` 返回 10 个中心；
- `center-monthly.sql` 返回 30 行并同时出现 `overStandardCount=1` 和 `withinStandardCount=1`；
- `person-annual.sql` 返回 10 人，既有高于基准也有低于基准；
- `person-monthly.sql` 返回 30 行；
- `duration-distribution.sql` 返回 `13、14、3`。

### Task 3: 更新使用手册并完成回归检查

**Files:**
- Modify: `docs/使用手册.md`

- [ ] **Step 1: 增加“导入示例测试数据”小节**

在“建表”之后增加：

```markdown
### 3.3 导入示例测试数据

项目提供 `database/mysql57/seed-test-data.sql`。脚本只清理和插入 `TEST_` 前缀的数据，可重复执行：

```powershell
Get-Content -Raw -Encoding utf8 database/mysql57/seed-test-data.sql |
  docker exec -i xnreport-mysql57 mysql --default-character-set=utf8mb4 -uxnreport -pxnreport xnreport
```

成功后应有 10 条基准审批记录、30 条当期审批记录和 10 条测试人员记录。脚本不会删除非 `TEST_` 数据。
```

- [ ] **Step 2: 检查文档、SQL 和工作区**

Run:

```powershell
git diff --check
rg -n "seed-test-data|40|30|10|TEST_" database/mysql57/seed-test-data.sql docs/使用手册.md
git status --short
```

Expected: `git diff --check` 无输出；手册能定位脚本和预期数量；工作区只包含本任务预期修改。

- [ ] **Step 3: 提交文档**

```powershell
git add docs/使用手册.md
git commit -m "docs: explain MySQL test data import"
```

- [ ] **Step 4: 最终只读验证**

Run:

```powershell
docker exec xnreport-mysql57 mysql --default-character-set=utf8mb4 -uxnreport -pxnreport -N -e "SELECT COUNT(*) FROM XN_API_DESIGN_FLOW_APPROVAL WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '='; SELECT COUNT(*) FROM xn_grp WHERE empe_id LIKE 'TEST=_%' ESCAPE '=';" xnreport
git status --short
```

Expected: 依次输出 `40`、`10`，Git 工作区干净。

