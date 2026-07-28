package com.xn.report.support;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportMetadata;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.ConditionDefinition;
import com.xn.report.config.definition.RuleDefinition;
import com.xn.report.config.definition.RuleDefinition.ResultDefinition;
import com.xn.report.config.definition.RuleDefinition.SummaryDefinition;
import com.xn.report.config.definition.SortFieldDefinition;
import com.xn.report.config.definition.ValueReferenceDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetType;
import com.xn.report.chart.ChartAxis;
import com.xn.report.chart.ChartDataLabelMode;
import com.xn.report.chart.ChartModel;
import com.xn.report.chart.ChartModelBuilder;
import com.xn.report.chart.ChartNullHandling;
import com.xn.report.chart.ChartType;
import com.xn.report.rule.ComparisonCondition;
import com.xn.report.rule.ComparisonOperator;
import com.xn.report.rule.ConditionNode;
import com.xn.report.rule.LogicalCondition;
import com.xn.report.rule.RuleEvaluationContext;
import com.xn.report.rule.ValueReference;
import com.xn.report.transform.Direction;
import com.xn.report.transform.NullOrder;
import com.xn.report.text.TextRenderContext;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        dataset.setSheetName("Sheet-" + id);
        dataset.setSqlFile(sqlFile);
        dataset.setSql(sql);
        dataset.setResultType(DatasetType.LIST);
        dataset.setDependsOn(Arrays.asList(dependsOn));
        return dataset;
    }

    public static DatasetRow row(Object... pairs) {
        return DatasetRow.of(pairs);
    }

    public static DatasetRow person(String name, String avgHours) {
        return DatasetRow.of(
                "personName", name,
                "avgHours", new BigDecimal(avgHours));
    }

    public static DatasetResult people(DatasetRow... rows) {
        return DatasetResult.list("people", Arrays.asList(rows));
    }

    public static Map<String, Object> parameters(Object... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be pairs");
        }
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return values;
    }

    public static ConditionNode and(ConditionNode... children) {
        return LogicalCondition.and(Arrays.asList(children));
    }

    public static ConditionNode or(ConditionNode... children) {
        return LogicalCondition.or(Arrays.asList(children));
    }

    public static ConditionNode compare(
            ValueReference left,
            ComparisonOperator operator,
            ValueReference right) {
        return new ComparisonCondition(left, operator, right);
    }

    public static ValueReference literal(Object value) {
        return ValueReference.literal(value);
    }

    public static ValueReference field(String field) {
        return ValueReference.currentField(field);
    }

    public static ValueReference datasetField(String dataset, String field) {
        return ValueReference.datasetField(dataset, field);
    }

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
        return new RuleEvaluationContext(
                datasets, Collections.<String, Object>emptyMap());
    }

    public static DatasetResult pipelineRows() {
        return DatasetResult.list("pipeline", Arrays.asList(
                DatasetRow.of("name", "A", "group", "X",
                        "hours", new BigDecimal("11")),
                DatasetRow.of("name", "A", "group", "X",
                        "hours", new BigDecimal("11")),
                DatasetRow.of("name", "B", "group", "Y",
                        "hours", new BigDecimal("12")),
                DatasetRow.of("name", "C", "group", "X",
                        "hours", new BigDecimal("9"))));
    }

    public static RuleDefinition pipelineRule() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId("pipeline");
        rule.setDataset("pipeline");

        ConditionDefinition condition = new ConditionDefinition();
        condition.setOperator(ConditionDefinition.Operator.GT);
        condition.setLeft(currentField("hours"));
        condition.setRight(literalValue(new BigDecimal("10")));
        rule.setCondition(condition);

        SortFieldDefinition sort = new SortFieldDefinition();
        sort.setField("hours");
        sort.setDirection(Direction.DESC);
        sort.setNullOrder(NullOrder.LAST);

        SummaryDefinition maximum = new SummaryDefinition();
        maximum.setName("maxHours");
        maximum.setField("hours");
        maximum.setOperation(SummaryDefinition.Operation.MAX);

        ResultDefinition result = new ResultDefinition();
        result.setDistinctFields(Collections.singletonList("name"));
        result.setSort(Collections.singletonList(sort));
        result.setGroupByFields(Collections.singletonList("group"));
        result.setMaxItems(2);
        result.setSummaries(Collections.singletonList(maximum));
        rule.setResult(result);
        return rule;
    }

    public static TextRenderContext textContext() {
        DatasetContext datasets = DatasetContext.builder()
                .put(DatasetResult.single(
                        "baseline",
                        Collections.singletonList(DatasetRow.of(
                                "standardHours", new BigDecimal("10.00")))))
                .build();
        return TextRenderContext.builder()
                .currentRow(DatasetRow.of(
                        "personName", "张三",
                        "avgHours", new BigDecimal("12.50")))
                .summary(parameters("matchedCount", 2))
                .runtime(parameters("period", "2026H1"))
                .datasets(datasets)
                .build();
    }

    public static ChartDefinition comboChartDefinition() {
        ChartDefinition chart = new ChartDefinition();
        chart.setId("centerEventChart");
        chart.setTitle("中心事件数");
        chart.setDataset("centerEvents");
        chart.setCategoryField("month");
        chart.setSeries(Arrays.asList(
                chartSeries("uncertain", "不定责事件",
                        ChartType.STACKED_COLUMN, "event", ChartAxis.PRIMARY),
                chartSeries("certain", "定责事件",
                        ChartType.STACKED_COLUMN, "event", ChartAxis.PRIMARY),
                chartSeries("baseline", "中心基准值",
                        ChartType.LINE, null, ChartAxis.SECONDARY)));
        return chart;
    }

    public static DatasetResult centerEvents() {
        return DatasetResult.list("centerEvents", Arrays.asList(
                DatasetRow.of("month", "2026年3月",
                        "uncertain", 1, "certain", 2, "baseline", 10),
                DatasetRow.of("month", "2026年1月",
                        "uncertain", 2, "certain", 2, "baseline", 12),
                DatasetRow.of("month", "2026年2月",
                        "uncertain", 0, "certain", 1, "baseline", 11)));
    }

    public static ChartModel comboChartModel() {
        return new ChartModelBuilder().build(
                comboChartDefinition(), centerEvents());
    }

    public static DistributionDefinition durationDistribution() {
        DistributionDefinition definition = new DistributionDefinition();
        definition.setField("hours");
        definition.setLabelMode(
                DistributionDefinition.LabelMode.COUNT_AND_PERCENT);
        definition.setBins(Arrays.asList(
                bin("one-day", "1天之内", null, false,
                        new BigDecimal("24"), true),
                bin("seven-days", "7天之内", new BigDecimal("24"), false,
                        new BigDecimal("168"), true),
                bin("over-seven-days", "7天以上",
                        new BigDecimal("168"), false, null, false)));
        return definition;
    }

    private static ChartSeriesDefinition chartSeries(
            String field,
            String name,
            ChartType type,
            String stackGroup,
            ChartAxis axis) {
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField(field);
        series.setName(name);
        series.setType(type);
        if (stackGroup != null) {
            series.setStackGroup(stackGroup);
        }
        series.setAxis(axis);
        series.setNullHandling(ChartNullHandling.GAP);
        series.setDataLabels(ChartDataLabelMode.VALUE);
        return series;
    }

    private static DistributionDefinition.BinDefinition bin(
            String id,
            String label,
            BigDecimal min,
            boolean minInclusive,
            BigDecimal max,
            boolean maxInclusive) {
        DistributionDefinition.BinDefinition bin =
                new DistributionDefinition.BinDefinition();
        bin.setId(id);
        bin.setLabel(label);
        if (min != null) {
            bin.setMin(min);
            bin.setMinInclusive(minInclusive);
        }
        if (max != null) {
            bin.setMax(max);
            bin.setMaxInclusive(maxInclusive);
        }
        return bin;
    }

    private static ValueReferenceDefinition currentField(String field) {
        ValueReferenceDefinition reference = new ValueReferenceDefinition();
        reference.setSource(ValueReferenceDefinition.Source.CURRENT_FIELD);
        reference.setField(field);
        return reference;
    }

    private static ValueReferenceDefinition literalValue(Object value) {
        ValueReferenceDefinition reference = new ValueReferenceDefinition();
        reference.setSource(ValueReferenceDefinition.Source.LITERAL);
        reference.setValue(value);
        return reference;
    }
}
