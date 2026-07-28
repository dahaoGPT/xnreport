# Excel 原生图表绑定修正规格

## 目标

修正 Excel 原生图表的数据可追溯性、分组语义、系列顺序和模板缓存，使其与既有 `ChartModel` 契约一致。

## 同 Sheet 图表数据区

每个图表模型在所属 SQL 的可见数据 Sheet 上物化一块连续数据区。数据区从主明细和已有模板内容最右侧再空一列开始，第一行写 `图表数据：<chartId>[:<groupKey>]`，第二行依次写分类字段和按 `legendOrder` 排序后的系列字段，之后写模型中的分类、数值和气泡大小。不同分组横向排列并留一列间隔，不覆盖单元格、表格、合并区域或已有图表锚点。

图表公式只引用该数据区。由此完整支持 `groupByField`、`categorySort` 的 `SOURCE/ASC/DESC/EXPLICIT`、`SKIP_CATEGORY` 和 `legendOrder`，且用户选择 Excel 系列时能在同一可见 Sheet 看到对应数据。不得创建隐藏 Sheet 或关系 Sheet。

## 生成图表

`GENERATED_NATIVE` 为每个 `ChartModel` 创建一张原生图表，分组顺序采用 `ChartModelBuilder` 的稳定组键升序。图表标记为 `REPORT_CHART:<chartId>`；分组图追加 `:<groupKey>`。锚点按顺序向下错开，避免互相覆盖。

## 模板图表

无 `groupByField` 时继续支持单个 `templateChartMarker` 或 `templateChartIndex`。

有 `groupByField` 时配置 `templateChartLocators`，每项包含非空且唯一的 `groupKey`，并且恰好包含一个 `marker` 或 `index`。旧单 locator 与 locator 列表互斥。Schema 和配置校验在 SQL 执行前拒绝缺失、重复、空值、混用和非法索引；运行时按模型 `groupKey` 精确匹配，拒绝数据产生未声明组或声明组没有数据模型。

模板系列按 `ChartModel.series` 顺序绑定，而不是原始 YAML 顺序，确保 `legendOrder` 与 Word、生成式 Excel 一致。模板图表数量及每图系列数量必须匹配。

## OOXML 缓存

更新模板公式后显式重建每个系列的：

- 分类 `strCache` 或 `numCache`，数字和日期分类使用 `numCache`；
- 数值和气泡大小 `numCache`；
- 每个缓存的 `ptCount` 和按索引排列的 `pt`。

空数据写 `ptCount=0` 并移除所有旧 `pt`。更新仅修改系列公式、标题引用和缓存，不改变图表类型、布局、锚点或样式。

## 验证

工作簿写出后重新打开，校验图表公式均指向同 Sheet 数据区、缓存点数与公式范围一致、系列顺序等于 `legendOrder`、空缓存无旧点。精确回归覆盖 3 点扩展到 4 点、空数据、字符串类别、数字/日期类别、分组、四种排序、`SKIP_CATEGORY` 和多 locator。
