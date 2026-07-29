package com.xn.report.config;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.TemplateChartLocatorDefinition;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.DistributionDefinition.BinDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.ConditionDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.config.definition.ValueReferenceDefinition;
import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.TransformDefinition;
import com.xn.report.config.definition.TransformOperator;
import com.xn.report.config.definition.TrendDefinition;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordCoverDefinition;
import com.xn.report.config.definition.WordDefinition;
import com.xn.report.config.definition.WordNumberingDefinition;
import com.xn.report.config.definition.WordNumberingLevelDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.config.definition.WordTableColumnDefinition;
import com.xn.report.config.definition.WordTableBinding;
import com.xn.report.config.definition.ExcelDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.config.definition.ExcelValueBinding;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.excel.ExcelSheetNameRules;
import com.xn.report.excel.ExcelTableNameRules;
import com.xn.report.dataset.DatasetType;
import com.xn.report.rule.RuleEngine;
import com.xn.report.text.FormatterRegistry;
import com.xn.report.text.PlaceholderParser;
import com.xn.report.text.PlaceholderParser.Part;
import com.xn.report.chart.ChartType;
import com.xn.report.chart.ChartAxis;
import com.xn.report.chart.ChartModelBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ReportDefinitionValidator {

    static final int MAX_SECTION_TITLE_UTF16_LENGTH = 255;

    private static final Pattern DATASET_ID =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");
    private static final Pattern MANUAL_SECTION_NUMBERING =
            Pattern.compile("^(?:[一二三四五六七八九十百]+[、.]"
                    + "|\\d+(?:\\.\\d+)*[.、]"
                    + "|[（(]\\d+[）)]|[①-⑳])\\s*");
    private static final Set<String> COMPONENT_TYPES =
            unmodifiableSet("SCENARIO", "KEY_FACTORS", "FIXED_TEXT", "RULE_TEXT",
                    "CHART", "TABLE", "UNIT", "ATTACHMENT");
    private static final Set<String> TEXT_COMPONENT_TYPES =
            unmodifiableSet("SCENARIO", "KEY_FACTORS", "FIXED_TEXT", "UNIT");
    private static final Set<String> EMPTY_STRATEGIES =
            unmodifiableSet("KEEP", "SHOW_EMPTY", "SKIP");
    private static final Set<String> WORD_NUMBER_FORMATS =
            unmodifiableSet("decimal", "lowerLetter", "upperLetter",
                    "lowerRoman", "upperRoman", "chineseCounting",
                    "decimalEnclosedCircle");
    private static final PlaceholderParser PLACEHOLDER_PARSER =
            new PlaceholderParser();
    private static final FormatterRegistry FORMATTERS =
            FormatterRegistry.defaults();

    public ValidationResult validate(ReportDefinition definition) {
        ValidationResult result = new ValidationResult();
        if (definition == null) {
            result.add("CFG-DEFINITION", "$", "Report definition is required");
            return result;
        }

        requireText(result, definition.getSchemaVersion(), "CFG-SCHEMA-VERSION",
                "$.schemaVersion", "schemaVersion is required");
        if (definition.getReport() == null
                || !hasText(definition.getReport().getCode())
                || !hasText(definition.getReport().getName())) {
            result.add("CFG-REPORT", "$.report",
                    "report with non-blank code and name is required");
        }

        List<DatasetDefinition> datasets = safeList(definition.getDatasets());
        if (datasets.isEmpty()) {
            result.add("CFG-DATASETS", "$.datasets", "At least one dataset is required");
        }

        Set<String> datasetIds = validateDatasets(datasets, result);
        validateDependencies(datasets, datasetIds, result);
        validateDependencyCycles(datasets, datasetIds, result);
        if (definition.isExcelExplicitNull()) {
            result.add("EXCEL-001", "$.excel",
                    "excel must not be null");
        }
        validateExcel(definition.getExcel(), datasets, datasetIds, result);
        if (definition.isRulesExplicitNull()) {
            result.add("RULE-001", "$.rules", "rules must not be null");
        }
        validateRules(
                definition.getRules(),
                datasets,
                datasetIds,
                definition.getParameters(),
                result);
        if (definition.isChartsExplicitNull()) {
            result.add("CHART-001", "$.charts", "charts must not be null");
        }
        Set<String> chartIds = validateCharts(
                definition.getCharts(), datasets, datasetIds, result);
        if (definition.isNarrativesExplicitNull()) {
            result.add("TEXT-001", "$.narratives",
                    "narratives must not be null");
        }
        Set<String> narrativeIds =
                validateNarratives(
                        definition.getNarratives(),
                        datasets,
                        datasetIds,
                        definition.getParameters(),
                        result);
        validateWord(
                definition.getWord(),
                definition.getReport() != null
                        && hasText(definition.getReport().getWordTemplate()),
                datasetIds,
                narrativeIds,
                chartIds,
                result);
        return result;
    }

    private Set<String> validateCharts(
            List<ChartDefinition> charts,
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            ValidationResult result) {
        Map<String, DatasetDefinition> datasetsById =
                new LinkedHashMap<String, DatasetDefinition>();
        for (DatasetDefinition dataset : datasets) {
            if (dataset != null && hasText(dataset.getId())) {
                datasetsById.put(dataset.getId(), dataset);
            }
        }
        Set<String> ids = new LinkedHashSet<String>();
        List<ChartDefinition> safeCharts = safeList(charts);
        for (int index = 0; index < safeCharts.size(); index++) {
            ChartDefinition chart = safeCharts.get(index);
            String path = "$.charts[" + index + "]";
            if (chart == null) {
                result.add("CHART-001", path, "Chart must not be null");
                continue;
            }
            if (!hasText(chart.getId())) {
                result.add("CHART-001", path + ".id", "Chart id is required");
            } else if (!ids.add(chart.getId())) {
                result.add("CHART-001", path + ".id",
                        "Duplicate chart id: " + chart.getId());
            }
            rejectExplicitNullChartProperties(chart, path, result);
            validateExcelChartBinding(chart, path, result);
            if (!hasText(chart.getDataset())
                    || !datasetIds.contains(chart.getDataset())) {
                result.add("CHART-001", path + ".dataset",
                        "Unknown chart dataset: " + chart.getDataset());
            }
            if (!hasText(chart.getCategoryField())) {
                result.add("CHART-001", path + ".categoryField",
                        "Chart categoryField is required");
            }
            if (chart.getWidthPixels() == null
                    || chart.getWidthPixels().intValue() <= 0
                    || chart.getWidthPixels().intValue() > 4000) {
                result.add("CHART-001", path + ".widthPixels",
                        "Chart widthPixels must be between 1 and 4000");
            }
            if (chart.getHeightPixels() == null
                    || chart.getHeightPixels().intValue() <= 0
                    || chart.getHeightPixels().intValue() > 2400) {
                result.add("CHART-001", path + ".heightPixels",
                        "Chart heightPixels must be between 1 and 2400");
            }
            if (chart.getDpi() == null || chart.getDpi().intValue() < 36
                    || chart.getDpi().intValue() > 600) {
                result.add("CHART-001", path + ".dpi",
                        "Chart dpi must be between 36 and 600");
            }
            DatasetDefinition dataset = datasetsById.get(chart.getDataset());
            boolean known = dataset != null
                    && dataset.getExpectedFields() != null
                    && !dataset.getExpectedFields().isEmpty();
            Set<String> fields = dataset == null
                    ? Collections.<String>emptySet()
                    : availableDatasetFields(dataset);
            validateChartField(chart.getCategoryField(), fields, known,
                    path + ".categoryField", result);
            if (hasText(chart.getGroupByField())) {
                validateChartField(chart.getGroupByField(), fields, known,
                        path + ".groupByField", result);
            }
            List<ChartSeriesDefinition> series = safeList(chart.getSeries());
            if (series.isEmpty()) {
                result.add("CHART-001", path + ".series",
                        "Chart requires at least one series");
            }
            if (series.size() > ChartModelBuilder.MAX_SERIES) {
                result.add("CHART-001", path + ".series",
                        "Chart exceeds MAX_SERIES="
                                + ChartModelBuilder.MAX_SERIES);
            }
            int configuredCategories = safeList(
                    chart.getCategories()).size();
            if (configuredCategories > ChartModelBuilder.MAX_CATEGORIES) {
                result.add("CHART-001", path + ".categories",
                        "Chart exceeds MAX_CATEGORIES="
                                + ChartModelBuilder.MAX_CATEGORIES);
            }
            if ((long) configuredCategories * series.size()
                    > ChartModelBuilder.MAX_POINTS) {
                result.add("CHART-001", path,
                        "Configured categories and series exceed MAX_POINTS="
                                + ChartModelBuilder.MAX_POINTS);
            }
            Set<Integer> legendOrders = new LinkedHashSet<Integer>();
            Map<String, ChartStackContract> stackContracts =
                    new LinkedHashMap<String, ChartStackContract>();
            for (int seriesIndex = 0;
                    seriesIndex < series.size(); seriesIndex++) {
                validateChartSeries(
                        chart, series.get(seriesIndex), fields, known,
                        path + ".series[" + seriesIndex + "]",
                        legendOrders, stackContracts, result);
            }
            validateStackSlots(series, path, result);
            if (containsChartType(series, ChartType.STOCK)
                    && chart.getMode()
                    != ChartDefinition.Mode.TEMPLATE_NATIVE) {
                result.add("CHART-001", path + ".mode",
                        "STOCK chart requires TEMPLATE_NATIVE mode");
            }
            validateAxisBounds(chart.getPrimaryAxisMin(),
                    chart.getPrimaryAxisMax(), path + ".primaryAxis", result);
            validateAxisBounds(chart.getSecondaryAxisMin(),
                    chart.getSecondaryAxisMax(), path + ".secondaryAxis", result);
            validatePercentAxisBounds(chart, path, result);
        }
        return ids;
    }

    private void validateStackSlots(
            List<ChartSeriesDefinition> series,
            String path, ValidationResult result) {
        Map<String, String> slots = new LinkedHashMap<String, String>();
        for (ChartSeriesDefinition item : series) {
            if (item == null || item.getType() == null
                    || renderSlot(item.getType()) == null) {
                continue;
            }
            ChartAxis axis = item.getAxis() == null
                    ? ChartAxis.PRIMARY : item.getAxis();
            String slot = renderSlot(item.getType()) + "|" + axis.name();
            String token = hasText(item.getStackGroup())
                    ? item.getStackGroup() : item.getType().name();
            String previous = slots.put(slot, token);
            if (previous != null
                    && !previous.equals(token)) {
                result.add("CHART-001", path + ".series",
                        "Chart stack slot " + renderSlot(item.getType())
                                + " on " + axis
                                + " axis has conflicting series groups "
                                + "or multiple stackGroup values");
            }
        }
    }

    private String renderSlot(ChartType type) {
        if (type == ChartType.COLUMN
                || type == ChartType.STACKED_COLUMN
                || type == ChartType.PERCENT_STACKED_COLUMN) {
            return "VERTICAL_COLUMN";
        }
        if (type == ChartType.BAR || type == ChartType.STACKED_BAR) {
            return "HORIZONTAL_BAR";
        }
        if (type == ChartType.AREA || type == ChartType.STACKED_AREA) {
            return "VERTICAL_AREA";
        }
        return null;
    }

    private void validatePercentAxisBounds(
            ChartDefinition chart, String path, ValidationResult result) {
        boolean primary = false;
        boolean secondary = false;
        boolean primaryOrdinary = false;
        boolean secondaryOrdinary = false;
        for (ChartSeriesDefinition series : safeList(chart.getSeries())) {
            if (series == null || series.getType() == null
                    || series.getType().isPieLike()
                    || series.getType() == ChartType.RADAR) {
                continue;
            }
            boolean percent = series.getType()
                    == ChartType.PERCENT_STACKED_COLUMN;
            if (series.getAxis() == ChartAxis.SECONDARY) {
                secondary |= percent;
                secondaryOrdinary |= !percent;
            } else {
                primary |= percent;
                primaryOrdinary |= !percent;
            }
        }
        if (primary && primaryOrdinary) {
            result.add("CHART-001", path + ".series",
                    "A percent axis cannot share PRIMARY with ordinary numeric series");
        }
        if (secondary && secondaryOrdinary) {
            result.add("CHART-001", path + ".series",
                    "A percent axis cannot share SECONDARY with ordinary numeric series");
        }
        if (primary) {
            validatePercentBound(chart.getPrimaryAxisMin(),
                    path + ".primaryAxisMin", result);
            validatePercentBound(chart.getPrimaryAxisMax(),
                    path + ".primaryAxisMax", result);
        }
        if (secondary) {
            validatePercentBound(chart.getSecondaryAxisMin(),
                    path + ".secondaryAxisMin", result);
            validatePercentBound(chart.getSecondaryAxisMax(),
                    path + ".secondaryAxisMax", result);
        }
    }

    private void validatePercentBound(
            BigDecimal value, String path, ValidationResult result) {
        if (value != null && (value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.ONE) > 0)) {
            result.add("CHART-001", path,
                    "Percent axis bounds use ratio units from 0 to 1");
        }
    }

    private void rejectExplicitNullChartProperties(
            ChartDefinition chart, String path, ValidationResult result) {
        for (String property : chart.getPresentProperties()) {
            Object value = chartProperty(chart, property);
            if (value == null) {
                result.add("CHART-001", path + "." + property,
                        property + " must not be null");
            }
        }
    }

    private void validateExcelChartBinding(
            ChartDefinition chart,
            String path,
            ValidationResult result) {
        boolean marker = chart.hasProperty("templateChartMarker")
                && hasText(chart.getTemplateChartMarker());
        boolean index = chart.hasProperty("templateChartIndex")
                && chart.getTemplateChartIndex() != null;
        boolean locatorListPresent =
                chart.hasProperty("templateChartLocators");
        List<TemplateChartLocatorDefinition> groupedLocators =
                safeList(chart.getTemplateChartLocators());
        boolean grouped = hasText(chart.getGroupByField());
        if (chart.getMode() == ChartDefinition.Mode.TEMPLATE_NATIVE) {
            if (!hasText(chart.getExcelSheet())) {
                result.add("CHART-002", path + ".excelSheet",
                        "TEMPLATE_NATIVE requires excelSheet");
            }
            if (grouped) {
                if (marker || index
                        || chart.hasProperty("templateChartMarker")
                        || chart.hasProperty("templateChartIndex")) {
                    result.add("CHART-002",
                            path + ".templateChartLocators",
                            "Grouped templateChartLocators must not be "
                                    + "combined with legacy template chart "
                                    + "locator properties");
                }
                validateGroupedTemplateLocators(
                        groupedLocators, path, result);
            } else if (locatorListPresent) {
                result.add("CHART-002",
                        path + ".templateChartLocators",
                        "templateChartLocators requires groupByField; "
                                + "use one legacy template chart locator");
            } else if (marker == index) {
                result.add("CHART-002", path + ".templateChartLocator",
                        "TEMPLATE_NATIVE requires exactly one "
                                + "templateChartMarker or templateChartIndex");
            }
            if (hasAnchorProperty(chart)) {
                result.add("CHART-002", path,
                        "Template chart layout comes from the template; "
                                + "anchor properties are not allowed");
            }
        } else if (marker || index || locatorListPresent
                || chart.hasProperty("templateChartMarker")
                || chart.hasProperty("templateChartIndex")) {
            result.add("CHART-001",
                    locatorListPresent
                            ? path + ".templateChartLocators"
                            : marker || chart.hasProperty("templateChartMarker")
                            ? path + ".templateChartMarker"
                            : path + ".templateChartIndex",
                    "Template chart locator is only valid for "
                            + "TEMPLATE_NATIVE mode");
        }
        validateNonNegative(chart.getTemplateChartIndex(),
                path + ".templateChartIndex", result, true);
        validateNonNegative(chart.getAnchorRow(),
                path + ".anchorRow", result, true);
        validateNonNegative(chart.getAnchorColumn(),
                path + ".anchorColumn", result, true);
        validateNonNegative(chart.getAnchorWidthColumns(),
                path + ".anchorWidthColumns", result, false);
        validateNonNegative(chart.getAnchorHeightRows(),
                path + ".anchorHeightRows", result, false);
    }

    private void validateGroupedTemplateLocators(
            List<TemplateChartLocatorDefinition> locators,
            String chartPath,
            ValidationResult result) {
        if (locators.isEmpty()) {
            result.add("CHART-002",
                    chartPath + ".templateChartLocators",
                    "Grouped TEMPLATE_NATIVE requires one locator "
                            + "for every declared groupKey");
            return;
        }
        Set<String> groups = new LinkedHashSet<String>();
        Set<String> markers = new LinkedHashSet<String>();
        Set<Integer> indexes = new LinkedHashSet<Integer>();
        for (int index = 0; index < locators.size(); index++) {
            String path = chartPath + ".templateChartLocators["
                    + index + "]";
            TemplateChartLocatorDefinition locator = locators.get(index);
            if (locator == null) {
                result.add("CHART-002", path,
                        "Template chart locator must not be null");
                continue;
            }
            if (!hasText(locator.getGroupKey())) {
                result.add("CHART-002", path + ".groupKey",
                        "groupKey must not be blank");
            } else if (!groups.add(locator.getGroupKey())) {
                result.add("CHART-002", path + ".groupKey",
                        "groupKey must be unique within "
                                + "templateChartLocators");
            }
            boolean marker = locator.hasProperty("marker")
                    && hasText(locator.getMarker());
            boolean chartIndex = locator.hasProperty("index")
                    && locator.getIndex() != null;
            if (marker == chartIndex) {
                result.add("CHART-002",
                        !marker && !chartIndex
                                ? path + ".marker" : path,
                        "Template chart locator requires exactly one "
                                + "marker or index");
            }
            if (marker && !markers.add(locator.getMarker())) {
                result.add("CHART-002", path + ".marker",
                        "marker must be unique within "
                                + "templateChartLocators");
            }
            if (chartIndex
                    && !indexes.add(locator.getIndex())) {
                result.add("CHART-002", path + ".index",
                        "index must be unique within "
                                + "templateChartLocators");
            }
            if (locator.getIndex() != null
                    && locator.getIndex().intValue() < 0) {
                result.add("CHART-002", path + ".index",
                        "Value must not be negative");
            }
            for (String property : locator.getPresentProperties()) {
                Object value = "groupKey".equals(property)
                        ? locator.getGroupKey()
                        : "marker".equals(property)
                        ? locator.getMarker() : locator.getIndex();
                if (value == null) {
                    result.add("CHART-002",
                            path + "." + property,
                            property + " must not be null");
                }
            }
        }
    }

    private boolean hasAnchorProperty(ChartDefinition chart) {
        return chart.hasProperty("anchorRow")
                || chart.hasProperty("anchorColumn")
                || chart.hasProperty("anchorWidthColumns")
                || chart.hasProperty("anchorHeightRows");
    }

    private void validateNonNegative(
            Integer value,
            String path,
            ValidationResult result,
            boolean zeroAllowed) {
        if (value != null && (zeroAllowed
                ? value.intValue() < 0 : value.intValue() <= 0)) {
            result.add("CHART-001", path,
                    zeroAllowed
                            ? "Value must not be negative"
                            : "Value must be positive");
        }
    }

    private Object chartProperty(ChartDefinition chart, String property) {
        if ("id".equals(property)) return chart.getId();
        if ("title".equals(property)) return chart.getTitle();
        if ("mode".equals(property)) return chart.getMode();
        if ("dataset".equals(property)) return chart.getDataset();
        if ("excelSheet".equals(property)) return chart.getExcelSheet();
        if ("excelTable".equals(property)) return chart.getExcelTable();
        if ("templateChartMarker".equals(property)) return chart.getTemplateChartMarker();
        if ("templateChartIndex".equals(property)) return chart.getTemplateChartIndex();
        if ("templateChartLocators".equals(property)) return chart.getTemplateChartLocators();
        if ("anchorRow".equals(property)) return chart.getAnchorRow();
        if ("anchorColumn".equals(property)) return chart.getAnchorColumn();
        if ("anchorWidthColumns".equals(property)) return chart.getAnchorWidthColumns();
        if ("anchorHeightRows".equals(property)) return chart.getAnchorHeightRows();
        if ("categoryField".equals(property)) return chart.getCategoryField();
        if ("groupByField".equals(property)) return chart.getGroupByField();
        if ("categories".equals(property)) return chart.getCategories();
        if ("categorySort".equals(property)) return chart.getCategorySort();
        if ("series".equals(property)) return chart.getSeries();
        if ("legendPosition".equals(property)) return chart.getLegendPosition();
        if ("primaryAxisMin".equals(property)) return chart.getPrimaryAxisMin();
        if ("primaryAxisMax".equals(property)) return chart.getPrimaryAxisMax();
        if ("secondaryAxisMin".equals(property)) return chart.getSecondaryAxisMin();
        if ("secondaryAxisMax".equals(property)) return chart.getSecondaryAxisMax();
        if ("dataLabelMode".equals(property)) return chart.getDataLabelMode();
        if ("widthPixels".equals(property)) return chart.getWidthPixels();
        if ("heightPixels".equals(property)) return chart.getHeightPixels();
        if ("dpi".equals(property)) return chart.getDpi();
        if ("emptyDataPolicy".equals(property)) return chart.getEmptyDataPolicy();
        if ("emptyMessage".equals(property)) return chart.getEmptyMessage();
        return null;
    }

    private void validateChartSeries(
            ChartDefinition chart,
            ChartSeriesDefinition series,
            Set<String> fields,
            boolean known,
            String path,
            Set<Integer> legendOrders,
            Map<String, ChartStackContract> stackContracts,
            ValidationResult result) {
        if (series == null) {
            result.add("CHART-001", path, "Chart series must not be null");
            return;
        }
        for (String property : series.getPresentProperties()) {
            if (chartSeriesProperty(series, property) == null) {
                result.add("CHART-001", path + "." + property,
                        property + " must not be null");
            }
        }
        if (!hasText(series.getField())) {
            result.add("CHART-001", path + ".field",
                    "Chart series field is required");
        } else {
            validateChartField(series.getField(), fields, known,
                    path + ".field", result);
        }
        if (!hasText(series.getName())) {
            result.add("CHART-001", path + ".name",
                    "Chart series name is required");
        }
        ChartType type = series.getType();
        if (type == null) {
            result.add("CHART-001", path + ".type",
                    "Chart series type is required");
            return;
        }
        if (type.isStacked() && !hasText(series.getStackGroup())) {
            result.add("CHART-001", path + ".stackGroup",
                    "Stacked chart series requires stackGroup");
        }
        if (!type.isStacked() && series.hasProperty("stackGroup")) {
            result.add("CHART-001", path + ".stackGroup",
                    "stackGroup is only valid for stacked chart series");
        }
        if (type == ChartType.BUBBLE) {
            if (!hasText(series.getSizeField())) {
                result.add("CHART-001", path + ".sizeField",
                        "BUBBLE series requires sizeField");
            } else {
                validateChartField(series.getSizeField(), fields, known,
                        path + ".sizeField", result);
            }
        } else if (series.hasProperty("sizeField")) {
            result.add("CHART-001", path + ".sizeField",
                    "sizeField is only valid for BUBBLE series");
        }
        if (series.getLineWidth() == null
                || series.getLineWidth().compareTo(BigDecimal.ZERO) <= 0) {
            result.add("CHART-001", path + ".lineWidth",
                    "lineWidth must be positive");
        }
        if (series.getLegendOrder() != null
                && !legendOrders.add(series.getLegendOrder())) {
            result.add("CHART-001", path + ".legendOrder",
                    "legendOrder must be unique within a chart");
        }
        if (hasText(series.getColor())
                && !series.getColor().matches("^#?[0-9A-Fa-f]{6}$")) {
            result.add("CHART-001", path + ".color",
                    "Chart color must be a six-digit RGB hex value");
        }
        validateChartSeriesPropertyMatrix(chart, series, path, result);
        if (hasText(series.getStackGroup())) {
            ChartStackContract contract =
                    stackContracts.get(series.getStackGroup());
            if (contract == null) {
                stackContracts.put(series.getStackGroup(),
                        new ChartStackContract(type, series.getAxis()));
            } else {
                if (contract.type != type) {
                    result.add("CHART-001", path + ".stackGroup",
                            "All series in stackGroup "
                                    + series.getStackGroup()
                                    + " must use the same type");
                }
                if (contract.axis != series.getAxis()) {
                    result.add("CHART-001", path + ".axis",
                            "All series in stackGroup "
                                    + series.getStackGroup()
                                    + " must use the same axis");
                }
            }
        }
    }

    private void validateChartSeriesPropertyMatrix(
            ChartDefinition chart,
            ChartSeriesDefinition series,
            String path,
            ValidationResult result) {
        ChartType type = series.getType();
        if ((type == ChartType.SCATTER || type == ChartType.BUBBLE)
                && (series.hasProperty("lineStyle")
                || series.hasProperty("lineWidth"))) {
            result.add("CHART-001", path,
                    type + " does not support lineStyle or lineWidth");
        }
        if ((series.hasProperty("lineStyle")
                || series.hasProperty("lineWidth"))
                && type != ChartType.LINE
                && type != ChartType.AREA
                && type != ChartType.STACKED_AREA
                && type != ChartType.RADAR
                && type != ChartType.STOCK) {
            result.add("CHART-001", path,
                    type + " does not support lineStyle or lineWidth");
        }
        if (type == ChartType.BUBBLE && series.hasProperty("marker")) {
            result.add("CHART-001", path + ".marker",
                    "BUBBLE does not support marker");
        }
        if (type == ChartType.RADAR
                && (series.hasProperty("marker")
                || series.hasProperty("dataLabels")
                || series.hasProperty("format")
                || series.hasProperty("axis"))) {
            result.add("CHART-001", path,
                    "RADAR does not support marker, dataLabels, format, or axis");
        }
        if (series.hasProperty("marker")
                && type != ChartType.LINE
                && type != ChartType.SCATTER
                && type != ChartType.STOCK) {
            result.add("CHART-001", path + ".marker",
                    type + " does not support marker");
        }
        if (type == ChartType.SCATTER
                && series.hasProperty("marker")
                && !series.isMarker()) {
            result.add("CHART-001", path + ".marker",
                    "SCATTER requires a visible marker");
        }
        if (series.hasProperty("format")
                && (series.hasProperty("dataLabels")
                ? series.getDataLabels()
                == com.xn.report.chart.ChartDataLabelMode.NONE
                : chart.getDataLabelMode()
                == com.xn.report.chart.ChartDataLabelMode.NONE)) {
            result.add("CHART-001", path + ".format",
                    "format requires visible dataLabels");
        }
        if (type.isPieLike() && series.hasProperty("format")) {
            result.add("CHART-001", path + ".format",
                    type + " does not support series format");
        }
        if (!type.isPieLike()
                && series.hasProperty("dataLabels")
                && series.getDataLabels()
                        != com.xn.report.chart.ChartDataLabelMode.NONE
                && series.getDataLabels()
                        != com.xn.report.chart.ChartDataLabelMode.VALUE) {
            result.add("CHART-001", path + ".dataLabels",
                    type + " supports only VALUE dataLabels");
        }
        if ((type.isPieLike() || type == ChartType.RADAR)
                && series.hasProperty("axis")) {
            result.add("CHART-001", path + ".axis",
                    type + " does not support an axis selection");
        }
    }

    private boolean containsChartType(
            List<ChartSeriesDefinition> series, ChartType type) {
        for (ChartSeriesDefinition item : series) {
            if (item != null && item.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private Object chartSeriesProperty(
            ChartSeriesDefinition series, String property) {
        if ("field".equals(property)) return series.getField();
        if ("name".equals(property)) return series.getName();
        if ("type".equals(property)) return series.getType();
        if ("axis".equals(property)) return series.getAxis();
        if ("stackGroup".equals(property)) return series.getStackGroup();
        if ("color".equals(property)) return series.getColor();
        if ("lineStyle".equals(property)) return series.getLineStyle();
        if ("lineWidth".equals(property)) return series.getLineWidth();
        if ("marker".equals(property)) return series.getMarker();
        if ("dataLabels".equals(property)) return series.getDataLabels();
        if ("format".equals(property)) return series.getFormat();
        if ("nullHandling".equals(property)) return series.getNullHandling();
        if ("legendOrder".equals(property)) return series.getLegendOrder();
        if ("sizeField".equals(property)) return series.getSizeField();
        return null;
    }

    private void validateChartField(
            String field,
            Set<String> fields,
            boolean known,
            String path,
            ValidationResult result) {
        if (known && hasText(field) && !containsField(fields, field)) {
            result.add("CHART-001", path,
                    "Unknown chart field " + field);
        }
    }

    private void validateAxisBounds(
            BigDecimal minimum,
            BigDecimal maximum,
            String path,
            ValidationResult result) {
        if (minimum != null && maximum != null
                && minimum.compareTo(maximum) >= 0) {
            result.add("CHART-001", path,
                    "Chart axis minimum must be less than maximum");
        }
    }

    private void validateRules(
            List<RuleDefinition> rules,
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            Map<String, ParameterDefinition> parameters,
            ValidationResult result) {
        Map<String, DatasetType> datasetTypes =
                new LinkedHashMap<String, DatasetType>();
        Map<String, Set<String>> datasetFields =
                new LinkedHashMap<String, Set<String>>();
        Set<String> knownFieldContracts = new LinkedHashSet<String>();
        for (DatasetDefinition dataset : datasets) {
            if (dataset != null && hasText(dataset.getId())) {
                datasetTypes.put(dataset.getId(), dataset.getResultType());
                datasetFields.put(
                        dataset.getId(), availableDatasetFields(dataset));
                if (dataset.getExpectedFields() != null
                        && !dataset.getExpectedFields().isEmpty()) {
                    knownFieldContracts.add(dataset.getId());
                }
            }
        }
        Set<String> ids = new LinkedHashSet<String>();
        List<RuleDefinition> safeRules = safeList(rules);
        for (int index = 0; index < safeRules.size(); index++) {
            RuleDefinition rule = safeRules.get(index);
            String path = "$.rules[" + index + "]";
            if (rule == null) {
                result.add("RULE-001", path, "Rule must not be null");
                continue;
            }
            if (!hasText(rule.getId())) {
                result.add("RULE-001", path + ".id", "Rule id is required");
            } else if (!ids.add(rule.getId())) {
                result.add("RULE-001", path + ".id",
                        "Duplicate rule id: " + rule.getId());
            }
            if (!hasText(rule.getDataset())
                    || !datasetIds.contains(rule.getDataset())) {
                result.add("RULE-001", path + ".dataset",
                        "Unknown rule dataset: " + rule.getDataset());
            } else if (datasetTypes.get(rule.getDataset()) != DatasetType.LIST) {
                result.add("RULE-001", path + ".dataset",
                        "Rule input dataset must be LIST");
            }
            try {
                new RuleEngine().compile(rule.getCondition());
            } catch (RuntimeException exception) {
                result.add("RULE-001", path + ".condition",
                        exception.getMessage() == null
                                ? "Invalid rule condition" : exception.getMessage());
            }
            validateConditionReferences(
                    rule.getCondition(),
                    rule.getDataset(),
                    datasetTypes,
                    datasetFields,
                    knownFieldContracts,
                    parameters == null
                            ? Collections.<String, ParameterDefinition>emptyMap()
                            : parameters,
                    path + ".condition",
                    result);
            validateRuleResult(
                    rule.getResult(),
                    datasetFields.get(rule.getDataset()),
                    knownFieldContracts.contains(rule.getDataset()),
                    path + ".result",
                    result);
        }
    }

    private void validateConditionReferences(
            ConditionDefinition condition,
            String currentDataset,
            Map<String, DatasetType> datasetTypes,
            Map<String, Set<String>> datasetFields,
            Set<String> knownFieldContracts,
            Map<String, ParameterDefinition> parameters,
            String path,
            ValidationResult result) {
        if (condition == null) {
            return;
        }
        validateValueReference(
                condition.getLeft(),
                currentDataset,
                datasetTypes,
                datasetFields,
                knownFieldContracts,
                parameters,
                path + ".left",
                result);
        validateValueReference(
                condition.getRight(),
                currentDataset,
                datasetTypes,
                datasetFields,
                knownFieldContracts,
                parameters,
                path + ".right",
                result);
        List<ConditionDefinition> children = condition.getChildren();
        if (children != null) {
            for (int index = 0; index < children.size(); index++) {
                validateConditionReferences(
                        children.get(index),
                        currentDataset,
                        datasetTypes,
                        datasetFields,
                        knownFieldContracts,
                        parameters,
                        path + ".children[" + index + "]", result);
            }
        }
    }

    private Object ruleResultProperty(
            RuleDefinition.ResultDefinition definition, String property) {
        switch (property) {
            case "distinctFields":
                return definition.getDistinctFields();
            case "sort":
                return definition.getSort();
            case "groupByFields":
                return definition.getGroupByFields();
            case "maxItems":
                return definition.getMaxItems();
            case "summaries":
                return definition.getSummaries();
            default:
                return null;
        }
    }

    private void validateValueReference(
            ValueReferenceDefinition reference,
            String currentDataset,
            Map<String, DatasetType> datasetTypes,
            Map<String, Set<String>> datasetFields,
            Set<String> knownFieldContracts,
            Map<String, ParameterDefinition> parameters,
            String path,
            ValidationResult result) {
        if (reference == null || reference.getSource() == null) {
            return;
        }
        switch (reference.getSource()) {
            case CURRENT_FIELD:
                if (hasText(currentDataset)
                        && hasText(reference.getField())
                        && knownFieldContracts.contains(currentDataset)
                        && !containsField(
                                datasetFields.get(currentDataset),
                                reference.getField())) {
                    result.add("RULE-001", path + ".field",
                            "Unknown CURRENT_FIELD field "
                                    + reference.getField()
                                    + " in dataset " + currentDataset);
                }
                break;
            case DATASET_FIELD:
                DatasetType type = datasetTypes.get(reference.getDataset());
                if (type == null) {
                    result.add("RULE-001", path + ".dataset",
                            "Unknown referenced dataset: "
                                    + reference.getDataset());
                } else if (type != DatasetType.SCALAR
                        && type != DatasetType.SINGLE) {
                    result.add("RULE-001", path + ".dataset",
                            "DATASET_FIELD requires SCALAR or SINGLE dataset");
                } else if (hasText(reference.getField())
                        && knownFieldContracts.contains(reference.getDataset())
                        && !containsField(
                                datasetFields.get(reference.getDataset()),
                                reference.getField())) {
                    result.add("RULE-001", path + ".field",
                            "Unknown DATASET_FIELD field "
                                    + reference.getField()
                                    + " in dataset " + reference.getDataset());
                }
                break;
            case RUNTIME_PARAMETER:
                if (hasText(reference.getParameter())
                        && !parameters.containsKey(reference.getParameter())) {
                    result.add("RULE-001", path + ".parameter",
                            "Unknown RUNTIME_PARAMETER "
                                    + reference.getParameter());
                }
                break;
            case LITERAL:
                break;
            default:
                result.add("RULE-001", path + ".source",
                        "Unsupported value reference source");
        }
    }

    private Set<String> availableDatasetFields(DatasetDefinition dataset) {
        Set<String> fields = new LinkedHashSet<String>();
        if (dataset.getExpectedFields() != null) {
            fields.addAll(dataset.getExpectedFields().keySet());
        }
        for (TransformDefinition transform : safeList(dataset.getTransforms())) {
            if (transform != null
                    && transform.getType()
                            == com.xn.report.config.definition.TransformType.DERIVED_FIELD
                    && hasText(transform.getTargetField())) {
                fields.add(transform.getTargetField());
            }
        }
        return fields;
    }

    private boolean containsField(Set<String> fields, String requested) {
        if (fields == null) {
            return false;
        }
        for (String field : fields) {
            if (field != null && field.equalsIgnoreCase(requested)) {
                return true;
            }
        }
        return false;
    }

    private void validateRuleResult(
            RuleDefinition.ResultDefinition definition,
            Set<String> availableFields,
            boolean knownFieldContract,
            String path,
            ValidationResult result) {
        if (definition == null) {
            result.add("RULE-001", path, "Rule result must not be null");
            return;
        }
        for (String property : definition.getPresentProperties()) {
            if (ruleResultProperty(definition, property) == null) {
                result.add("RULE-001", path + "." + property,
                        property + " must not be null");
            }
        }
        if (definition.getMaxItems() != null
                && definition.getMaxItems().intValue() < 0) {
            result.add("RULE-001", path + ".maxItems",
                    "maxItems must be non-negative");
        }
        validateTextList(
                definition.getDistinctFields(), path + ".distinctFields", result);
        validateKnownFields(
                definition.getDistinctFields(),
                availableFields,
                knownFieldContract,
                path + ".distinctFields",
                result);
        validateTextList(
                definition.getGroupByFields(), path + ".groupByFields", result);
        validateKnownFields(
                definition.getGroupByFields(),
                availableFields,
                knownFieldContract,
                path + ".groupByFields",
                result);
        List<SortFieldDefinition> sorts = safeList(definition.getSort());
        for (int index = 0; index < sorts.size(); index++) {
            SortFieldDefinition sort = sorts.get(index);
            String sortPath = path + ".sort[" + index + "]";
            if (sort == null || !hasText(sort.getField())
                    || sort.getDirection() == null || sort.getNullOrder() == null) {
                result.add("RULE-001", sortPath,
                        "Rule sort requires field, direction and nullOrder");
            } else if (knownFieldContract
                    && !containsField(availableFields, sort.getField())) {
                result.add("RULE-001", sortPath + ".field",
                        "Unknown rule result field " + sort.getField());
            }
        }
        List<RuleDefinition.SummaryDefinition> summaries =
                safeList(definition.getSummaries());
        for (int index = 0; index < summaries.size(); index++) {
            RuleDefinition.SummaryDefinition summary = summaries.get(index);
            String summaryPath = path + ".summaries[" + index + "]";
            if (summary == null || !hasText(summary.getName())
                    || !hasText(summary.getField())
                    || summary.getOperation() == null) {
                result.add("RULE-001", summaryPath,
                        "Rule summary requires name, field and operation");
            } else if (knownFieldContract
                    && !containsField(availableFields, summary.getField())) {
                result.add("RULE-001", summaryPath + ".field",
                        "Unknown rule result field " + summary.getField());
            }
        }
    }

    private void validateKnownFields(
            List<String> fields,
            Set<String> availableFields,
            boolean knownFieldContract,
            String path,
            ValidationResult result) {
        if (!knownFieldContract) {
            return;
        }
        List<String> safeFields = safeList(fields);
        for (int index = 0; index < safeFields.size(); index++) {
            String field = safeFields.get(index);
            if (hasText(field) && !containsField(availableFields, field)) {
                result.add("RULE-001", path + "[" + index + "]",
                        "Unknown rule result field " + field);
            }
        }
    }

    private void validateTextList(
            List<String> values, String path, ValidationResult result) {
        List<String> safeValues = safeList(values);
        Set<String> seen = new LinkedHashSet<String>();
        for (int index = 0; index < safeValues.size(); index++) {
            String value = safeValues.get(index);
            if (!hasText(value)
                    || !seen.add(value.toLowerCase(Locale.ROOT))) {
                result.add("RULE-001", path + "[" + index + "]",
                        "Rule fields must be non-blank and unique");
            }
        }
    }

    private Set<String> validateDatasets(
            List<DatasetDefinition> datasets, ValidationResult result) {
        Set<String> ids = new LinkedHashSet<String>();
        List<String> sheetNames = new ArrayList<String>();
        for (int index = 0; index < datasets.size(); index++) {
            DatasetDefinition dataset = datasets.get(index);
            String path = "$.datasets[" + index + "]";
            if (dataset == null) {
                result.add("CFG-DATASET", path, "Dataset must not be null");
                continue;
            }

            String id = dataset.getId();
            if (!hasText(id) || !DATASET_ID.matcher(id).matches()) {
                result.add("CFG-DATASET-ID", path + ".id",
                        "Dataset id must match " + DATASET_ID.pattern());
            } else if (!ids.add(id)) {
                result.add("CFG-DUPLICATE-DATASET", path + ".id",
                        "Duplicate dataset id: " + id);
            }

            boolean hasSqlFile = hasText(dataset.getSqlFile());
            boolean hasSql = hasText(dataset.getSql());
            if (hasSqlFile == hasSql) {
                result.add("CFG-SQL-SOURCE", path,
                        "Exactly one of sqlFile and sql must be configured");
            }
            validateSheetName(dataset.getSheetName(), path + ".sheetName",
                    sheetNames, result);
            validateExpectedFields(dataset, path, result);
            validateTransforms(dataset, path, result);
        }
        return ids;
    }

    private void validateExpectedFields(
            DatasetDefinition dataset,
            String datasetPath,
            ValidationResult result) {
        Map<String, FieldDefinition> expected =
                dataset.getExpectedFields();
        if (expected == null) {
            return;
        }
        int maxColumns = org.apache.poi.ss.SpreadsheetVersion
                .EXCEL2007.getMaxColumns();
        if (expected.size() > maxColumns) {
            result.add(
                    "EXCEL-001",
                    datasetPath + ".expectedFields",
                    "Expected fields exceed XLSX column limit "
                            + maxColumns);
        }
        Set<String> normalized =
                new LinkedHashSet<String>();
        for (String field : expected.keySet()) {
            if (!hasText(field)) {
                continue;
            }
            if (!normalized.add(
                    field.toLowerCase(Locale.ROOT))) {
                result.add(
                        "EXCEL-001",
                        datasetPath + ".expectedFields." + field,
                        "Duplicate expected field ignoring case: "
                                + field);
            }
        }
    }

    private void validateExcel(
            ExcelDefinition excel,
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            ValidationResult result) {
        if (excel == null) {
            result.add("EXCEL-001", "$.excel",
                    "excel must not be null");
            return;
        }
        Map<String, DatasetDefinition> datasetsById =
                new LinkedHashMap<String, DatasetDefinition>();
        for (DatasetDefinition dataset : datasets) {
            if (dataset != null && hasText(dataset.getId())) {
                datasetsById.put(dataset.getId(), dataset);
            }
        }
        if (excel.isValueBindingsExplicitNull()) {
            result.add("EXCEL-001", "$.excel.valueBindings",
                    "valueBindings must not be null");
        }
        for (int index = 0;
                index < safeList(excel.getValueBindings()).size();
                index++) {
            ExcelValueBinding binding =
                    safeList(excel.getValueBindings()).get(index);
            String path = "$.excel.valueBindings[" + index + "]";
            if (binding == null) {
                result.add("EXCEL-001", path,
                        "Excel value binding must not be null");
                continue;
            }
            requireText(result, binding.getSheet(), "EXCEL-001",
                    path + ".sheet", "Value binding sheet is required");
            requireText(result, binding.getValue(), "EXCEL-001",
                    path + ".value", "Value binding value is required");
            validateCellReference(binding.getCell(),
                    path + ".cell", result);
            if (binding.isFormatPresent()
                    && !hasText(binding.getFormat())) {
                result.add("EXCEL-001", path + ".format",
                        "Value format must be non-blank when configured");
            }
        }

        if (excel.isTableBindingsExplicitNull()) {
            result.add("EXCEL-001", "$.excel.tableBindings",
                    "tableBindings must not be null");
        }
        Set<String> boundDatasets = new LinkedHashSet<String>();
        Set<String> tableNames = new LinkedHashSet<String>();
        List<ExcelTableBinding> tableBindings =
                safeList(excel.getTableBindings());
        for (int index = 0; index < tableBindings.size(); index++) {
            ExcelTableBinding binding = tableBindings.get(index);
            String path = "$.excel.tableBindings[" + index + "]";
            if (binding == null) {
                result.add("EXCEL-001", path,
                        "Excel table binding must not be null");
                continue;
            }
            DatasetDefinition dataset =
                    datasetsById.get(binding.getDataset());
            if (!hasText(binding.getDataset())
                    || !datasetIds.contains(binding.getDataset())) {
                result.add("EXCEL-001", path + ".dataset",
                        "Unknown table binding dataset: "
                                + binding.getDataset());
            } else if (!boundDatasets.add(binding.getDataset())) {
                result.add("EXCEL-001", path + ".dataset",
                        "Dataset has more than one table binding: "
                                + binding.getDataset());
            }
            if (!hasText(binding.getSheet())) {
                result.add("EXCEL-001", path + ".sheet",
                        "Table binding sheet is required");
            } else if (dataset != null
                    && !binding.getSheet().equals(
                            dataset.getSheetName())) {
                result.add("EXCEL-001", path + ".sheet",
                        "Table binding sheet must match dataset sheetName "
                                + dataset.getSheetName());
            }
            validateTableName(
                    binding.getTable(), path + ".table",
                    tableNames, result);
            if (binding.getStartRow() == null
                    || binding.getStartRow().intValue() < 0
                    || binding.getStartRow().intValue() >= 1048576) {
                result.add("EXCEL-001", path + ".startRow",
                        "startRow must be between 0 and 1048575");
            }
            int maxColumns = org.apache.poi.ss.SpreadsheetVersion
                    .EXCEL2007.getMaxColumns();
            if (safeList(binding.getColumns()).size()
                    > maxColumns) {
                result.add(
                        "EXCEL-001",
                        path + ".columns",
                        "Table columns exceed XLSX column limit "
                                + maxColumns);
            }
            Set<String> expectedFieldNames =
                    new LinkedHashSet<String>();
            if (dataset != null
                    && dataset.getExpectedFields() != null) {
                for (String field
                        : dataset.getExpectedFields().keySet()) {
                    if (hasText(field)) {
                        expectedFieldNames.add(
                                field.toLowerCase(Locale.ROOT));
                    }
                }
            }
            Set<String> finalFields =
                    new LinkedHashSet<String>(
                            expectedFieldNames);
            for (ExcelTableBinding.ColumnBinding column
                    : safeList(binding.getColumns())) {
                if (column != null
                        && hasText(column.getField())) {
                    finalFields.add(column.getField()
                            .toLowerCase(Locale.ROOT));
                }
            }
            if (finalFields.size() > maxColumns
                    && safeList(binding.getColumns()).size()
                    <= maxColumns) {
                result.add(
                        "EXCEL-001",
                        path + ".columns",
                        "Final table fields exceed XLSX column limit "
                                + maxColumns);
            }
            if (binding.isColumnsExplicitNull()
                    || safeList(binding.getColumns()).isEmpty()) {
                result.add("EXCEL-001", path + ".columns",
                        "Table binding requires at least one column");
                continue;
            }
            Set<String> fields = new LinkedHashSet<String>();
            Set<String> headers = new LinkedHashSet<String>();
            for (int columnIndex = 0;
                    columnIndex < binding.getColumns().size();
                    columnIndex++) {
                ExcelTableBinding.ColumnBinding column =
                        binding.getColumns().get(columnIndex);
                String columnPath = path + ".columns["
                        + columnIndex + "]";
                if (column == null) {
                    result.add("EXCEL-001", columnPath,
                            "Excel table column must not be null");
                    continue;
                }
                if (!hasText(column.getField())) {
                    result.add("EXCEL-001", columnPath + ".field",
                            "Column field is required");
                } else {
                    String normalized =
                            column.getField().toLowerCase(Locale.ROOT);
                    if (!fields.add(normalized)) {
                        result.add("EXCEL-001",
                                columnPath + ".field",
                                "Duplicate table column field: "
                                        + column.getField());
                    }
                    if (dataset != null
                            && dataset.getExpectedFields() != null
                            && !dataset.getExpectedFields().isEmpty()
                            && !expectedFieldNames.contains(
                                    normalized)) {
                        result.add("EXCEL-001",
                                columnPath + ".field",
                                "Unknown dataset field: "
                                        + column.getField());
                    }
                }
                if (!hasText(column.getHeader())) {
                    result.add("EXCEL-001", columnPath + ".header",
                            "Column header is required");
                } else if (!headers.add(
                        column.getHeader().toLowerCase(Locale.ROOT))) {
                    result.add("EXCEL-001", columnPath + ".header",
                            "Duplicate table column header: "
                                    + column.getHeader());
                }
                if (column.isFormatPresent()
                        && !hasText(column.getFormat())) {
                    result.add("EXCEL-001", columnPath + ".format",
                            "Column format must be non-blank when configured");
                }
            }
        }
    }

    private void validateCellReference(
            String reference, String path, ValidationResult result) {
        if (!hasText(reference)
                || !reference.matches(
                        "^\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]*$")) {
            result.add("EXCEL-001", path,
                    "Cell must be a single A1 reference");
            return;
        }
        String normalized = reference.replace("$", "");
        int split = 0;
        while (split < normalized.length()
                && Character.isLetter(normalized.charAt(split))) {
            split++;
        }
        int column = org.apache.poi.ss.util.CellReference
                .convertColStringToIndex(
                        normalized.substring(0, split));
        long row;
        try {
            row = Long.parseLong(normalized.substring(split));
        } catch (NumberFormatException exception) {
            result.add("EXCEL-001", path,
                    "Cell is outside the XLSX worksheet bounds");
            return;
        }
        if (column < 0 || column >= 16384
                || row < 1 || row > 1048576) {
            result.add("EXCEL-001", path,
                    "Cell is outside the XLSX worksheet bounds");
        }
    }

    private void validateTableName(
            String tableName,
            String path,
            Set<String> seen,
            ValidationResult result) {
        try {
            ExcelTableNameRules.validate(tableName);
        } catch (IllegalArgumentException exception) {
            result.add("EXCEL-001", path,
                    "Invalid Excel table name: " + tableName);
            return;
        }
        String normalized = ExcelTableNameRules.normalized(tableName);
        if (!seen.add(normalized)) {
            result.add("EXCEL-001", path,
                    "Duplicate Excel table name: " + tableName);
        }
    }

    private void validateTransforms(
            DatasetDefinition dataset,
            String datasetPath,
            ValidationResult result) {
        List<TransformDefinition> transforms = safeList(dataset.getTransforms());
        Set<String> derivedTargets = new LinkedHashSet<String>();
        for (int index = 0; index < transforms.size(); index++) {
            TransformDefinition transform = transforms.get(index);
            String path = datasetPath + ".transforms[" + index + "]";
            if (transform == null) {
                result.add("CFG-TRANSFORM", path,
                        "Transform must not be null");
                continue;
            }
            if (transform.getType() == null) {
                result.add("CFG-TRANSFORM-TYPE", path + ".type",
                        "Transform type is required");
                continue;
            }
            validateTransformAttributes(transform, path, result);
            switch (transform.getType()) {
                case FILTER:
                    validateFilterTransform(transform, path, result);
                    break;
                case SORT:
                    validateSortTransform(transform, path, result);
                    break;
                case DISTINCT:
                    validateDistinctTransform(transform, path, result);
                    break;
                case LIMIT:
                    if (transform.getLimit() == null
                            || transform.getLimit().intValue() < 0) {
                        result.add("CFG-TRANSFORM-LIMIT", path + ".limit",
                                "LIMIT requires a non-negative limit");
                    }
                    break;
                case DERIVED_FIELD:
                    validateDerivedTransform(
                            dataset, transform, path, derivedTargets, result);
                    break;
                default:
                    result.add("CFG-TRANSFORM-TYPE", path + ".type",
                            "Unsupported transform type: " + transform.getType());
            }
        }
    }

    private void validateFilterTransform(
            TransformDefinition transform,
            String path,
            ValidationResult result) {
        if (!hasText(transform.getField())) {
            result.add("CFG-TRANSFORM-FIELD", path + ".field",
                    "FILTER requires a non-blank field");
        }
        TransformOperator operator = transform.getOperator();
        if (!isFilterOperator(operator)) {
            result.add("CFG-TRANSFORM-OPERATOR", path + ".operator",
                    "FILTER requires a filter operator");
        } else if (operator == TransformOperator.IS_NULL
                || operator == TransformOperator.IS_NOT_NULL) {
            if (transform.hasValue()) {
                result.add("CFG-TRANSFORM-ATTRIBUTE", path + ".value",
                        operator + " must not configure value");
            }
        } else if (!transform.hasValue() || transform.getValue() == null) {
            result.add("CFG-TRANSFORM-VALUE", path + ".value",
                    "FILTER comparison requires a value");
        }
    }

    private void validateSortTransform(
            TransformDefinition transform,
            String path,
            ValidationResult result) {
        List<SortFieldDefinition> fields = safeList(transform.getSortFields());
        if (fields.isEmpty()) {
            result.add("CFG-TRANSFORM-SORT-FIELDS", path + ".sortFields",
                    "SORT requires at least one sort field");
        }
        Set<String> seenFields = new LinkedHashSet<String>();
        for (int index = 0; index < fields.size(); index++) {
            SortFieldDefinition field = fields.get(index);
            String fieldPath = path + ".sortFields[" + index + "]";
            if (field == null) {
                result.add("CFG-TRANSFORM-SORT-FIELD", fieldPath,
                        "Sort field must not be null");
                continue;
            }
            if (!hasText(field.getField())) {
                result.add("CFG-TRANSFORM-SORT-FIELD", fieldPath + ".field",
                        "Sort field name is required");
            } else if (!seenFields.add(
                    field.getField().toLowerCase(Locale.ROOT))) {
                result.add("CFG-TRANSFORM-SORT-FIELD", fieldPath + ".field",
                        "Duplicate sort field: " + field.getField());
            }
            if (field.getDirection() == null) {
                result.add("CFG-TRANSFORM-DIRECTION", fieldPath + ".direction",
                        "Sort direction is required");
            }
            if (field.getNullOrder() == null) {
                result.add("CFG-TRANSFORM-NULL-ORDER", fieldPath + ".nullOrder",
                        "Sort null order is required");
            }
        }
    }

    private void validateDistinctTransform(
            TransformDefinition transform,
            String path,
            ValidationResult result) {
        List<String> fields = safeList(transform.getFields());
        if (fields.isEmpty()) {
            result.add("CFG-TRANSFORM-DISTINCT-FIELDS", path + ".fields",
                    "DISTINCT requires at least one field");
            return;
        }
        Set<String> seenFields = new LinkedHashSet<String>();
        for (int index = 0; index < fields.size(); index++) {
            String field = fields.get(index);
            if (!hasText(field)
                    || !seenFields.add(field.toLowerCase(Locale.ROOT))) {
                result.add("CFG-TRANSFORM-DISTINCT-FIELDS",
                        path + ".fields[" + index + "]",
                        "DISTINCT fields must be non-blank and unique");
            }
        }
    }

    private void validateDerivedTransform(
            DatasetDefinition dataset,
            TransformDefinition transform,
            String path,
            Set<String> derivedTargets,
            ValidationResult result) {
        if (dataset.getResultType() == com.xn.report.dataset.DatasetType.SCALAR) {
            result.add("CFG-TRANSFORM-DATASET-TYPE", path,
                    "DERIVED_FIELD supports only LIST or SINGLE datasets");
        }
        if (!hasText(transform.getSourceField())) {
            result.add("CFG-TRANSFORM-SOURCE-FIELD", path + ".sourceField",
                    "DERIVED_FIELD requires a sourceField");
        }
        if (!hasText(transform.getTargetField())) {
            result.add("CFG-TRANSFORM-TARGET-FIELD", path + ".targetField",
                    "DERIVED_FIELD requires a targetField");
        } else if (!derivedTargets.add(
                transform.getTargetField().toLowerCase(Locale.ROOT))) {
            result.add("CFG-TRANSFORM-DUPLICATE-TARGET", path + ".targetField",
                    "Duplicate derived target: " + transform.getTargetField());
        }
        if (!isArithmeticOperator(transform.getOperator())) {
            result.add("CFG-TRANSFORM-OPERATOR", path + ".operator",
                    "DERIVED_FIELD requires an arithmetic operator");
        }
        if (transform.getOperand() == null) {
            result.add("CFG-TRANSFORM-OPERAND", path + ".operand",
                    "DERIVED_FIELD requires a numeric operand");
        }
        if (transform.getScale() != null
                && transform.getScale().intValue() < 0) {
            result.add("CFG-TRANSFORM-SCALE", path + ".scale",
                    "DERIVED_FIELD scale must be non-negative");
        }
        if (transform.getDivideByZeroStrategy()
                == com.xn.report.transform.DivideByZeroStrategy.DEFAULT_VALUE
                && (!transform.hasProperty("divideByZeroDefault")
                        || transform.getDivideByZeroDefault() == null)) {
            result.add("CFG-TRANSFORM-DIVIDE-DEFAULT",
                    path + ".divideByZeroDefault",
                    "DEFAULT_VALUE requires divideByZeroDefault");
        }
        if (transform.getOperator() != TransformOperator.DIVIDE
                && (transform.hasProperty("divideByZeroStrategy")
                || transform.hasProperty("divideByZeroDefault"))) {
            result.add("CFG-TRANSFORM-ATTRIBUTE", path,
                    "Divide-by-zero settings require DIVIDE operator");
        } else if (transform.getOperator() == TransformOperator.DIVIDE
                && transform.getDivideByZeroStrategy()
                        != com.xn.report.transform.DivideByZeroStrategy.DEFAULT_VALUE
                && transform.hasProperty("divideByZeroDefault")) {
            result.add("CFG-TRANSFORM-ATTRIBUTE",
                    path + ".divideByZeroDefault",
                    "divideByZeroDefault requires DEFAULT_VALUE strategy");
        }
    }

    private void validateTransformAttributes(
            TransformDefinition transform,
            String path,
            ValidationResult result) {
        Set<String> allowed;
        Set<String> required;
        switch (transform.getType()) {
            case FILTER:
                allowed = unmodifiableSet("type", "field", "operator", "value");
                required = unmodifiableSet("type", "field", "operator");
                break;
            case SORT:
                allowed = unmodifiableSet("type", "sortFields");
                required = unmodifiableSet("type", "sortFields");
                break;
            case DISTINCT:
                allowed = unmodifiableSet("type", "fields");
                required = unmodifiableSet("type", "fields");
                break;
            case LIMIT:
                allowed = unmodifiableSet("type", "limit");
                required = unmodifiableSet("type", "limit");
                break;
            case DERIVED_FIELD:
                allowed = unmodifiableSet(
                        "type",
                        "sourceField",
                        "targetField",
                        "operator",
                        "operand",
                        "scale",
                        "divideByZeroStrategy",
                        "divideByZeroDefault",
                        "fieldConflictStrategy");
                required = unmodifiableSet(
                        "type",
                        "sourceField",
                        "targetField",
                        "operator",
                        "operand");
                break;
            default:
                allowed = Collections.emptySet();
                required = Collections.emptySet();
        }
        for (String property : transform.getPresentProperties()) {
            if (!allowed.contains(property)) {
                result.add("CFG-TRANSFORM-ATTRIBUTE", path + "." + property,
                        property + " is not allowed for "
                                + transform.getType());
            } else if (transformPropertyValue(transform, property) == null) {
                result.add("CFG-TRANSFORM-ATTRIBUTE", path + "." + property,
                        property + " must not be null");
            }
        }
        for (String property : required) {
            if (!transform.hasProperty(property)) {
                result.add("CFG-TRANSFORM-ATTRIBUTE", path + "." + property,
                        property + " is required for " + transform.getType());
            }
        }
    }

    private static Object transformPropertyValue(
            TransformDefinition transform, String property) {
        switch (property) {
            case "type":
                return transform.getType();
            case "field":
                return transform.getField();
            case "fields":
                return transform.getFields();
            case "sortFields":
                return transform.getSortFields();
            case "operator":
                return transform.getOperator();
            case "value":
                return transform.getValue();
            case "sourceField":
                return transform.getSourceField();
            case "targetField":
                return transform.getTargetField();
            case "operand":
                return transform.getOperand();
            case "limit":
                return transform.getLimit();
            case "scale":
                return transform.getScale();
            case "divideByZeroStrategy":
                return transform.getDivideByZeroStrategy();
            case "divideByZeroDefault":
                return transform.getDivideByZeroDefault();
            case "fieldConflictStrategy":
                return transform.getFieldConflictStrategy();
            default:
                throw new IllegalArgumentException(
                        "Unknown transform property: " + property);
        }
    }

    private static boolean isFilterOperator(TransformOperator operator) {
        if (operator == null) {
            return false;
        }
        switch (operator) {
            case EQUAL:
            case EQ:
            case NOT_EQUAL:
            case NE:
            case GREATER_THAN:
            case GT:
            case GREATER_THAN_OR_EQUAL:
            case GTE:
            case LESS_THAN:
            case LT:
            case LESS_THAN_OR_EQUAL:
            case LTE:
            case IS_NULL:
            case IS_NOT_NULL:
                return true;
            default:
                return false;
        }
    }

    private static boolean isArithmeticOperator(TransformOperator operator) {
        return operator == TransformOperator.ADD
                || operator == TransformOperator.SUBTRACT
                || operator == TransformOperator.MULTIPLY
                || operator == TransformOperator.DIVIDE;
    }

    private void validateSheetName(
            String sheetName,
            String path,
            List<String> seen,
            ValidationResult result) {
        if (!hasText(sheetName)) {
            result.add("CFG-SHEET-NAME-REQUIRED", path, "sheetName is required");
            return;
        }
        try {
            ExcelSheetNameRules.validate(sheetName);
        } catch (IllegalArgumentException exception) {
            String code = sheetName.length() > 31
                    ? "CFG-SHEET-NAME-LENGTH" : "CFG-SHEET-NAME-ILLEGAL";
            result.add(code, path, exception.getMessage());
        }
        if (ExcelSheetNameRules.containsIgnoreCase(seen, sheetName)) {
            result.add("CFG-DUPLICATE-SHEET-NAME", path,
                    "Duplicate sheetName: " + sheetName);
        }
        seen.add(sheetName);
    }

    private void validateDependencies(
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            ValidationResult result) {
        for (int datasetIndex = 0; datasetIndex < datasets.size(); datasetIndex++) {
            DatasetDefinition dataset = datasets.get(datasetIndex);
            if (dataset == null) {
                continue;
            }
            List<String> dependencies = safeList(dataset.getDependsOn());
            for (int dependencyIndex = 0;
                    dependencyIndex < dependencies.size();
                    dependencyIndex++) {
                String dependency = dependencies.get(dependencyIndex);
                if (!hasText(dependency) || !datasetIds.contains(dependency)) {
                    result.add("CFG-UNKNOWN-DEPENDENCY",
                            "$.datasets[" + datasetIndex + "].dependsOn["
                                    + dependencyIndex + "]",
                            "Unknown dataset dependency: " + dependency);
                }
            }
        }
    }

    private void validateDependencyCycles(
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            ValidationResult result) {
        Map<String, List<String>> graph = new LinkedHashMap<String, List<String>>();
        for (String id : datasetIds) {
            graph.put(id, new ArrayList<String>());
        }
        for (DatasetDefinition dataset : datasets) {
            if (dataset == null || !hasText(dataset.getId())
                    || !graph.containsKey(dataset.getId())) {
                continue;
            }
            for (String dependency : safeList(dataset.getDependsOn())) {
                if (graph.containsKey(dependency)) {
                    graph.get(dataset.getId()).add(dependency);
                }
            }
        }

        Map<String, Integer> states = new LinkedHashMap<String, Integer>();
        for (String id : graph.keySet()) {
            if (hasCycle(id, graph, states)) {
                result.add("CFG-DEPENDENCY-CYCLE", "$.datasets",
                        "Dataset dependency graph contains a cycle involving " + id);
                return;
            }
        }
    }

    private boolean hasCycle(
            String id,
            Map<String, List<String>> graph,
            Map<String, Integer> states) {
        Integer state = states.get(id);
        if (Integer.valueOf(1).equals(state)) {
            return true;
        }
        if (Integer.valueOf(2).equals(state)) {
            return false;
        }

        states.put(id, 1);
        for (String dependency : graph.get(id)) {
            if (hasCycle(dependency, graph, states)) {
                return true;
            }
        }
        states.put(id, 2);
        return false;
    }

    private Set<String> validateNarratives(
            List<NarrativeDefinition> narratives,
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            Map<String, ParameterDefinition> parameters,
            ValidationResult result) {
        Set<String> narrativeIds = new LinkedHashSet<String>();
        Map<String, DatasetDefinition> datasetsById =
                new LinkedHashMap<String, DatasetDefinition>();
        for (DatasetDefinition dataset : safeList(datasets)) {
            if (dataset != null && hasText(dataset.getId())) {
                datasetsById.put(dataset.getId(), dataset);
            }
        }
        List<NarrativeDefinition> safeNarratives = safeList(narratives);
        for (int index = 0; index < safeNarratives.size(); index++) {
            NarrativeDefinition narrative = safeNarratives.get(index);
            String path = "$.narratives[" + index + "]";
            if (narrative == null) {
                result.add("CFG-NARRATIVE", path, "Narrative must not be null");
                continue;
            }
            if (!hasText(narrative.getId())) {
                result.add("TEXT-001", path + ".id", "Narrative id is required");
            } else if (!narrativeIds.add(narrative.getId())) {
                result.add("CFG-DUPLICATE-NARRATIVE", path + ".id",
                        "Duplicate narrative id: " + narrative.getId());
            }
            if (narrative.getSourceType() == null) {
                result.add("TEXT-001", path + ".sourceType",
                        "Narrative sourceType must be FIXED_TEMPLATE or RULE_GENERATED");
            }
            if (narrative.hasProperty("parameters")
                    && narrative.getParameters() == null) {
                result.add("TEXT-001", path + ".parameters",
                        "Narrative parameters must not be null");
            }
            if (narrative.hasProperty("emptyStrategy")
                    && narrative.getEmptyStrategy() == null) {
                result.add("TEXT-001", path + ".emptyStrategy",
                        "Narrative emptyStrategy must not be null");
            }
            if (hasText(narrative.getDataset())
                    && !datasetIds.contains(narrative.getDataset())) {
                result.add("CFG-UNKNOWN-DATASET-REFERENCE", path + ".dataset",
                        "Unknown dataset reference: " + narrative.getDataset());
            }
            validateNarrativeVariant(
                    narrative, path, datasetIds, datasetsById,
                    parameters, result);
            if (narrative.hasProperty("distribution")
                    && narrative.getDistribution() == null) {
                result.add("TEXT-001", path + ".distribution",
                        "Narrative distribution must not be null");
            }
            if (narrative.hasProperty("distribution")
                    || hasDistributionContent(narrative.getDistribution())) {
                validateDistribution(narrative.getDistribution(),
                        path + ".distribution",
                        datasetsById.get(narrative.getDataset()),
                        result);
            }
        }
        return narrativeIds;
    }

    private boolean hasDistributionContent(DistributionDefinition distribution) {
        return distribution != null
                && (hasText(distribution.getField())
                || (distribution.getBins() != null
                && !distribution.getBins().isEmpty())
                || distribution.hasProperty("labelMode"));
    }

    private void validateNarrativeVariant(
            NarrativeDefinition narrative,
            String path,
            Set<String> datasetIds,
            Map<String, DatasetDefinition> datasetsById,
            Map<String, ParameterDefinition> parameters,
            ValidationResult result) {
        rejectNarrativeExplicitNull(
                narrative, "baseline", narrative.getBaseline(), path, result);
        rejectNarrativeExplicitNull(
                narrative, "format", narrative.getFormat(), path, result);
        if (narrative.getSourceType()
                == NarrativeDefinition.SourceType.FIXED_TEMPLATE) {
            if (!hasText(narrative.getTemplate())) {
                result.add("TEXT-001", path + ".template",
                        "FIXED_TEMPLATE requires template");
            } else {
                validateTextTemplate(
                        narrative.getTemplate(),
                        path + ".template",
                        result);
            }
            rejectNarrativeProperties(
                    narrative, path, result,
                    "analyzer", "analyzerType", "baseline", "format",
                    "sentence", "distribution", "trend");
        } else if (narrative.getSourceType()
                == NarrativeDefinition.SourceType.RULE_GENERATED) {
            if (!hasText(narrative.getAnalyzer())) {
                result.add("TEXT-001", path + ".analyzer",
                        "RULE_GENERATED requires analyzer");
            }
            if (!hasText(narrative.getDataset())) {
                result.add("TEXT-001", path + ".dataset",
                        "RULE_GENERATED requires dataset");
            }
            if (!hasText(narrative.getSentence())) {
                result.add("TEXT-001", path + ".sentence",
                        "RULE_GENERATED requires sentence");
            } else {
                validateTextTemplate(
                        narrative.getSentence(),
                        path + ".sentence",
                        result);
            }
            rejectNarrativeProperties(narrative, path, result, "template");
            if (narrative.getAnalyzerType() == null) {
                result.add("TEXT-001", path + ".analyzerType",
                        "RULE_GENERATED requires analyzerType");
            } else if (narrative.getAnalyzerType()
                    == NarrativeDefinition.AnalyzerType.TREND) {
                if (!narrative.hasProperty("trend")
                        || narrative.getTrend() == null) {
                    result.add("TEXT-001", path + ".trend",
                            "TREND requires non-null trend");
                } else {
                    validateTrend(
                            narrative,
                            narrative.getTrend(),
                            path + ".trend",
                            datasetIds,
                            datasetsById,
                            parameters,
                            result);
                }
                rejectNarrativeProperties(
                        narrative, path, result, "distribution");
            } else {
                if (!narrative.hasProperty("distribution")
                        || narrative.getDistribution() == null) {
                    result.add("TEXT-001", path + ".distribution",
                            "DISTRIBUTION requires non-null distribution");
                }
                rejectNarrativeProperties(
                        narrative, path, result, "trend", "baseline");
            }
        }
    }

    private void rejectNarrativeExplicitNull(
            NarrativeDefinition narrative,
            String property,
            Object value,
            String path,
            ValidationResult result) {
        if (narrative.hasProperty(property) && value == null) {
            result.add("TEXT-001", path + "." + property,
                    property + " must not be null");
        }
    }

    private void validateTrend(
            NarrativeDefinition narrative,
            TrendDefinition trend,
            String path,
            Set<String> datasetIds,
            Map<String, DatasetDefinition> datasetsById,
            Map<String, ParameterDefinition> parameters,
            ValidationResult result) {
        if (!hasText(trend.getPeriodField())) {
            result.add("TEXT-001", path + ".periodField",
                    "Trend periodField is required");
        }
        if (!hasText(trend.getValueField())) {
            result.add("TEXT-001", path + ".valueField",
                    "Trend valueField is required");
        }
        DatasetDefinition source = datasetsById.get(narrative.getDataset());
        validateNarrativeDatasetField(
                source,
                trend.getPeriodField(),
                path + ".periodField",
                result);
        validateNarrativeDatasetField(
                source,
                trend.getValueField(),
                path + ".valueField",
                result);
        if (trend.getComparisonSource() == null) {
            result.add("TEXT-001", path + ".comparisonSource",
                    "Trend comparisonSource is required");
            return;
        }
        rejectTrendExplicitNull(
                trend, "flatTolerance", trend.getFlatTolerance(), path, result);
        rejectTrendExplicitNull(
                trend, "abnormalThreshold", trend.getAbnormalThreshold(), path, result);
        if (trend.getFlatTolerance() == null
                || trend.getFlatTolerance().signum() < 0) {
            result.add("TEXT-001", path + ".flatTolerance",
                    "Trend flatTolerance must be non-negative");
        }
        switch (trend.getComparisonSource()) {
            case PREVIOUS_YEAR:
                rejectTrendProperties(trend, path, result,
                        "comparisonDataset", "comparisonField",
                        "comparisonParameter", "comparisonValue");
                break;
            case LITERAL:
                requireTrendProperty(
                        trend, "comparisonValue", trend.getComparisonValue(),
                        path, result);
                rejectTrendProperties(trend, path, result,
                        "comparisonDataset", "comparisonField",
                        "comparisonParameter");
                break;
            case RUNTIME_PARAMETER:
                requireTrendText(
                        trend.getComparisonParameter(),
                        path + ".comparisonParameter",
                        "comparisonParameter is required",
                        result);
                if (hasText(trend.getComparisonParameter())
                        && (parameters == null
                        || !parameters.containsKey(
                        trend.getComparisonParameter()))) {
                    result.add("TEXT-001", path + ".comparisonParameter",
                            "Unknown runtime parameter: "
                                    + trend.getComparisonParameter());
                }
                rejectTrendProperties(trend, path, result,
                        "comparisonDataset", "comparisonField",
                        "comparisonValue");
                break;
            case DATASET_FIELD:
                requireTrendText(
                        trend.getComparisonDataset(),
                        path + ".comparisonDataset",
                        "comparisonDataset is required",
                        result);
                requireTrendText(
                        trend.getComparisonField(),
                        path + ".comparisonField",
                        "comparisonField is required",
                        result);
                validateTrendDataset(
                        trend.getComparisonDataset(),
                        path + ".comparisonDataset",
                        datasetIds,
                        result);
                validateTrendComparisonField(
                        trend.getComparisonDataset(),
                        trend.getComparisonField(),
                        path + ".comparisonField",
                        datasetsById,
                        result);
                rejectTrendProperties(trend, path, result,
                        "comparisonParameter", "comparisonValue");
                break;
            case ANNUAL_BASELINE:
                requireTrendText(
                        trend.getComparisonField(),
                        path + ".comparisonField",
                        "comparisonField is required",
                        result);
                if (trend.hasProperty("comparisonDataset")
                        && trend.getComparisonDataset() == null) {
                    result.add("TEXT-001", path + ".comparisonDataset",
                            "comparisonDataset must not be null");
                }
                String baseline = trend.hasProperty("comparisonDataset")
                        ? trend.getComparisonDataset() : narrative.getBaseline();
                if (!hasText(baseline)) {
                    result.add("TEXT-001", path + ".comparisonDataset",
                            "ANNUAL_BASELINE requires comparisonDataset or baseline");
                } else {
                    validateTrendDataset(
                            baseline, path + ".comparisonDataset",
                            datasetIds, result);
                    validateTrendComparisonField(
                            baseline,
                            trend.getComparisonField(),
                            path + ".comparisonField",
                            datasetsById,
                            result);
                }
                rejectTrendProperties(trend, path, result,
                        "comparisonParameter", "comparisonValue");
                break;
            default:
                result.add("TEXT-001", path + ".comparisonSource",
                        "Unsupported comparisonSource");
        }
    }

    private void validateTrendComparisonField(
            String datasetId,
            String field,
            String path,
            Map<String, DatasetDefinition> datasetsById,
            ValidationResult result) {
        DatasetDefinition dataset = datasetsById.get(datasetId);
        if (dataset == null
                || dataset.getExpectedFields() == null
                || dataset.getExpectedFields().isEmpty()
                || !hasText(field)) {
            return;
        }
        boolean found = false;
        for (String actual : availableDatasetFields(dataset)) {
            if (actual.equalsIgnoreCase(field)) {
                found = true;
                break;
            }
        }
        if (!found) {
            result.add("TEXT-001", path,
                    "Unknown comparison field " + field
                            + " for dataset " + datasetId
                            + "; expected "
                            + availableDatasetFields(dataset));
        }
    }

    private void validateNarrativeDatasetField(
            DatasetDefinition dataset,
            String field,
            String path,
            ValidationResult result) {
        if (dataset == null
                || dataset.getExpectedFields() == null
                || dataset.getExpectedFields().isEmpty()
                || !hasText(field)
                || containsField(availableDatasetFields(dataset), field)) {
            return;
        }
        result.add("TEXT-001", path,
                "Unknown narrative field " + field
                        + " in dataset " + dataset.getId());
    }

    private void validateTextTemplate(
            String template,
            String path,
            ValidationResult result) {
        try {
            for (Part part : PLACEHOLDER_PARSER.parse(template)) {
                if (!part.isLiteral()
                        && part.formatter() != null
                        && !FORMATTERS.supports(part.formatter())) {
                    result.add("TEXT-001", path,
                            "Unknown formatter: " + part.formatter());
                }
            }
        } catch (RuntimeException exception) {
            result.add("TEXT-001", path,
                    exception.getMessage() == null
                            ? "Invalid text template"
                            : exception.getMessage());
        }
    }

    private void validateTrendDataset(
            String id,
            String path,
            Set<String> datasetIds,
            ValidationResult result) {
        if (hasText(id) && !datasetIds.contains(id)) {
            result.add("TEXT-001", path,
                    "Unknown comparison dataset: " + id);
        }
    }

    private void requireTrendText(
            String value,
            String path,
            String message,
            ValidationResult result) {
        if (!hasText(value)) {
            result.add("TEXT-001", path, message);
        }
    }

    private void requireTrendProperty(
            TrendDefinition trend,
            String property,
            Object value,
            String path,
            ValidationResult result) {
        if (!trend.hasProperty(property) || value == null) {
            result.add("TEXT-001", path + "." + property,
                    property + " is required");
        }
    }

    private void rejectTrendExplicitNull(
            TrendDefinition trend,
            String property,
            Object value,
            String path,
            ValidationResult result) {
        if (trend.hasProperty(property) && value == null) {
            result.add("TEXT-001", path + "." + property,
                    property + " must not be null");
        }
    }

    private void rejectTrendProperties(
            TrendDefinition trend,
            String path,
            ValidationResult result,
            String... properties) {
        for (String property : properties) {
            if (trend.hasProperty(property)) {
                result.add("TEXT-001", path + "." + property,
                        property + " is not allowed for "
                                + trend.getComparisonSource());
            }
        }
    }

    private void rejectNarrativeProperties(
            NarrativeDefinition narrative,
            String path,
            ValidationResult result,
            String... properties) {
        for (String property : properties) {
            if (narrative.hasProperty(property)) {
                result.add("TEXT-001", path + "." + property,
                        property + " is not allowed for "
                                + narrative.getSourceType());
            }
        }
    }

    private void validateDistribution(
            DistributionDefinition distribution,
            String path,
            DatasetDefinition dataset,
            ValidationResult result) {
        if (distribution == null) {
            return;
        }
        if (!hasText(distribution.getField())) {
            result.add("TEXT-001", path + ".field",
                    "Distribution field is required");
        }
        validateNarrativeDatasetField(
                dataset,
                distribution.getField(),
                path + ".field",
                result);
        if (distribution.hasProperty("bins")
                && distribution.getBins() == null) {
            result.add("TEXT-001", path + ".bins",
                    "Distribution bins must not be null");
        }
        if (distribution.getBins() == null
                || distribution.getBins().isEmpty()) {
            result.add("TEXT-001", path + ".bins",
                    "Distribution requires at least one bin");
        }
        if (!distribution.hasProperty("labelMode")) {
            result.add("TEXT-001", path + ".labelMode",
                    "Distribution labelMode is required");
        } else if (distribution.getLabelMode() == null) {
            result.add("TEXT-001", path + ".labelMode",
                    "Distribution labelMode must not be null");
        }
        List<BinDefinition> bins = safeList(distribution.getBins());
        Set<String> binIds = new LinkedHashSet<String>();
        for (int index = 0; index < bins.size(); index++) {
            BinDefinition bin = bins.get(index);
            if (bin == null) {
                result.add("CFG-DISTRIBUTION-BIN", path + ".bins[" + index + "]",
                        "Distribution bin must not be null");
                continue;
            }
            if (!hasText(bin.getId())) {
                result.add("TEXT-001", path + ".bins[" + index + "].id",
                        "Distribution bin id is required");
            } else if (!binIds.add(bin.getId())) {
                result.add("TEXT-001", path + ".bins[" + index + "].id",
                        "Duplicate distribution bin id: " + bin.getId());
            }
            if (!hasText(bin.getLabel())) {
                result.add("TEXT-001", path + ".bins[" + index + "].label",
                        "Distribution bin label is required");
            }
            rejectExplicitNullBinProperty(
                    bin, "min", path + ".bins[" + index + "]", result);
            rejectExplicitNullBinProperty(
                    bin, "max", path + ".bins[" + index + "]", result);
            rejectExplicitNullBinProperty(
                    bin, "minInclusive", path + ".bins[" + index + "]", result);
            rejectExplicitNullBinProperty(
                    bin, "maxInclusive", path + ".bins[" + index + "]", result);
            if (bin.isMinInclusive() && bin.getMin() == null) {
                result.add("TEXT-001",
                        path + ".bins[" + index + "].minInclusive",
                        "minInclusive requires min");
            }
            if (bin.isMaxInclusive() && bin.getMax() == null) {
                result.add("TEXT-001",
                        path + ".bins[" + index + "].maxInclusive",
                        "maxInclusive requires max");
            }
            if (isEmptyOrReversed(bin)) {
                result.add("CFG-DISTRIBUTION-BOUNDS", path + ".bins[" + index + "]",
                        "Distribution bin bounds do not define a non-empty interval");
            }
            for (int otherIndex = 0; otherIndex < index; otherIndex++) {
                BinDefinition other = bins.get(otherIndex);
                if (other != null && intervalsOverlap(bin, other)) {
                    result.add("CFG-DISTRIBUTION-OVERLAP", path + ".bins[" + index + "]",
                            "Distribution bins overlap: "
                                    + other.getId() + " and " + bin.getId());
                }
            }
        }
    }

    private void rejectExplicitNullBinProperty(
            BinDefinition bin,
            String property,
            String path,
            ValidationResult result) {
        if (!bin.hasProperty(property)) {
            return;
        }
        Object value;
        if ("min".equals(property)) {
            value = bin.getMin();
        } else if ("max".equals(property)) {
            value = bin.getMax();
        } else if ("minInclusive".equals(property)) {
            value = bin.getMinInclusive();
        } else {
            value = bin.getMaxInclusive();
        }
        if (value == null) {
            result.add("TEXT-001", path + "." + property,
                    property + " must not be null");
        }
    }

    private boolean isEmptyOrReversed(BinDefinition bin) {
        BigDecimal min = bin.getMin();
        BigDecimal max = bin.getMax();
        if (min == null || max == null) {
            return false;
        }
        int comparison = min.compareTo(max);
        return comparison > 0
                || (comparison == 0 && !(bin.isMinInclusive() && bin.isMaxInclusive()));
    }

    private boolean intervalsOverlap(BinDefinition left, BinDefinition right) {
        if (endsBefore(
                left.getMax(), left.isMaxInclusive(),
                right.getMin(), right.isMinInclusive())) {
            return false;
        }
        return !endsBefore(
                right.getMax(), right.isMaxInclusive(),
                left.getMin(), left.isMinInclusive());
    }

    private boolean endsBefore(
            BigDecimal upper,
            boolean upperInclusive,
            BigDecimal lower,
            boolean lowerInclusive) {
        if (upper == null || lower == null) {
            return false;
        }
        int comparison = upper.compareTo(lower);
        return comparison < 0
                || (comparison == 0 && !(upperInclusive && lowerInclusive));
    }

    private void validateWord(
            WordDefinition word,
            boolean wordTemplateConfigured,
            Set<String> datasetIds,
            Set<String> narrativeIds,
            Set<String> chartIds,
            ValidationResult result) {
        if (word == null) {
            return;
        }
        WordCoverDefinition cover = word.getCover();
        boolean coverStarted = cover != null
                && (hasText(cover.getTitle())
                || hasText(cover.getOrganization())
                || hasText(cover.getReportPeriod())
                || hasText(cover.getPreparedBy())
                || hasText(cover.getPreparedDate()));
        if (wordTemplateConfigured || coverStarted) {
            validateWordCover(cover, result);
        }
        if (word.getToc() != null
                && (word.getToc().getMaxLevel() < 1 || word.getToc().getMaxLevel() > 4)) {
            result.add("CFG-TOC-LEVEL", "$.word.toc.maxLevel",
                    "TOC maxLevel must be between 1 and 4");
        }
        if (word.getToc() != null
                && word.getToc().isEnabled()
                && !word.getToc().isUpdateOnOpen()) {
            result.add("CFG-TOC-UPDATE", "$.word.toc.updateOnOpen",
                    "Enabled TOC must update fields when Word opens the document");
        }
        validateWordNumbering(word.getNumbering(), result);
        Set<String> sectionIds = new LinkedHashSet<String>();
        Set<String> tableBindingIds = validateWordTableBindings(
                word.getTableBindings(), datasetIds, result);
        Set<WordSectionDefinition> visited = Collections.newSetFromMap(
                new IdentityHashMap<WordSectionDefinition, Boolean>());
        List<WordSectionDefinition> sections = safeList(word.getSections());
        for (int index = 0; index < sections.size(); index++) {
            validateSection(sections.get(index), null, 1,
                    "$.word.sections[" + index + "]",
                    sectionIds, tableBindingIds, datasetIds,
                    narrativeIds, chartIds,
                    visited, result);
        }
    }

    private void validateWordNumbering(
            WordNumberingDefinition numbering, ValidationResult result) {
        if (numbering == null) {
            result.add("CFG-WORD-NUMBERING", "$.word.numbering",
                    "Word numbering definition is required");
            return;
        }
        if (numbering.getNumId() != null
                && numbering.getNumId().longValue() <= 0L) {
            result.add("CFG-WORD-NUMBERING-ID", "$.word.numbering.numId",
                    "Word numbering numId must be positive");
        }
        List<WordNumberingLevelDefinition> levels =
                safeList(numbering.getLevels());
        if (levels.size() != 4) {
            result.add("CFG-WORD-NUMBERING-LEVEL",
                    "$.word.numbering.levels",
                    "Word numbering requires exactly four levels");
        }
        Set<Integer> configuredLevels = new LinkedHashSet<Integer>();
        for (int index = 0; index < levels.size(); index++) {
            WordNumberingLevelDefinition level = levels.get(index);
            String path = "$.word.numbering.levels[" + index + "]";
            if (level == null) {
                result.add("CFG-WORD-NUMBERING-LEVEL", path,
                        "Word numbering level must not be null");
                continue;
            }
            if (level.getLevel() < 1 || level.getLevel() > 4
                    || !configuredLevels.add(
                    Integer.valueOf(level.getLevel()))) {
                result.add("CFG-WORD-NUMBERING-LEVEL", path + ".level",
                        "Word numbering levels must uniquely contain 1 through 4");
            }
            if (!hasText(level.getNumFmt())
                    || !WORD_NUMBER_FORMATS.contains(level.getNumFmt())) {
                result.add("CFG-WORD-NUMBERING-FORMAT", path + ".numFmt",
                        "Word numbering numFmt is unknown");
            }
            if (!hasText(level.getLvlText())) {
                result.add("CFG-WORD-NUMBERING-TEXT", path + ".lvlText",
                        "Word numbering lvlText must not be blank");
            }
        }
        if (configuredLevels.size() != 4) {
            result.add("CFG-WORD-NUMBERING-LEVEL",
                    "$.word.numbering.levels",
                    "Word numbering levels must uniquely contain 1 through 4");
        }
    }

    private Set<String> validateWordTableBindings(
            List<WordTableBinding> bindings,
            Set<String> datasetIds,
            ValidationResult result) {
        Set<String> ids = new LinkedHashSet<String>();
        List<WordTableBinding> safe = safeList(bindings);
        for (int index = 0; index < safe.size(); index++) {
            WordTableBinding binding = safe.get(index);
            String path = "$.word.tableBindings[" + index + "]";
            if (binding == null) {
                result.add("CFG-WORD-TABLE-BINDING", path,
                        "Word table binding must not be null");
                continue;
            }
            if (!hasText(binding.getId())) {
                result.add("CFG-WORD-TABLE-BINDING", path + ".id",
                        "Word table binding id is required");
            } else if (!ids.add(binding.getId())) {
                result.add("CFG-DUPLICATE-WORD-TABLE-BINDING",
                        path + ".id",
                        "Duplicate Word table binding id: "
                                + binding.getId());
            }
            if (!hasText(binding.getDataset())
                    || !datasetIds.contains(binding.getDataset())) {
                result.add("CFG-WORD-TABLE-BINDING", path + ".dataset",
                        "Word table binding references an unknown dataset: "
                                + binding.getDataset());
            }
            if (hasText(binding.getMarker())
                    == hasText(binding.getTableId())) {
                result.add("CFG-WORD-TABLE-BINDING", path,
                        "Word table binding requires exactly one marker or tableId");
            }
            if (!"PROTOTYPE".equals(binding.getStrategy())
                    && !"GENERATED".equals(binding.getStrategy())) {
                result.add("CFG-WORD-TABLE-BINDING", path + ".strategy",
                        "Word table binding strategy must be PROTOTYPE or GENERATED");
            }
            if (!EMPTY_STRATEGIES.contains(binding.getEmptyStrategy())) {
                result.add("CFG-WORD-TABLE-BINDING",
                        path + ".emptyStrategy",
                        "Word table emptyStrategy must be KEEP, SHOW_EMPTY, or SKIP");
            }
            if (!hasText(binding.getEmptyMessage())) {
                result.add("CFG-WORD-TABLE-BINDING",
                        path + ".emptyMessage",
                        "Word table emptyMessage must not be blank");
            }
            validateWordTableColumns(
                    binding.getColumns(), path + ".columns", result);
        }
        return ids;
    }

    private void validateWordTableColumns(
            List<WordTableColumnDefinition> configured,
            String path,
            ValidationResult result) {
        List<WordTableColumnDefinition> columns = safeList(configured);
        for (int index = 0; index < columns.size(); index++) {
            WordTableColumnDefinition column = columns.get(index);
            String columnPath = path + "[" + index + "]";
            if (column == null) {
                result.add("CFG-WORD-TABLE-COLUMN", columnPath,
                        "Word table column must not be null");
                continue;
            }
            if (!hasText(column.getField())) {
                result.add("CFG-WORD-TABLE-COLUMN",
                        columnPath + ".field",
                        "Word table column field is required");
            }
            if (column.getWidthDxa() != null
                    && column.getWidthDxa().intValue() <= 0) {
                result.add("CFG-WORD-TABLE-COLUMN",
                        columnPath + ".widthDxa",
                        "Word table column widthDxa must be positive");
            }
        }
    }

    private void validateWordCover(
            WordCoverDefinition cover, ValidationResult result) {
        if (cover == null) {
            result.add("CFG-WORD-COVER", "$.word.cover",
                    "Word cover definition is required");
            return;
        }
        requireText(result, cover.getTitle(), "CFG-WORD-COVER",
                "$.word.cover.title", "Word cover title is required");
        requireText(result, cover.getOrganization(), "CFG-WORD-COVER",
                "$.word.cover.organization", "Word cover organization is required");
        requireText(result, cover.getReportPeriod(), "CFG-WORD-COVER",
                "$.word.cover.reportPeriod", "Word cover reportPeriod is required");
        requireText(result, cover.getPreparedBy(), "CFG-WORD-COVER",
                "$.word.cover.preparedBy", "Word cover preparedBy is required");
        requireText(result, cover.getPreparedDate(), "CFG-WORD-COVER",
                "$.word.cover.preparedDate", "Word cover preparedDate is required");
    }

    private void validateSection(
            WordSectionDefinition section,
            Integer parentLevel,
            int depth,
            String path,
            Set<String> sectionIds,
            Set<String> tableBindingIds,
            Set<String> datasetIds,
            Set<String> narrativeIds,
            Set<String> chartIds,
            Set<WordSectionDefinition> visited,
            ValidationResult result) {
        if (section == null) {
            result.add("CFG-SECTION", path, "Word section must not be null");
            return;
        }
        if (depth > 4) {
            result.add("CFG-SECTION-DEPTH", path,
                    "Word section nesting must not exceed four levels");
            return;
        }
        if (!visited.add(section)) {
            result.add("CFG-SECTION-CYCLE", path, "Word section tree contains a cycle");
            return;
        }
        if (!hasText(section.getId())) {
            result.add("CFG-SECTION-ID", path + ".id", "Word section id is required");
        } else if (!sectionIds.add(section.getId())) {
            result.add("CFG-DUPLICATE-SECTION", path + ".id",
                    "Duplicate word section id: " + section.getId());
        }
        if (!hasText(section.getTitle())) {
            result.add("CFG-SECTION-TITLE", path + ".title",
                    "Word section title is required");
        } else if (section.getTitle().length() > MAX_SECTION_TITLE_UTF16_LENGTH) {
            result.add("CFG-SECTION-TITLE-LENGTH", path + ".title",
                    "Word section title must not exceed "
                            + MAX_SECTION_TITLE_UTF16_LENGTH
                            + " Java UTF-16 code units");
        } else if (MANUAL_SECTION_NUMBERING.matcher(
                section.getTitle()).find()) {
            result.add("CFG-SECTION-TITLE-NUMBERING", path + ".title",
                    "Word section title must not include a manual numbering prefix");
        }
        int level = section.getLevel();
        if (level < 1 || level > 4) {
            result.add("CFG-SECTION-LEVEL", path + ".level",
                    "Word section level must be between 1 and 4");
        }
        if (parentLevel != null && level <= parentLevel.intValue()) {
            result.add("CFG-SECTION-HIERARCHY", path + ".level",
                    "Child section level must be greater than its parent level");
        }
        if (section.getEmptyStrategy() != null
                && !EMPTY_STRATEGIES.contains(section.getEmptyStrategy())) {
            result.add("CFG-EMPTY-STRATEGY", path + ".emptyStrategy",
                    "emptyStrategy must be KEEP, SHOW_EMPTY, or SKIP");
        }
        if (!hasText(section.getEmptyMessage())) {
            result.add("CFG-EMPTY-MESSAGE", path + ".emptyMessage",
                    "Word section emptyMessage must not be blank");
        }

        List<WordComponentDefinition> components = safeList(section.getComponents());
        for (int index = 0; index < components.size(); index++) {
            validateComponent(components.get(index),
                    path + ".components[" + index + "]",
                    tableBindingIds, datasetIds,
                    narrativeIds, chartIds, result);
        }
        List<WordSectionDefinition> children = safeList(section.getChildren());
        for (int index = 0; index < children.size(); index++) {
            validateSection(children.get(index), level, depth + 1,
                    path + ".children[" + index + "]",
                    sectionIds, tableBindingIds, datasetIds,
                    narrativeIds, chartIds,
                    visited, result);
        }
    }

    private void validateComponent(
            WordComponentDefinition component,
            String path,
            Set<String> tableBindingIds,
            Set<String> datasetIds,
            Set<String> narrativeIds,
            Set<String> chartIds,
            ValidationResult result) {
        if (component == null) {
            result.add("CFG-COMPONENT", path, "Word component must not be null");
            return;
        }
        String type = component.getType();
        if (!COMPONENT_TYPES.contains(type)) {
            result.add("CFG-COMPONENT-TYPE", path + ".type",
                    "Unknown word component type: " + type);
            return;
        }
        if (TEXT_COMPONENT_TYPES.contains(type) && !hasText(component.getText())) {
            result.add("CFG-COMPONENT-REFERENCE", path + ".text",
                    type + " requires non-blank text");
        } else if ("RULE_TEXT".equals(type)
                && (!hasText(component.getNarrativeId())
                || !narrativeIds.contains(component.getNarrativeId()))) {
            result.add("CFG-COMPONENT-REFERENCE", path + ".narrativeId",
                    "RULE_TEXT references an unknown narrative: "
                            + component.getNarrativeId());
        } else if ("CHART".equals(type)
                && (!hasText(component.getChartId())
                || !chartIds.contains(component.getChartId()))) {
            result.add("CFG-COMPONENT-REFERENCE", path + ".chartId",
                    "CHART references an unknown chart: "
                            + component.getChartId());
        } else if ("TABLE".equals(type)) {
            if (hasText(component.getTableId())
                    && !tableBindingIds.contains(component.getTableId())) {
                result.add("CFG-COMPONENT-REFERENCE",
                        path + ".tableId",
                        "TABLE references an unknown Word table binding: "
                                + component.getTableId());
            } else if (!hasText(component.getTableId())
                    && (!hasText(component.getDataset())
                    || !datasetIds.contains(component.getDataset()))) {
                result.add("CFG-COMPONENT-REFERENCE", path + ".dataset",
                        "Generated TABLE references an unknown dataset: "
                                + component.getDataset());
            } else if (hasText(component.getTableId())
                    && hasText(component.getDataset())) {
                result.add("CFG-COMPONENT-REFERENCE", path,
                        "TABLE must reference a binding or an explicit dataset, not both");
            }
            if (!hasText(component.getEmptyMessage())) {
                result.add("CFG-EMPTY-MESSAGE", path + ".emptyMessage",
                        "Word table emptyMessage must not be blank");
            }
            validateWordTableColumns(
                    component.getColumns(), path + ".columns", result);
        }
        if ("CHART".equals(type) && component.getWidthInches() != null
                && component.getWidthInches().doubleValue() <= 0.0d) {
            result.add("CFG-WORD-IMAGE-WIDTH", path + ".widthInches",
                    "Word chart widthInches must be positive");
        }
        if ("CHART".equals(type)
                && !com.xn.report.config.definition.WordImageAlignment
                .supports(component.getAlignment())) {
            result.add("CFG-WORD-IMAGE-ALIGNMENT", path + ".alignment",
                    "Word chart alignment must be LEFT, CENTER, or RIGHT");
        }
        if ("ATTACHMENT".equals(type)
                && !hasText(component.getTitle())
                && !hasText(component.getDescription())
                && !hasAnyText(component.getItems())) {
            result.add("CFG-COMPONENT-REFERENCE", path,
                    "ATTACHMENT requires a title, description, or item");
        }
        for (int index = 0; index < safeList(component.getItems()).size(); index++) {
            if (!hasText(component.getItems().get(index))) {
                result.add("CFG-COMPONENT-REFERENCE",
                        path + ".items[" + index + "]",
                        "Attachment item must not be blank");
            }
        }
    }

    private void requireText(
            ValidationResult result,
            String value,
            String code,
            String path,
            String message) {
        if (!hasText(value)) {
            result.add(code, path, message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean hasAnyText(List<String> values) {
        for (String value : safeList(values)) {
            if (hasText(value)) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
    }

    private static Set<String> unmodifiableSet(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }

    private static final class ChartStackContract {
        private final ChartType type;
        private final com.xn.report.chart.ChartAxis axis;

        private ChartStackContract(
                ChartType type, com.xn.report.chart.ChartAxis axis) {
            this.type = type;
            this.axis = axis == null
                    ? com.xn.report.chart.ChartAxis.PRIMARY : axis;
        }
    }
}
