package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel 图表生成与写入总调度门面。
 * <p>
 * 在 Excel 数据集明细表格物化完成后执行：
 * <ul>
 *   <li>调用 {@link ChartModelBuilder} 基于数据集结果派生各分组图表模型。</li>
 *   <li>调用 {@link ExcelChartDataAreaWriter} 在数据表右侧物化图表独立数据区域。</li>
 *   <li>依据图表模式分发：
 *     <ul>
 *       <li>{@code GENERATED_NATIVE}：调用 {@link GeneratedNativeChartWriter} 动态生成原生 Excel OOXML 图表。</li>
 *       <li>{@code TEMPLATE_NATIVE}：调用 {@link TemplateNativeChartUpdater} 准确定位模板预置图表并重绑定公式缓存与数据序列。</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 */
public final class ExcelChartWriter {

    private final ChartModelBuilder modelBuilder;
    private final ChartRangeResolver rangeResolver;
    private final GeneratedNativeChartWriter generatedWriter;
    private final TemplateNativeChartUpdater templateUpdater;
    private final ExcelChartDataAreaWriter dataAreaWriter;
    private final ChartSourceCategoryIndex.Factory
            categoryIndexFactory;

    public ExcelChartWriter() {
        this(new ChartModelBuilder(), new ChartRangeResolver(),
                new GeneratedNativeChartWriter(),
                new TemplateNativeChartUpdater(),
                new ExcelChartDataAreaWriter(),
                ChartSourceCategoryIndex.factory());
    }

    public ExcelChartWriter(
            ChartModelBuilder modelBuilder,
            ChartRangeResolver rangeResolver,
            GeneratedNativeChartWriter generatedWriter,
            TemplateNativeChartUpdater templateUpdater) {
        this(modelBuilder, rangeResolver, generatedWriter,
                templateUpdater, new ExcelChartDataAreaWriter(),
                ChartSourceCategoryIndex.factory());
    }

    public ExcelChartWriter(
            ChartModelBuilder modelBuilder,
            ChartRangeResolver rangeResolver,
            GeneratedNativeChartWriter generatedWriter,
            TemplateNativeChartUpdater templateUpdater,
            ExcelChartDataAreaWriter dataAreaWriter) {
        this(modelBuilder, rangeResolver, generatedWriter,
                templateUpdater, dataAreaWriter,
                ChartSourceCategoryIndex.factory());
    }

    ExcelChartWriter(
            ChartModelBuilder modelBuilder,
            ChartRangeResolver rangeResolver,
            GeneratedNativeChartWriter generatedWriter,
            TemplateNativeChartUpdater templateUpdater,
            ExcelChartDataAreaWriter dataAreaWriter,
            ChartSourceCategoryIndex.Factory categoryIndexFactory) {
        this.modelBuilder = require(modelBuilder, "modelBuilder");
        this.rangeResolver = require(rangeResolver, "rangeResolver");
        this.generatedWriter = require(
                generatedWriter, "generatedWriter");
        this.templateUpdater = require(
                templateUpdater, "templateUpdater");
        this.dataAreaWriter = require(
                dataAreaWriter, "dataAreaWriter");
        this.categoryIndexFactory = require(
                categoryIndexFactory, "categoryIndexFactory");
    }

    /**
     * 执行全部配置图表的物化生成与模板更新。
     *
     * @param workbook 工作簿
     * @param charts 图表定义列表
     * @param datasets 数据集定义列表
     * @param context 数据集查询上下文
     * @param tableBindings 表格绑定关系
     */
    public void write(
            XSSFWorkbook workbook,
            List<ChartDefinition> charts,
            List<DatasetDefinition> datasets,
            DatasetContext context,
            Map<String, ExcelTableBinding> tableBindings) {
        if (workbook == null || charts == null
                || datasets == null || context == null) {
            throw new IllegalArgumentException(
                    "workbook, charts, datasets and context must not be null");
        }
        Map<String, DatasetDefinition> byId =
                new LinkedHashMap<String, DatasetDefinition>();
        for (DatasetDefinition dataset : datasets) {
            if (dataset != null) {
                byId.put(dataset.getId(), dataset);
            }
        }
        Map<String, ExcelTableBinding> bindings =
                tableBindings == null
                        ? Collections.<String, ExcelTableBinding>emptyMap()
                        : tableBindings;
        for (ChartDefinition chart : charts) {
            if (chart == null) {
                throw new IllegalArgumentException(
                        "Chart definition must not be null");
            }
            DatasetDefinition dataset = byId.get(chart.getDataset());
            if (dataset == null || !context.contains(chart.getDataset())) {
                throw new IllegalArgumentException(
                        "Missing chart dataset: " + chart.getDataset());
            }
            DatasetResult result = context.get(chart.getDataset());
            List<ChartModel> models =
                    modelBuilder.buildAll(chart, result);
            validateTemplateGroups(chart, models);
            if (chart.getMode()
                    == ChartDefinition.Mode.TEMPLATE_NATIVE) {
                templateUpdater.validateUniqueTargets(
                        workbook, chart);
            }
            ChartSourceCategoryIndex categoryIndex =
                    categoryIndexFactory.build(chart, result);
            for (int index = 0; index < models.size(); index++) {
                ChartModel model = models.get(index);
                ChartFormulaRange range = dataAreaWriter.write(
                        workbook, dataset, result, chart, model,
                        categoryIndex);
                if (chart.getMode()
                        == ChartDefinition.Mode.GENERATED_NATIVE) {
                    generatedWriter.write(
                            workbook, chart, model, range, index);
                } else if (chart.getMode()
                        == ChartDefinition.Mode.TEMPLATE_NATIVE) {
                    templateUpdater.update(
                            workbook, chart, model, range);
                } else {
                    throw new UnsupportedChartTypeException(
                            "Excel IMAGE chart mode is not an editable "
                                    + "native chart; configure GENERATED_NATIVE "
                                    + "or TEMPLATE_NATIVE");
                }
            }
        }
    }

    private static void validateTemplateGroups(
            ChartDefinition definition, List<ChartModel> models) {
        if (definition.getMode()
                != ChartDefinition.Mode.TEMPLATE_NATIVE
                || definition.getGroupByField() == null) {
            return;
        }
        Map<String, com.xn.report.config.definition
                .TemplateChartLocatorDefinition> byGroup =
                new LinkedHashMap<String, com.xn.report.config.definition
                        .TemplateChartLocatorDefinition>();
        for (com.xn.report.config.definition
                .TemplateChartLocatorDefinition locator
                : definition.getTemplateChartLocators()) {
            byGroup.put(locator.getGroupKey(), locator);
        }
        if (byGroup.size() != models.size()) {
            throw new IllegalArgumentException(
                    "Grouped template chart locator count "
                            + byGroup.size()
                            + " does not match model count "
                            + models.size());
        }
        for (ChartModel model : models) {
            if (!byGroup.containsKey(model.getGroupKey())) {
                throw new IllegalArgumentException(
                        "Missing template chart locator for group "
                                + model.getGroupKey());
            }
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    name + " must not be null");
        }
        return value;
    }
}
