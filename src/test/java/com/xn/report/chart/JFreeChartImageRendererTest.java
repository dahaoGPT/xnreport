package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.support.TestFixtures;
import com.xn.report.text.DistributionAnalyzer;
import com.xn.report.text.DistributionResult;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBubbleRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

class JFreeChartImageRendererTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersReadableHeadlessPngAtExactConfiguredSize() throws Exception {
        JFreeChartImageRenderer renderer = new JFreeChartImageRenderer(
                temporaryDirectory,
                Arrays.asList("Definitely Missing", "Dialog"));

        RenderedChart rendered = renderer.render(
                TestFixtures.comboChartModel(),
                new ChartRenderOptions(1600, 850, 180));

        BufferedImage image = ImageIO.read(rendered.getPath().toFile());
        assertThat(rendered.getMediaType()).isEqualTo("image/png");
        assertThat(rendered.getDpi()).isEqualTo(180);
        assertThat(image.getWidth()).isEqualTo(1600);
        assertThat(image.getHeight()).isEqualTo(850);
        assertThat(Files.size(rendered.getPath())).isGreaterThan(1000L);
    }

    @Test
    void createsPieLabelsFromTheSameDistributionResult() {
        DistributionDefinition distribution = TestFixtures.durationDistribution();
        DistributionResult result = new DistributionAnalyzer().analyze(
                Arrays.asList(
                        DatasetRow.of("hours", 12),
                        DatasetRow.of("hours", 30),
                        DatasetRow.of("hours", 200)),
                distribution,
                NarrativeDefinition.EmptyStrategy.FAIL);
        ChartDefinition chart = new ChartDefinition();
        chart.setId("duration");
        chart.setTitle("审批时长区间分布");
        chart.setDataset("durationRows");
        chart.setCategoryField("label");
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField("count");
        series.setName("人数");
        series.setType(ChartType.PIE);
        chart.setSeries(Collections.singletonList(series));
        chart.setDataLabelMode(ChartDataLabelMode.COUNT);

        ChartModel model = new ChartModelBuilder().buildDistribution(chart, result);

        assertThat(model.getCategories()).hasSize(3);
        assertThat(model.getDataLabels())
                .containsExactly("1天之内 1 (33.33%)",
                        "7天之内 1 (33.33%)", "7天以上 1 (33.33%)");
        assertThat(model.getSeries().get(0).getValues())
                .extracting(Number::intValue).containsExactly(1, 1, 1);
    }

    @Test
    void rendersEveryAdvertisedWordChartTypeWithoutSilentFallback() throws Exception {
        JFreeChartImageRenderer renderer = new JFreeChartImageRenderer(
                temporaryDirectory, Collections.singletonList("Dialog"));
        for (ChartType type : ChartType.values()) {
            if (type == ChartType.STOCK) {
                continue;
            }
            ChartModel model = model(type);
            assertThat(renderer.supports(model)).as(type.name()).isTrue();
            RenderedChart rendered = renderer.render(
                    model, new ChartRenderOptions(480, 320, 96));
            assertThat(ImageIO.read(rendered.getPath().toFile()))
                    .as(type.name()).isNotNull();
        }
    }

    @Test
    void rejectsTemplateOnlyStockInsteadOfChangingItsType() {
        JFreeChartImageRenderer renderer = new JFreeChartImageRenderer(
                temporaryDirectory, Collections.singletonList("Dialog"));
        ChartModel stock = model(ChartType.STOCK);

        assertThat(renderer.supports(stock)).isFalse();
        assertThatThrownBy(() -> renderer.render(
                stock, new ChartRenderOptions(480, 320, 96)))
                .isInstanceOf(UnsupportedChartTypeException.class)
                .hasMessageContaining("STOCK");
    }

    @Test
    void appliesScatterAndBubbleMarkersLabelsFormatsAndColors() {
        JFreeChartImageRenderer renderer = new JFreeChartImageRenderer(
                temporaryDirectory, Collections.singletonList("Dialog"));

        ChartDefinition scatterDefinition = definition(ChartType.SCATTER);
        ChartSeriesDefinition scatterSeries = scatterDefinition.getSeries().get(0);
        scatterSeries.setMarker(false);
        scatterSeries.setDataLabels(ChartDataLabelMode.VALUE);
        scatterSeries.setFormat("0.0");
        scatterSeries.setColor("#112233");
        JFreeChart scatterChart = renderer.createChart(
                new ChartModelBuilder().build(scatterDefinition, xyRows()));
        XYLineAndShapeRenderer scatter = (XYLineAndShapeRenderer)
                ((XYPlot) scatterChart.getPlot()).getRenderer(0);
        assertThat(scatter.getSeriesShapesVisible(0)).isFalse();
        assertThat(scatter.getSeriesItemLabelGenerator(0)).isNotNull();
        assertThat(scatter.isSeriesItemLabelsVisible(0)).isTrue();
        assertThat(scatter.getSeriesPaint(0))
                .isEqualTo(new java.awt.Color(0x11, 0x22, 0x33));

        ChartDefinition bubbleDefinition = definition(ChartType.BUBBLE);
        ChartSeriesDefinition bubbleSeries = bubbleDefinition.getSeries().get(0);
        bubbleSeries.setDataLabels(ChartDataLabelMode.VALUE);
        bubbleSeries.setFormat("0.00");
        JFreeChart bubbleChart = renderer.createChart(
                new ChartModelBuilder().build(bubbleDefinition, xyRows()));
        XYBubbleRenderer bubble = (XYBubbleRenderer)
                ((XYPlot) bubbleChart.getPlot()).getRenderer(0);
        assertThat(bubble.getSeriesItemLabelGenerator(0)).isNotNull();
        assertThat(bubble.isSeriesItemLabelsVisible(0)).isTrue();
    }

    @Test
    void appliesRadarColorAndLineStroke() {
        JFreeChartImageRenderer renderer = new JFreeChartImageRenderer(
                temporaryDirectory, Collections.singletonList("Dialog"));
        ChartDefinition definition = definition(ChartType.RADAR);
        ChartSeriesDefinition series = definition.getSeries().get(0);
        series.setColor("#AABBCC");
        series.setLineStyle(ChartLineStyle.DASHED);
        series.setLineWidth(new java.math.BigDecimal("3"));

        SpiderWebPlot plot = (SpiderWebPlot) renderer.createChart(
                new ChartModelBuilder().build(definition, xyRows())).getPlot();

        assertThat(plot.getSeriesPaint(0))
                .isEqualTo(new java.awt.Color(0xAA, 0xBB, 0xCC));
        java.awt.BasicStroke stroke =
                (java.awt.BasicStroke) plot.getSeriesOutlineStroke(0);
        assertThat(stroke.getLineWidth()).isEqualTo(3F);
        assertThat(stroke.getDashArray()).isNotEmpty();
    }

    @Test
    void preservesGlobalLegendOrderAcrossInterleavedRendererGroups() {
        JFreeChartImageRenderer renderer = new JFreeChartImageRenderer(
                temporaryDirectory, Collections.singletonList("Dialog"));
        ChartDefinition definition = new ChartDefinition();
        definition.setId("legend");
        definition.setTitle("Legend");
        definition.setDataset("render");
        definition.setCategoryField("x");
        definition.setSeries(Arrays.asList(
                series("a", "Line first", ChartType.LINE, 0),
                series("b", "Column second", ChartType.COLUMN, 1),
                series("c", "Line third", ChartType.LINE, 2)));
        DatasetResult rows = DatasetResult.list("render", Arrays.asList(
                DatasetRow.of("x", "1", "a", 1, "b", 2, "c", 3),
                DatasetRow.of("x", "2", "a", 2, "b", 3, "c", 4)));

        CategoryPlot plot = (CategoryPlot) renderer.createChart(
                new ChartModelBuilder().build(definition, rows)).getPlot();
        LegendItemCollection items = plot.getLegendItems();

        assertThat(items.get(0).getLabel()).isEqualTo("Line first");
        assertThat(items.get(1).getLabel()).isEqualTo("Column second");
        assertThat(items.get(2).getLabel()).isEqualTo("Line third");
    }

    private static ChartSeriesDefinition series(
            String field, String name, ChartType type, int order) {
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField(field);
        series.setName(name);
        series.setType(type);
        series.setLegendOrder(order);
        return series;
    }

    private static ChartDefinition definition(ChartType type) {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("properties-" + type);
        definition.setTitle(type.name());
        definition.setDataset("render");
        definition.setCategoryField("x");
        if (type == ChartType.STOCK) {
            definition.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
        }
        ChartSeriesDefinition series =
                series("y", "Series", type, 0);
        if (type == ChartType.BUBBLE) {
            series.setSizeField("size");
        }
        definition.setSeries(Collections.singletonList(series));
        return definition;
    }

    private static DatasetResult xyRows() {
        return DatasetResult.list("render", Arrays.asList(
                DatasetRow.of("x", "1", "y", 2, "size", 3),
                DatasetRow.of("x", "2", "y", 4, "size", 5)));
    }

    private static ChartModel model(ChartType type) {
        ChartDefinition definition = new ChartDefinition();
        definition.setId("render-" + type);
        definition.setTitle(type.name());
        definition.setDataset("render");
        definition.setCategoryField("x");
        if (type == ChartType.STOCK) {
            definition.setMode(ChartDefinition.Mode.TEMPLATE_NATIVE);
        }
        ChartSeriesDefinition series = new ChartSeriesDefinition();
        series.setField("y");
        series.setName("数据");
        series.setType(type);
        if (type.isStacked()) {
            series.setStackGroup("values");
        }
        if (type == ChartType.BUBBLE) {
            series.setSizeField("size");
        }
        definition.setSeries(Collections.singletonList(series));
        DatasetResult data = DatasetResult.list("render", Arrays.asList(
                DatasetRow.of("x", "1", "y", 2, "size", 3),
                DatasetRow.of("x", "2", "y", 4, "size", 5),
                DatasetRow.of("x", "3", "y", 3, "size", 4)));
        return new ChartModelBuilder().build(definition, data);
    }
}
