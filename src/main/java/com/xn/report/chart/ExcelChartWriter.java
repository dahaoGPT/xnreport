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
 * Dispatches Excel chart definitions after dataset sheets and tables have
 * been materialized.
 */
public final class ExcelChartWriter {

    private final ChartModelBuilder modelBuilder;
    private final ChartRangeResolver rangeResolver;
    private final GeneratedNativeChartWriter generatedWriter;
    private final TemplateNativeChartUpdater templateUpdater;

    public ExcelChartWriter() {
        this(new ChartModelBuilder(), new ChartRangeResolver(),
                new GeneratedNativeChartWriter(),
                new TemplateNativeChartUpdater());
    }

    public ExcelChartWriter(
            ChartModelBuilder modelBuilder,
            ChartRangeResolver rangeResolver,
            GeneratedNativeChartWriter generatedWriter,
            TemplateNativeChartUpdater templateUpdater) {
        this.modelBuilder = require(modelBuilder, "modelBuilder");
        this.rangeResolver = require(rangeResolver, "rangeResolver");
        this.generatedWriter = require(
                generatedWriter, "generatedWriter");
        this.templateUpdater = require(
                templateUpdater, "templateUpdater");
    }

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
            if (models.size() != 1) {
                throw new IllegalArgumentException(
                        "Excel native chart binding currently requires "
                                + "one chart model per definition; chart "
                                + chart.getId() + " produced "
                                + models.size());
            }
            ChartFormulaRange range = rangeResolver.resolve(
                    workbook, dataset, result,
                    bindings.get(dataset.getId()), chart);
            validateDirectRangeAlignment(
                    chart, result, models.get(0), range);
            if (chart.getMode()
                    == ChartDefinition.Mode.GENERATED_NATIVE) {
                generatedWriter.write(
                        workbook, chart, models.get(0), range);
            } else if (chart.getMode()
                    == ChartDefinition.Mode.TEMPLATE_NATIVE) {
                templateUpdater.update(workbook, chart, range);
            } else {
                throw new UnsupportedChartTypeException(
                        "Excel IMAGE chart mode is not an editable "
                                + "native chart; configure GENERATED_NATIVE "
                                + "or TEMPLATE_NATIVE");
            }
        }
    }

    private static void validateDirectRangeAlignment(
            ChartDefinition definition,
            DatasetResult result,
            ChartModel model,
            ChartFormulaRange range) {
        if (result.type()
                != com.xn.report.dataset.DatasetType.LIST
                || result.list().size() != range.getPointCount()
                || model.getCategories().size()
                != range.getPointCount()) {
            throw new IllegalArgumentException(
                    "Excel chart " + definition.getId()
                            + " cannot directly reference the dataset "
                            + "sheet because grouping, aggregation or "
                            + "category skipping changed the point count");
        }
        for (int index = 0; index < result.list().size(); index++) {
            Object value = result.list().get(index)
                    .getOrNull(definition.getCategoryField());
            String label = value == null
                    ? "<null>" : String.valueOf(value);
            if (!label.equals(model.getCategories().get(index))) {
                throw new IllegalArgumentException(
                        "Excel chart " + definition.getId()
                                + " category order differs from its "
                                + "visible dataset sheet at row "
                                + (range.getFirstDataRow() + index + 1)
                                + "; use SOURCE order or order the SQL "
                                + "to match the configured chart");
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
