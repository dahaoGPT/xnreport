# Excel Chart Binding Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make editable Excel charts faithfully materialize and bind every `ChartModel`, including grouping, sorting, skipped categories, legend order, template locators, and OOXML caches.

**Architecture:** Add a focused same-sheet chart-data materializer returning `ChartFormulaRange` objects, then dispatch one generated or template chart per model. Extend template locator configuration for explicit group keys and rebuild OOXML caches from the materialized cells after formula rebinding.

**Tech Stack:** Java 8, Spring Boot 2.7, Apache POI 5.2.5, XMLBeans, JUnit 5, AssertJ.

---

### Task 1: Grouped template locator contract

**Files:**
- Create: `src/main/java/com/xn/report/config/definition/TemplateChartLocatorDefinition.java`
- Modify: `src/main/java/com/xn/report/config/definition/ChartDefinition.java`
- Modify: `src/main/java/com/xn/report/config/ReportDefinitionValidator.java`
- Modify: `src/main/resources/schema/report-definition.schema.json`
- Test: `src/test/java/com/xn/report/config/ExcelChartConfigurationTest.java`

- [ ] Write failing loader/schema/validator tests for grouped locator lists, duplicate group keys, mixed legacy/list locators, and exactly-one marker/index.
- [ ] Run `mvn -q -Dtest=ExcelChartConfigurationTest test` and confirm the new assertions fail.
- [ ] Add `templateChartLocators` with `{groupKey, marker|index}`, property-presence tracking, schema definition, and startup validation.
- [ ] Rerun the focused test and confirm it passes.

### Task 2: Same-sheet chart data areas

**Files:**
- Create: `src/main/java/com/xn/report/chart/ExcelChartDataAreaWriter.java`
- Modify: `src/main/java/com/xn/report/chart/ExcelChartWriter.java`
- Modify: `src/main/java/com/xn/report/chart/GeneratedNativeChartWriter.java`
- Test: `src/test/java/com/xn/report/excel/ExcelWorkbookWriterChartTest.java`

- [ ] Write failing tests that build grouped models with `ASC`, `DESC`, `EXPLICIT`, `SOURCE`, `SKIP_CATEGORY`, and reversed `legendOrder`, then reopen the workbook and assert one visible same-sheet data area and chart per group.
- [ ] Run the focused test and confirm the existing single-model rejection fails it.
- [ ] Materialize model categories/series in non-overlapping columns to the right of used content and return a direct `ChartFormulaRange` for each model.
- [ ] Dispatch every model, offset generated chart anchors deterministically, and bind formulas to each data area.
- [ ] Rerun the focused test and confirm it passes.

### Task 3: Template ordering, grouping, and cache rebuild

**Files:**
- Modify: `src/main/java/com/xn/report/chart/TemplateNativeChartUpdater.java`
- Modify: `src/main/java/com/xn/report/chart/ChartLocator.java`
- Test: `src/test/java/com/xn/report/chart/TemplateNativeChartUpdaterTest.java`

- [ ] Write failing reopen tests for 3-to-4 string points, numeric/date categories, empty data, `legendOrder`, and grouped locator-to-model matching.
- [ ] Run `mvn -q -Dtest=TemplateNativeChartUpdaterTest test` and confirm stale/misordered caches fail.
- [ ] Change updater input to `ChartModel`, reorder configured series by model field, safely plot supported charts, and explicitly rebuild `strCache`/`numCache`, `ptCount`, and indexed points from same-sheet cells.
- [ ] Ensure empty caches have zero count and no points while chart type/style/layout XML remains unchanged.
- [ ] Rerun the focused tests and confirm they pass.

### Task 4: Output validation and regression

**Files:**
- Modify: `src/main/java/com/xn/report/excel/ExcelOutputValidator.java`
- Test: `src/test/java/com/xn/report/excel/ExcelWorkbookWriterChartTest.java`
- Test: `src/test/java/com/xn/report/chart/GeneratedNativeChartWriterTest.java`

- [ ] Add failing validation tests for off-sheet formulas, wrong cache counts, stale empty points, and wrong legend order.
- [ ] Extend reopen validation to compare formulas, cache point counts, and series title order with the materialized ranges/models.
- [ ] Run `mvn -q test` and require zero failures/errors.
- [ ] Commit only Task 12 files with a focused fix message.
