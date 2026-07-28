package com.xn.report.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardXYItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.AbstractCategoryItemRenderer;
import org.jfree.chart.renderer.category.AreaRenderer;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.chart.renderer.xy.XYBubbleRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public final class JFreeChartImageRenderer implements ChartImageRenderer {

    private static final Set<ChartType> SUPPORTED =
            Collections.unmodifiableSet(EnumSet.of(
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
                    ChartType.RADAR));
    private static final List<Color> DEFAULT_COLORS = Arrays.asList(
            new Color(91, 155, 213),
            new Color(237, 125, 49),
            new Color(165, 165, 165),
            new Color(255, 192, 0),
            new Color(68, 114, 196),
            new Color(112, 173, 71));

    private final Path outputDirectory;
    private final String fontFamily;

    public JFreeChartImageRenderer(
            Path outputDirectory, List<String> fontCandidates) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Chart output directory is required");
        }
        this.outputDirectory = outputDirectory.toAbsolutePath().normalize();
        this.fontFamily = chooseFont(fontCandidates);
    }

    @Override
    public boolean supports(ChartModel model) {
        if (model == null || model.getSeries().isEmpty()) {
            return false;
        }
        for (ChartSeriesModel series : model.getSeries()) {
            if (!SUPPORTED.contains(series.getType())) {
                return false;
            }
        }
        return compatibleFamily(model);
    }

    @Override
    public RenderedChart render(
            ChartModel model, ChartRenderOptions options) {
        if (model == null || options == null) {
            throw new IllegalArgumentException(
                    "Chart model and render options are required");
        }
        if (!supports(model)) {
            ChartType type = model.getSeries().isEmpty()
                    ? ChartType.STOCK : model.getSeries().get(0).getType();
            throw new UnsupportedChartTypeException(type);
        }
        try {
            Files.createDirectories(outputDirectory);
            JFreeChart chart = createChart(model);
            applyFont(chart, new Font(fontFamily, Font.PLAIN, 18));
            BufferedImage image = new BufferedImage(
                    options.getWidthPixels(),
                    options.getHeightPixels(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                chart.draw(graphics,
                        new java.awt.geom.Rectangle2D.Double(
                                0, 0,
                                options.getWidthPixels(),
                                options.getHeightPixels()));
            } finally {
                graphics.dispose();
            }
            Path output = outputDirectory.resolve(
                    safeFileName(model.getChartId())
                            + "-" + UUID.randomUUID() + ".png");
            if (!ImageIO.write(image, "png", output.toFile())) {
                throw new IllegalStateException("No PNG image writer is available");
            }
            return new RenderedChart(
                    output, "image/png",
                    options.getWidthPixels(),
                    options.getHeightPixels(),
                    options.getDpi());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot render chart " + model.getChartId(), exception);
        }
    }

    JFreeChart createChart(ChartModel model) {
        if (model.isEmpty()) {
            return emptyChart(model);
        }
        ChartType first = model.getSeries().get(0).getType();
        if (first.isPieLike()) {
            return pieChart(model, first == ChartType.DOUGHNUT);
        }
        if (first == ChartType.RADAR) {
            return radarChart(model);
        }
        if (first == ChartType.SCATTER || first == ChartType.BUBBLE) {
            return xyChart(model);
        }
        return categoryChart(model);
    }

    private JFreeChart emptyChart(ChartModel model) {
        JFreeChart chart = ChartFactory.createBarChart(
                model.getTitle(), "", "", new DefaultCategoryDataset());
        chart.getCategoryPlot().setNoDataMessage(model.getEmptyMessage());
        return chart;
    }

    private JFreeChart categoryChart(ChartModel model) {
        CategoryAxis domain = new CategoryAxis("");
        NumberAxis primary = numberAxis(
                model.getPrimaryAxisMin(), model.getPrimaryAxisMax(),
                axisUsesPercent(model, ChartAxis.PRIMARY));
        CategoryPlot plot = new CategoryPlot(
                null, domain, primary, null);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(220, 220, 220));
        plot.setInsets(new RectangleInsets(8, 8, 8, 8));
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

        boolean horizontal = allHorizontal(model);
        plot.setOrientation(horizontal
                ? PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL);
        boolean needsSecondary = false;
        for (ChartSeriesModel series : model.getSeries()) {
            needsSecondary |= series.getAxis() == ChartAxis.SECONDARY;
        }
        if (needsSecondary) {
            plot.setRangeAxis(1, numberAxis(
                    model.getSecondaryAxisMin(),
                    model.getSecondaryAxisMax(),
                    axisUsesPercent(model, ChartAxis.SECONDARY)));
        }

        Map<GroupKey, List<ChartSeriesModel>> groups =
                categoryGroups(model.getSeries());
        int datasetIndex = 0;
        for (Map.Entry<GroupKey, List<ChartSeriesModel>> entry
                : groups.entrySet()) {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (ChartSeriesModel series : entry.getValue()) {
                for (int category = 0;
                        category < model.getCategories().size(); category++) {
                    dataset.addValue(
                            series.getValues().get(category),
                            series.getName(),
                            model.getCategories().get(category));
                }
            }
            AbstractCategoryItemRenderer renderer =
                    categoryRenderer(entry.getKey().type);
            configureCategoryRenderer(
                    renderer, entry.getValue(), model.getSeries());
            plot.setDataset(datasetIndex, dataset);
            plot.setRenderer(datasetIndex, renderer);
            if (entry.getKey().axis == ChartAxis.SECONDARY) {
                plot.mapDatasetToRangeAxis(datasetIndex, 1);
            }
            datasetIndex++;
        }
        plot.setFixedLegendItems(legendItems(model));
        return finish(model, plot);
    }

    private JFreeChart xyChart(ChartModel model) {
        NumberAxis xAxis = new NumberAxis("");
        NumberAxis yAxis = numberAxis(
                model.getPrimaryAxisMin(), model.getPrimaryAxisMax(), false);
        XYPlot plot = new XYPlot(null, xAxis, yAxis, null);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(220, 220, 220));
        for (int seriesIndex = 0;
                seriesIndex < model.getSeries().size(); seriesIndex++) {
            ChartSeriesModel series = model.getSeries().get(seriesIndex);
            if (series.getType() == ChartType.BUBBLE) {
                DefaultXYZDataset data = new DefaultXYZDataset();
                double[][] points = new double[3][model.getCategories().size()];
                for (int point = 0; point < model.getCategories().size(); point++) {
                    points[0][point] = x(model.getCategories().get(point), point);
                    points[1][point] = doubleValue(series.getValues().get(point));
                    points[2][point] = doubleValue(series.getSizes().get(point));
                }
                data.addSeries(series.getName(), points);
                XYBubbleRenderer renderer =
                        new XYBubbleRenderer(XYBubbleRenderer.SCALE_ON_RANGE_AXIS);
                renderer.setSeriesPaint(0, color(series, seriesIndex));
                configureXYLabels(renderer, series);
                plot.setDataset(seriesIndex, data);
                plot.setRenderer(seriesIndex, renderer);
            } else {
                XYSeries points = new XYSeries(series.getName(), false, false);
                for (int point = 0; point < model.getCategories().size(); point++) {
                    BigDecimal value = series.getValues().get(point);
                    if (value != null) {
                        points.add(x(model.getCategories().get(point), point), value);
                    }
                }
                XYSeriesCollection data = new XYSeriesCollection(points);
                XYLineAndShapeRenderer renderer =
                        new XYLineAndShapeRenderer(false, true);
                renderer.setSeriesPaint(0, color(series, seriesIndex));
                renderer.setSeriesShapesVisible(0, series.isMarker());
                configureXYLabels(renderer, series);
                plot.setDataset(seriesIndex, data);
                plot.setRenderer(seriesIndex, renderer);
            }
            if (series.getAxis() == ChartAxis.SECONDARY) {
                if (plot.getRangeAxis(1) == null) {
                    plot.setRangeAxis(1, numberAxis(
                            model.getSecondaryAxisMin(),
                            model.getSecondaryAxisMax(), false));
                }
                plot.mapDatasetToRangeAxis(seriesIndex, 1);
            }
        }
        plot.setFixedLegendItems(legendItems(model));
        return finish(model, plot);
    }

    private JFreeChart pieChart(ChartModel model, boolean ring) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<String>();
        ChartSeriesModel series = model.getSeries().get(0);
        for (int index = 0; index < model.getCategories().size(); index++) {
            dataset.setValue(model.getCategories().get(index),
                    series.getValues().get(index));
        }
        final List<String> labels = model.getDataLabels();
        final List<String> categories = model.getCategories();
        Plot plot;
        if (ring) {
            RingPlot ringPlot = new RingPlot(dataset);
            ringPlot.setSectionDepth(0.35);
            plot = ringPlot;
        } else {
            plot = new org.jfree.chart.plot.PiePlot<String>(dataset);
        }
        org.jfree.chart.plot.PiePlot<?> pie =
                (org.jfree.chart.plot.PiePlot<?>) plot;
        pie.setBackgroundPaint(Color.WHITE);
        pie.setLabelGenerator(model.getDataLabelMode() == ChartDataLabelMode.NONE
                ? null : new PieSectionLabelGenerator() {
            @Override
            public String generateSectionLabel(
                    org.jfree.data.general.PieDataset dataset, Comparable key) {
                int index = categories.indexOf(String.valueOf(key));
                return index >= 0 && index < labels.size()
                        ? labels.get(index) : String.valueOf(key);
            }

            @Override
            public java.text.AttributedString generateAttributedSectionLabel(
                    org.jfree.data.general.PieDataset dataset, Comparable key) {
                return null;
            }
        });
        for (int index = 0; index < model.getCategories().size(); index++) {
            pie.setSectionPaint(
                    model.getCategories().get(index),
                    color(series, index));
        }
        return finish(model, plot);
    }

    private JFreeChart radarChart(ChartModel model) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (ChartSeriesModel series : model.getSeries()) {
            for (int category = 0;
                    category < model.getCategories().size(); category++) {
                dataset.addValue(
                        series.getValues().get(category),
                        series.getName(),
                        model.getCategories().get(category));
            }
        }
        SpiderWebPlot plot = new SpiderWebPlot(dataset);
        plot.setBackgroundPaint(Color.WHITE);
        for (int index = 0; index < model.getSeries().size(); index++) {
            plot.setSeriesPaint(
                    index, color(model.getSeries().get(index), index));
            plot.setSeriesOutlineStroke(
                    index, stroke(model.getSeries().get(index)));
        }
        return finish(model, plot);
    }

    private JFreeChart finish(ChartModel model, Plot plot) {
        JFreeChart chart = new JFreeChart(
                model.getTitle(),
                new Font(fontFamily, Font.BOLD, 22),
                plot,
                model.getLegendPosition() != LegendPosition.NONE);
        chart.setBackgroundPaint(Color.WHITE);
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setPosition(edge(model.getLegendPosition()));
        }
        return chart;
    }

    private static AbstractCategoryItemRenderer categoryRenderer(
            ChartType type) {
        if (type == ChartType.COLUMN || type == ChartType.BAR) {
            return new BarRenderer();
        }
        if (type == ChartType.STACKED_COLUMN
                || type == ChartType.STACKED_BAR) {
            return new StackedBarRenderer(false);
        }
        if (type == ChartType.PERCENT_STACKED_COLUMN) {
            return new StackedBarRenderer(true);
        }
        if (type == ChartType.LINE) {
            return new LineAndShapeRenderer(true, true);
        }
        if (type == ChartType.AREA) {
            return new AreaRenderer();
        }
        if (type == ChartType.STACKED_AREA) {
            return new StackedAreaRenderer();
        }
        throw new UnsupportedChartTypeException(type);
    }

    private static void configureCategoryRenderer(
            AbstractCategoryItemRenderer renderer,
            List<ChartSeriesModel> series,
            List<ChartSeriesModel> globalSeries) {
        for (int index = 0; index < series.size(); index++) {
            ChartSeriesModel item = series.get(index);
            int globalIndex = globalSeries.indexOf(item);
            renderer.setSeriesPaint(index, color(item, globalIndex));
            renderer.setSeriesStroke(index, stroke(item));
            if (item.getDataLabelMode() != ChartDataLabelMode.NONE) {
                renderer.setSeriesItemLabelGenerator(
                        index, hasText(item.getFormat())
                                ? new StandardCategoryItemLabelGenerator(
                                        "{2}", new DecimalFormat(item.getFormat()))
                                : new StandardCategoryItemLabelGenerator());
                renderer.setSeriesItemLabelsVisible(index, Boolean.TRUE);
            }
            if (renderer instanceof LineAndShapeRenderer) {
                LineAndShapeRenderer line = (LineAndShapeRenderer) renderer;
                line.setSeriesShapesVisible(index, item.isMarker());
                line.setSeriesShape(index,
                        new Ellipse2D.Double(-4, -4, 8, 8));
            }
        }
    }

    private static void configureXYLabels(
            org.jfree.chart.renderer.xy.AbstractXYItemRenderer renderer,
            ChartSeriesModel series) {
        if (series.getDataLabelMode() == ChartDataLabelMode.NONE) {
            return;
        }
        java.text.NumberFormat numberFormat = hasText(series.getFormat())
                ? new DecimalFormat(series.getFormat())
                : new DecimalFormat("0.##########");
        renderer.setSeriesItemLabelGenerator(
                0, new StandardXYItemLabelGenerator(
                        "{2}", numberFormat, numberFormat));
        renderer.setSeriesItemLabelsVisible(0, true);
    }

    private static LegendItemCollection legendItems(ChartModel model) {
        LegendItemCollection items = new LegendItemCollection();
        for (int index = 0; index < model.getSeries().size(); index++) {
            ChartSeriesModel series = model.getSeries().get(index);
            Color color = color(series, index);
            boolean lineVisible = series.getType() == ChartType.LINE;
            boolean shapeVisible = !lineVisible || series.isMarker();
            java.awt.Shape shape = series.getType() == ChartType.SCATTER
                    || lineVisible
                    ? new Ellipse2D.Double(-4, -4, 8, 8)
                    : new Rectangle2D.Double(-4, -4, 8, 8);
            items.add(new LegendItem(
                    series.getName(), series.getName(), null, null,
                    shapeVisible, shape, true, color,
                    false, color, stroke(series),
                    lineVisible, new Line2D.Double(-8, 0, 8, 0),
                    stroke(series), color));
        }
        return items;
    }

    private static Map<GroupKey, List<ChartSeriesModel>> categoryGroups(
            List<ChartSeriesModel> series) {
        Map<GroupKey, List<ChartSeriesModel>> groups =
                new LinkedHashMap<GroupKey, List<ChartSeriesModel>>();
        for (ChartSeriesModel item : series) {
            GroupKey key = new GroupKey(
                    item.getType(), item.getAxis(), item.getStackGroup());
            List<ChartSeriesModel> values = groups.get(key);
            if (values == null) {
                values = new ArrayList<ChartSeriesModel>();
                groups.put(key, values);
            }
            values.add(item);
        }
        return groups;
    }

    private static boolean compatibleFamily(ChartModel model) {
        Set<Integer> families = new LinkedHashSet<Integer>();
        for (ChartSeriesModel series : model.getSeries()) {
            ChartType type = series.getType();
            families.add(type.isPieLike() ? 1
                    : type == ChartType.RADAR ? 2
                    : type == ChartType.SCATTER || type == ChartType.BUBBLE ? 3
                    : 4);
        }
        if (families.size() != 1) {
            return false;
        }
        if (families.contains(1) && model.getSeries().size() != 1) {
            return false;
        }
        boolean horizontal = false;
        boolean vertical = false;
        for (ChartSeriesModel series : model.getSeries()) {
            if (series.getType() == ChartType.BAR
                    || series.getType() == ChartType.STACKED_BAR) {
                horizontal = true;
            } else if (!series.getType().isPieLike()
                    && series.getType() != ChartType.RADAR
                    && series.getType() != ChartType.SCATTER
                    && series.getType() != ChartType.BUBBLE) {
                vertical = true;
            }
        }
        return !(horizontal && vertical);
    }

    private static boolean allHorizontal(ChartModel model) {
        boolean horizontal = false;
        for (ChartSeriesModel series : model.getSeries()) {
            if (series.getType() == ChartType.BAR
                    || series.getType() == ChartType.STACKED_BAR) {
                horizontal = true;
            } else {
                return false;
            }
        }
        return horizontal;
    }

    private static NumberAxis numberAxis(
            BigDecimal minimum, BigDecimal maximum, boolean percent) {
        NumberAxis axis = new NumberAxis("");
        if (percent) {
            NumberFormat format = NumberFormat.getPercentInstance(Locale.ROOT);
            format.setMaximumFractionDigits(2);
            axis.setNumberFormatOverride(format);
            axis.setRange(
                    minimum == null ? 0D : minimum.doubleValue(),
                    maximum == null ? 1D : maximum.doubleValue());
            return axis;
        }
        if (minimum != null && maximum != null) {
            axis.setRange(minimum.doubleValue(), maximum.doubleValue());
        } else {
            if (minimum != null) {
                axis.setLowerBound(minimum.doubleValue());
            }
            if (maximum != null) {
                axis.setUpperBound(maximum.doubleValue());
            }
        }
        return axis;
    }

    private static boolean axisUsesPercent(
            ChartModel model, ChartAxis axis) {
        for (ChartSeriesModel series : model.getSeries()) {
            if (series.getAxis() == axis
                    && series.getType()
                    == ChartType.PERCENT_STACKED_COLUMN) {
                return true;
            }
        }
        return false;
    }

    private static double x(String category, int index) {
        try {
            return new BigDecimal(category).doubleValue();
        } catch (NumberFormatException ignored) {
            return index + 1D;
        }
    }

    private static double doubleValue(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static Color color(ChartSeriesModel series, int index) {
        String value = series.getColor();
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_COLORS.get(index % DEFAULT_COLORS.size());
        }
        return Color.decode(value.startsWith("#") ? value : "#" + value);
    }

    private static BasicStroke stroke(ChartSeriesModel series) {
        float width = series.getLineWidth().floatValue();
        ChartLineStyle style = series.getLineStyle();
        if (style == ChartLineStyle.DASHED) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 10, new float[] {8, 6}, 0);
        }
        if (style == ChartLineStyle.DOTTED) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 10, new float[] {2, 5}, 0);
        }
        return new BasicStroke(width);
    }

    private static RectangleEdge edge(LegendPosition position) {
        if (position == LegendPosition.TOP) {
            return RectangleEdge.TOP;
        }
        if (position == LegendPosition.LEFT) {
            return RectangleEdge.LEFT;
        }
        if (position == LegendPosition.RIGHT) {
            return RectangleEdge.RIGHT;
        }
        return RectangleEdge.BOTTOM;
    }

    private static String chooseFont(List<String> candidates) {
        Set<String> available = new LinkedHashSet<String>(
                Arrays.asList(
                        GraphicsEnvironment.getLocalGraphicsEnvironment()
                                .getAvailableFontFamilyNames()));
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate != null && available.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return "Dialog";
    }

    private static void applyFont(JFreeChart chart, Font font) {
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(font.deriveFont(Font.BOLD, 22F));
        }
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(font.deriveFont(16F));
        }
        Plot plot = chart.getPlot();
        if (plot instanceof CategoryPlot) {
            CategoryPlot category = (CategoryPlot) plot;
            category.getDomainAxis().setLabelFont(font);
            category.getDomainAxis().setTickLabelFont(font.deriveFont(14F));
            for (int index = 0; index < category.getRangeAxisCount(); index++) {
                if (category.getRangeAxis(index) != null) {
                    category.getRangeAxis(index).setLabelFont(font);
                    category.getRangeAxis(index)
                            .setTickLabelFont(font.deriveFont(14F));
                }
            }
        } else if (plot instanceof XYPlot) {
            XYPlot xy = (XYPlot) plot;
            xy.getDomainAxis().setLabelFont(font);
            xy.getDomainAxis().setTickLabelFont(font.deriveFont(14F));
            for (int index = 0; index < xy.getRangeAxisCount(); index++) {
                if (xy.getRangeAxis(index) != null) {
                    xy.getRangeAxis(index).setLabelFont(font);
                    xy.getRangeAxis(index).setTickLabelFont(font.deriveFont(14F));
                }
            }
        } else if (plot instanceof org.jfree.chart.plot.PiePlot) {
            ((org.jfree.chart.plot.PiePlot<?>) plot)
                    .setLabelFont(font.deriveFont(14F));
        } else if (plot instanceof SpiderWebPlot) {
            ((SpiderWebPlot) plot).setLabelFont(font.deriveFont(14F));
        }
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class GroupKey {
        private final ChartType type;
        private final ChartAxis axis;
        private final String stackGroup;

        private GroupKey(
                ChartType type, ChartAxis axis, String stackGroup) {
            this.type = type;
            this.axis = axis;
            this.stackGroup = stackGroup;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GroupKey)) {
                return false;
            }
            GroupKey that = (GroupKey) other;
            return type == that.type && axis == that.axis
                    && java.util.Objects.equals(stackGroup, that.stackGroup);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(type, axis, stackGroup);
        }
    }
}
