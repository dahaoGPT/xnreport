package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChartModelBuilderTest {

    private final ChartModelBuilder builder = new ChartModelBuilder();

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
        assertThat(model.getSeries().get(2).getAxis()).isEqualTo(ChartAxis.SECONDARY);
        assertThat(model.getSeries().get(0).getValues())
                .containsExactly(new BigDecimal("2"), new BigDecimal("0"),
                        new BigDecimal("1"));
    }

    @Test
    void splitsGroupsDeterministicallyAndCompletesConfiguredCategories() {
        ChartDefinition definition = TestFixtures.comboChartDefinition();
        definition.setGroupByField("center");
        definition.setCategories(Arrays.asList(
                "2026年1月", "2026年2月", "2026年3月", "2026年4月"));

        DatasetResult rows = DatasetResult.list("centerEvents", Arrays.asList(
                event("B中心", "2026年2月", 4, 2, 10),
                event("A中心", "2026年2月", 1, 3, 11),
                event("A中心", "2026年1月", 2, 2, 12)));

        List<ChartModel> models = builder.buildAll(definition, rows);

        assertThat(models).extracting(ChartModel::getGroupKey)
                .containsExactly("A中心", "B中心");
        assertThat(models.get(0).getCategories()).containsExactly(
                "2026年1月", "2026年2月", "2026年3月", "2026年4月");
        assertThat(models.get(0).getSeries().get(0).getValues())
                .containsExactly(new BigDecimal("2"), new BigDecimal("1"), null, null);
    }

    @Test
    void appliesZeroAndSkipCategoryNullHandlingWithoutMisaligningSeries() {
        ChartDefinition zero = chartWithTwoSeries(
                ChartNullHandling.ZERO, ChartNullHandling.GAP);
        DatasetResult rows = DatasetResult.list("values", Arrays.asList(
                DatasetRow.of("month", "01", "a", null, "b", 5),
                DatasetRow.of("month", "02", "a", 2, "b", null)));

        ChartModel zeroModel = builder.build(zero, rows);

        assertThat(zeroModel.getCategories()).containsExactly("01", "02");
        assertThat(zeroModel.getSeries().get(0).getValues())
                .containsExactly(BigDecimal.ZERO, new BigDecimal("2"));
        assertThat(zeroModel.getSeries().get(1).getValues())
                .containsExactly(new BigDecimal("5"), null);

        ChartDefinition skip = chartWithTwoSeries(
                ChartNullHandling.SKIP_CATEGORY, ChartNullHandling.GAP);
        ChartModel skipped = builder.build(skip, rows);

        assertThat(skipped.getCategories()).containsExactly("02");
        assertThat(skipped.getSeries().get(0).getValues())
                .containsExactly(new BigDecimal("2"));
        assertThat(skipped.getSeries().get(1).getValues()).containsExactly((BigDecimal) null);
    }

    @Test
    void validatesActualRuntimeSchemaAndNumericSeriesValues() {
        ChartDefinition definition = TestFixtures.comboChartDefinition();

        assertThatThrownBy(() -> builder.build(
                definition,
                DatasetResult.list("centerEvents",
                        Collections.singletonList(
                                DatasetRow.of("month", "2026年1月",
                                        "uncertain", 1, "certain", 2)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("baseline");

        assertThatThrownBy(() -> builder.build(
                definition,
                DatasetResult.list("centerEvents",
                        Collections.singletonList(
                                DatasetRow.of("month", "2026年1月",
                                        "uncertain", "not-number",
                                        "certain", 2, "baseline", 3)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("uncertain")
                .hasMessageContaining("numeric");
    }

    @Test
    void modelAndNestedValuesAreDeeplyImmutable() {
        ChartModel model = TestFixtures.comboChartModel();

        assertThatThrownBy(() -> model.getCategories().add("later"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.getSeries().add(model.getSeries().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.getSeries().get(0).getValues().add(BigDecimal.TEN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void keepsEveryDeclaredDynamicTypeInsteadOfDowngradingIt() {
        for (ChartType type : Arrays.asList(
                ChartType.COLUMN,
                ChartType.STACKED_COLUMN,
                ChartType.PERCENT_STACKED_COLUMN,
                ChartType.LINE,
                ChartType.BAR,
                ChartType.STACKED_BAR,
                ChartType.PIE,
                ChartType.DOUGHNUT,
                ChartType.AREA,
                ChartType.STACKED_AREA,
                ChartType.SCATTER,
                ChartType.BUBBLE,
                ChartType.RADAR)) {
            ChartDefinition definition = simpleDefinition(type);
            ChartModel model = builder.build(definition,
                    DatasetResult.list("values", Arrays.asList(
                            DatasetRow.of("category", "1", "value", 2, "size", 4),
                            DatasetRow.of("category", "2", "value", 3, "size", 5))));
            assertThat(model.getSeries().get(0).getType()).isEqualTo(type);
        }
    }

    @Test
    void rejectsAStackGroupThatMixesTypesOrAxes() {
        ChartDefinition mixedType = TestFixtures.comboChartDefinition();
        mixedType.getSeries().get(1).setType(ChartType.STACKED_AREA);
        assertThatThrownBy(() -> builder.build(
                mixedType, TestFixtures.centerEvents()))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("stackGroup")
                .hasMessageContaining("same type");

        ChartDefinition mixedAxis = TestFixtures.comboChartDefinition();
        mixedAxis.getSeries().get(1).setAxis(ChartAxis.SECONDARY);
        assertThatThrownBy(() -> builder.build(
                mixedAxis, TestFixtures.centerEvents()))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("stackGroup")
                .hasMessageContaining("same axis");
    }

    @Test
    void appliesEmptyPolicyBeforeRuntimeSchemaChecks() {
        ChartDefinition output = TestFixtures.comboChartDefinition();
        output.setEmptyDataPolicy(ChartEmptyDataPolicy.OUTPUT_MESSAGE);
        ChartModel empty = builder.build(
                output, DatasetResult.list(
                        "centerEvents", Collections.<DatasetRow>emptyList()));
        assertThat(empty.isEmpty()).isTrue();

        ChartDefinition skip = TestFixtures.comboChartDefinition();
        skip.setEmptyDataPolicy(ChartEmptyDataPolicy.SKIP);
        assertThat(builder.buildAll(skip, DatasetResult.list(
                "centerEvents", Collections.<DatasetRow>emptyList()))).isEmpty();

        ChartDefinition contracted = TestFixtures.comboChartDefinition();
        DatasetSchema incomplete = DatasetSchema.of(
                "month", String.class,
                "uncertain", BigDecimal.class);
        assertThatThrownBy(() -> builder.build(
                contracted,
                DatasetResult.list("centerEvents", incomplete,
                        Collections.<DatasetRow>emptyList())))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("certain");
    }

    @Test
    void generatesOrdinaryPieLabelsFromSeriesValues() {
        for (ChartDataLabelMode mode : Arrays.asList(
                ChartDataLabelMode.COUNT,
                ChartDataLabelMode.PERCENT,
                ChartDataLabelMode.COUNT_AND_PERCENT)) {
            ChartDefinition pie = simpleDefinition(ChartType.PIE);
            pie.setDataLabelMode(mode);
            ChartModel model = builder.build(pie,
                    DatasetResult.list("values", Arrays.asList(
                            DatasetRow.of("category", "A", "value", 1),
                            DatasetRow.of("category", "B", "value", 1),
                            DatasetRow.of("category", "C", "value", 1))));
            if (mode == ChartDataLabelMode.COUNT) {
                assertThat(model.getDataLabels())
                        .containsExactly("A 1", "B 1", "C 1");
            } else if (mode == ChartDataLabelMode.PERCENT) {
                assertThat(model.getDataLabels())
                        .containsExactly("A 33.33%", "B 33.33%", "C 33.33%");
            } else {
                assertThat(model.getDataLabels()).containsExactly(
                        "A 1 (33.33%)", "B 1 (33.33%)", "C 1 (33.33%)");
            }
        }
    }

    @Test
    void ordinaryPieUsesZeroPercentWhenTotalIsZero() {
        ChartDefinition pie = simpleDefinition(ChartType.DOUGHNUT);
        pie.setDataLabelMode(ChartDataLabelMode.COUNT_AND_PERCENT);
        ChartModel model = builder.build(pie,
                DatasetResult.list("values", Arrays.asList(
                        DatasetRow.of("category", "A", "value", 0),
                        DatasetRow.of("category", "B", "value", 0))));

        assertThat(model.getDataLabels())
                .containsExactly("A 0 (0.00%)", "B 0 (0.00%)");
    }

    @Test
    void stockRequiresTemplateNativeMode() {
        ChartDefinition stock = simpleDefinition(ChartType.STOCK);
        stock.setMode(ChartDefinition.Mode.GENERATED_NATIVE);
        assertThatThrownBy(() -> builder.build(
                stock, DatasetResult.list("values",
                        Collections.singletonList(
                                DatasetRow.of("category", "1", "value", 2)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("TEMPLATE_NATIVE");

        stock.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
        assertThat(builder.build(
                stock, DatasetResult.list("values",
                        Collections.singletonList(
                                DatasetRow.of("category", "1", "value", 2)))
        ).getSeries().get(0).getType()).isEqualTo(ChartType.STOCK);
    }

    @Test
    void scatterDefaultsToVisibleMarkersAndRejectsExplicitFalse() {
        ChartDefinition scatter = simpleDefinition(ChartType.SCATTER);
        ChartModel model = builder.build(scatter,
                DatasetResult.list("values",
                        Collections.singletonList(
                                DatasetRow.of(
                                        "category", "1",
                                        "value", 2,
                                        "size", 1))));
        assertThat(model.getSeries().get(0).isMarker()).isTrue();

        scatter.getSeries().get(0).setMarker(false);
        assertThatThrownBy(() -> builder.build(
                scatter, DatasetResult.list("values",
                        Collections.singletonList(
                                DatasetRow.of(
                                        "category", "1",
                                        "value", 2,
                                        "size", 1)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("visible marker");
    }

    @Test
    void outputMessageEmptyDataIgnoresConfiguredCategories() {
        ChartDefinition definition = TestFixtures.comboChartDefinition();
        definition.setCategories(Arrays.asList(
                "2026年1月", "2026年2月"));
        definition.setEmptyDataPolicy(ChartEmptyDataPolicy.OUTPUT_MESSAGE);

        ChartModel model = builder.build(
                definition,
                DatasetResult.list(
                        "centerEvents", Collections.<DatasetRow>emptyList()));

        assertThat(model.isEmpty()).isTrue();
        assertThat(model.getCategories()).isEmpty();
        assertThat(model.getSeries())
                .allSatisfy(series -> assertThat(series.getValues()).isEmpty());
    }

    @Test
    void preservesNullAndEmptyKeysAndRejectsAmbiguousTypedLabels() {
        ChartDefinition grouped = simpleDefinition(ChartType.LINE);
        grouped.setGroupByField("group");
        List<ChartModel> models = builder.buildAll(grouped,
                DatasetResult.list("values", Arrays.asList(
                        DatasetRow.of("group", null, "category", null, "value", 1),
                        DatasetRow.of("group", "", "category", "", "value", 2))));

        assertThat(models).hasSize(2);
        assertThat(models).extracting(ChartModel::getGroupKey)
                .containsExactly("", "<null>");
        assertThat(models).extracting(model -> model.getCategories().get(0))
                .containsExactly("", "<null>");

        ChartDefinition ungrouped = simpleDefinition(ChartType.LINE);
        assertThatThrownBy(() -> builder.build(ungrouped,
                DatasetResult.list("values", Arrays.asList(
                        DatasetRow.of("category", 1, "value", 1),
                        DatasetRow.of("category", "1", "value", 2)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("display label collision");
    }

    @Test
    void rejectsMultipleStackGroupsForTheSameTypeAndAxis() {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("overlap");
        definition.setDataset("values");
        definition.setCategoryField("category");
        ChartSeriesDefinition first = series(
                "a", "A", ChartType.STACKED_COLUMN, ChartNullHandling.GAP);
        first.setStackGroup("first");
        ChartSeriesDefinition second = series(
                "b", "B", ChartType.STACKED_COLUMN, ChartNullHandling.GAP);
        second.setStackGroup("second");
        definition.setSeries(Arrays.asList(first, second));

        assertThatThrownBy(() -> builder.build(definition,
                DatasetResult.list("values", Collections.singletonList(
                        DatasetRow.of("category", "x", "a", 1, "b", 2)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("multiple stackGroup");
    }

    @Test
    void rejectsMixedColumnStackSlotsAndPercentSeriesSharingANumericAxis() {
        ChartDefinition mixed = new ChartDefinition();
        mixed.setId("mixed-stack-slot");
        mixed.setDataset("values");
        mixed.setCategoryField("category");
        ChartSeriesDefinition ordinary = series(
                "a", "A", ChartType.STACKED_COLUMN, ChartNullHandling.GAP);
        ordinary.setStackGroup("ordinary");
        ChartSeriesDefinition percent = series(
                "b", "B", ChartType.PERCENT_STACKED_COLUMN,
                ChartNullHandling.GAP);
        percent.setStackGroup("percent");
        mixed.setSeries(Arrays.asList(ordinary, percent));
        DatasetResult rows = DatasetResult.list("values",
                Collections.singletonList(
                        DatasetRow.of("category", "x", "a", 1, "b", 2)));

        assertThatThrownBy(() -> builder.build(mixed, rows))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("stack slot");

        ChartDefinition percentAndLine = new ChartDefinition();
        percentAndLine.setId("percent-axis");
        percentAndLine.setDataset("values");
        percentAndLine.setCategoryField("category");
        ChartSeriesDefinition percentOnly = series(
                "a", "Percent", ChartType.PERCENT_STACKED_COLUMN,
                ChartNullHandling.GAP);
        percentOnly.setStackGroup("percent");
        ChartSeriesDefinition line = series(
                "b", "Hours", ChartType.LINE, ChartNullHandling.GAP);
        percentAndLine.setSeries(Arrays.asList(percentOnly, line));

        assertThatThrownBy(() -> builder.build(percentAndLine, rows))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("percent axis");
    }

    @Test
    void chartDataLabelsDefaultOnlySeriesThatOmitTheProperty() {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("labels");
        definition.setDataset("values");
        definition.setCategoryField("category");
        definition.setDataLabelMode(ChartDataLabelMode.VALUE);
        ChartSeriesDefinition inherited = series(
                "a", "Inherited", ChartType.LINE, ChartNullHandling.GAP);
        inherited.setFormat("0.0");
        ChartSeriesDefinition disabled = series(
                "b", "Disabled", ChartType.LINE, ChartNullHandling.GAP);
        disabled.setDataLabels(ChartDataLabelMode.NONE);
        definition.setSeries(Arrays.asList(inherited, disabled));

        ChartModel model = builder.build(definition,
                DatasetResult.list("values", Collections.singletonList(
                        DatasetRow.of("category", "x", "a", 1, "b", 2))));

        assertThat(model.getSeries()).extracting(
                ChartSeriesModel::getDataLabelMode)
                .containsExactly(ChartDataLabelMode.VALUE,
                        ChartDataLabelMode.NONE);
    }

    @Test
    void rejectsNegativePieValuesWithoutLabelsAndNonPositiveBubbleSizes() {
        ChartDefinition pie = simpleDefinition(ChartType.PIE);
        assertThatThrownBy(() -> builder.build(pie,
                DatasetResult.list("values", Collections.singletonList(
                        DatasetRow.of("category", "x", "value", -1)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("must not be negative");

        ChartDefinition bubble = simpleDefinition(ChartType.BUBBLE);
        assertThatThrownBy(() -> builder.build(bubble,
                DatasetResult.list("values", Collections.singletonList(
                        DatasetRow.of("category", "1", "value", 2, "size", 0)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsOversizedAndNonFiniteChartModelsBeforeRendering() {
        ChartDefinition tooManySeries = simpleDefinition(ChartType.LINE);
        List<ChartSeriesDefinition> series = new java.util.ArrayList<>();
        for (int index = 0; index <= ChartModelBuilder.MAX_SERIES; index++) {
            series.add(series(
                    "value", "S" + index, ChartType.LINE,
                    ChartNullHandling.GAP));
        }
        tooManySeries.setSeries(series);
        assertThatThrownBy(() -> builder.build(tooManySeries,
                DatasetResult.list("values", Collections.singletonList(
                        DatasetRow.of("category", "x", "value", 1)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("MAX_SERIES");

        ChartDefinition nonFinite = simpleDefinition(ChartType.LINE);
        assertThatThrownBy(() -> builder.build(nonFinite,
                DatasetResult.list("values", Collections.singletonList(
                        DatasetRow.of("category", "x", "value",
                                new BigDecimal("1E100000"))))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("finite double");
    }

    @Test
    void maxPointsCountsConfiguredCategoriesAcrossEveryOutputGroup() {
        ChartDefinition definition = simpleDefinition(ChartType.LINE);
        definition.setGroupByField("group");
        List<String> categories = new java.util.ArrayList<>();
        for (int index = 0; index < 2000; index++) {
            categories.add("C" + index);
        }
        definition.setCategories(categories);
        List<ChartSeriesDefinition> series = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            series.add(series("value", "S" + index, ChartType.LINE,
                    ChartNullHandling.GAP));
        }
        definition.setSeries(series);

        assertThatThrownBy(() -> builder.buildAll(definition,
                DatasetResult.list("values", Arrays.asList(
                        DatasetRow.of("group", "A", "category", "C0",
                                "value", 1),
                        DatasetRow.of("group", "B", "category", "C0",
                                "value", 2)))))
                .isInstanceOf(ChartBuildException.class)
                .hasMessageContaining("MAX_POINTS");
    }

    private static DatasetRow event(
            String center, String month, int uncertain, int certain, int baseline) {
        return DatasetRow.of(
                "center", center,
                "month", month,
                "uncertain", uncertain,
                "certain", certain,
                "baseline", baseline);
    }

    private static ChartDefinition chartWithTwoSeries(
            ChartNullHandling first, ChartNullHandling second) {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("nulls");
        definition.setTitle("Null values");
        definition.setDataset("values");
        definition.setCategoryField("month");
        definition.setSeries(Arrays.asList(
                series("a", "A", ChartType.LINE, first),
                series("b", "B", ChartType.LINE, second)));
        return definition;
    }

    private static ChartSeriesDefinition series(
            String field, String name, ChartType type, ChartNullHandling nullHandling) {
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField(field);
        series.setName(name);
        series.setType(type);
        series.setNullHandling(nullHandling);
        return series;
    }

    private static ChartDefinition simpleDefinition(ChartType type) {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("chart-" + type.name());
        definition.setTitle(type.name());
        definition.setDataset("values");
        definition.setCategoryField("category");
        ChartSeriesDefinition series = series(
                "value", "Value", type, ChartNullHandling.GAP);
        if (type.isStacked()) {
            series.setStackGroup("values");
        }
        if (type == ChartType.BUBBLE) {
            series.setSizeField("size");
        }
        definition.setSeries(Collections.singletonList(series));
        return definition;
    }
}
