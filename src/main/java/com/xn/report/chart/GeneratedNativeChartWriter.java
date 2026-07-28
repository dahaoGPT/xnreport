package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.PresetLineDash;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFPresetLineDash;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.DisplayBlanks;
import org.apache.poi.xddf.usermodel.chart.Grouping;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFAreaChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDoughnutChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFScatterChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFRadarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Creates editable native Excel charts. All data references target the
 * dataset's visible sheet; no hidden helper sheet is created.
 */
public final class GeneratedNativeChartWriter {

    public XSSFChart write(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartModel model,
            ChartFormulaRange range) {
        return write(workbook, definition, model, range, 0);
    }

    public XSSFChart write(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartModel model,
            ChartFormulaRange range,
            int chartOrdinal) {
        require(workbook, "workbook");
        require(definition, "definition");
        require(model, "model");
        require(range, "range");
        if (containsTemplateOnlyType(model)) {
            throw new UnsupportedChartTypeException(
                    "Chart type STOCK/BUBBLE requires "
                            + "TEMPLATE_NATIVE with Apache POI 5.2");
        }
        if (model.getSeries().size() != definition.getSeries().size()) {
            throw new IllegalArgumentException(
                    "Chart definition/model series count mismatch");
        }
        if (model.getCategories().size() != range.getPointCount()) {
            throw new IllegalArgumentException(
                    "Chart model must preserve the dataset sheet row count "
                            + "for directly traceable Excel formulas");
        }
        XSSFSheet sheet = destinationSheet(
                workbook, definition, range);
        XSSFClientAnchor anchor = anchor(
                sheet, definition, chartOrdinal);
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFChart chart = drawing.createChart(anchor);
        ChartLocator.setMarker(
                chart, "REPORT_CHART:" + model.getChartId()
                        + (model.getGroupKey() == null
                        ? "" : ":" + model.getGroupKey()));
        if (!model.getTitle().isEmpty()) {
            chart.setTitleText(model.getTitle());
            chart.setTitleOverlay(false);
        }
        configureLegend(chart, model.getLegendPosition());
        chart.setPlotOnlyVisibleCells(false);
        chart.displayBlanksAs(blankMode(model));

        boolean scatterOnly = isScatterOnly(model);
        if (hasScatter(model) && !scatterOnly) {
            throw new UnsupportedChartTypeException(
                    "A generated SCATTER chart cannot share category axes "
                            + "with other chart types; use TEMPLATE_NATIVE");
        }
        boolean needsAxes = needsAxes(model);
        AxisPair primary = needsAxes
                ? createAxes(
                        chart, ChartAxis.PRIMARY, scatterOnly) : null;
        AxisPair secondary = usesAxis(model, ChartAxis.SECONDARY)
                ? createAxes(
                        chart, ChartAxis.SECONDARY, scatterOnly) : null;

        Map<SeriesGroup, List<ChartSeriesModel>> groups =
                groups(model.getSeries());
        for (Map.Entry<SeriesGroup, List<ChartSeriesModel>> entry
                : groups.entrySet()) {
            SeriesGroup group = entry.getKey();
            AxisPair pair = group.axis == ChartAxis.SECONDARY
                    ? secondary : primary;
            XDDFChartData data = createData(
                    chart, group.type, pair);
            configureGroup(data, group.type);
            XDDFDataSource<?> categories =
                    categorySource(
                            model, definition.getCategoryField(),
                            range, group.type);
            for (ChartSeriesModel series : entry.getValue()) {
                XDDFNumericalDataSource<Double> values =
                        valuesSource(series, range);
                XDDFChartData.Series created =
                        data.addSeries(categories, values);
                created.setTitle(series.getName(),
                        new CellReference(
                                range.getSheetName(),
                                range.getHeaderRow(),
                                range.column(series.getField()),
                                true, true));
                configureSeries(created, series);
                configureDataLabels(
                        created,
                        series.getDataLabelMode()
                                == ChartDataLabelMode.NONE
                                ? model.getDataLabelMode()
                                : series.getDataLabelMode(),
                        series.getFormat());
            }
            chart.plot(data);
        }
        applyAxisBounds(primary, model, false);
        applyAxisBounds(secondary, model, true);
        return chart;
    }

    private static XSSFSheet destinationSheet(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartFormulaRange range) {
        String target = definition.getExcelSheet() == null
                ? range.getSheetName() : definition.getExcelSheet();
        XSSFSheet sheet = workbook.getSheet(target);
        if (sheet == null) {
            throw new IllegalArgumentException(
                    "Missing Excel chart destination sheet: " + target);
        }
        return sheet;
    }

    private static XSSFClientAnchor anchor(
            XSSFSheet sheet,
            ChartDefinition definition,
            int chartOrdinal) {
        int height = value(definition.getAnchorHeightRows(), 20);
        int row1 = definition.getAnchorRow() == null
                ? nextFreeChartRow(sheet)
                : definition.getAnchorRow().intValue()
                        + chartOrdinal * (height + 2);
        int col1 = value(definition.getAnchorColumn(), 0);
        int width = value(definition.getAnchorWidthColumns(), 10);
        if (row1 < 0 || col1 < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Chart anchor coordinates must be non-negative "
                            + "and dimensions positive");
        }
        if (definition.getAnchorRow() != null
                || definition.getAnchorColumn() != null) {
            ensureCellsAreEmpty(sheet, row1, col1, height, width);
            ensureChartsDoNotOverlap(
                    sheet, row1, col1,
                    row1 + height, col1 + width);
        }
        return configure(
                new XSSFClientAnchor(),
                row1, col1, height, width);
    }

    private static int nextFreeChartRow(XSSFSheet sheet) {
        int next = sheet.getLastRowNum() + 2;
        if (sheet.getDrawingPatriarch() == null) {
            return next;
        }
        for (org.openxmlformats.schemas.drawingml.x2006
                .spreadsheetDrawing.CTTwoCellAnchor anchor
                : sheet.getDrawingPatriarch().getCTDrawing()
                        .getTwoCellAnchorList()) {
            if (anchor.isSetGraphicFrame()) {
                next = Math.max(next, anchor.getTo().getRow() + 2);
            }
        }
        return next;
    }

    private static void ensureChartsDoNotOverlap(
            XSSFSheet sheet,
            int row1,
            int col1,
            int row2,
            int col2) {
        if (sheet.getDrawingPatriarch() == null) {
            return;
        }
        for (org.openxmlformats.schemas.drawingml.x2006
                .spreadsheetDrawing.CTTwoCellAnchor anchor
                : sheet.getDrawingPatriarch().getCTDrawing()
                        .getTwoCellAnchorList()) {
            if (!anchor.isSetGraphicFrame()) {
                continue;
            }
            boolean rows = row1 < anchor.getTo().getRow()
                    && row2 > anchor.getFrom().getRow();
            boolean columns = col1 < anchor.getTo().getCol()
                    && col2 > anchor.getFrom().getCol();
            if (rows && columns) {
                throw new IllegalArgumentException(
                        "Configured chart anchor overlaps an existing "
                                + "chart on sheet " + sheet.getSheetName());
            }
        }
    }

    private static XSSFClientAnchor configure(
            XSSFClientAnchor anchor,
            int row, int column, int height, int width) {
        anchor.setRow1(row);
        anchor.setCol1(column);
        anchor.setRow2(row + height);
        anchor.setCol2(column + width);
        return anchor;
    }

    private static void ensureCellsAreEmpty(
            XSSFSheet sheet,
            int row,
            int column,
            int height,
            int width) {
        for (int r = row; r < row + height; r++) {
            org.apache.poi.ss.usermodel.Row existing = sheet.getRow(r);
            if (existing == null) {
                continue;
            }
            for (int c = column; c < column + width; c++) {
                org.apache.poi.ss.usermodel.Cell cell =
                        existing.getCell(c);
                if (cell != null
                        && cell.getCellType()
                        != org.apache.poi.ss.usermodel.CellType.BLANK) {
                    throw new IllegalArgumentException(
                            "Configured chart anchor overlaps cell "
                                    + sheet.getSheetName() + "!"
                                    + new CellReference(r, c)
                                            .formatAsString());
                }
            }
        }
    }

    private static AxisPair createAxes(
            XSSFChart chart, ChartAxis axis, boolean scatter) {
        AxisPosition categoryPosition = axis == ChartAxis.PRIMARY
                ? AxisPosition.BOTTOM : AxisPosition.TOP;
        AxisPosition valuePosition = axis == ChartAxis.PRIMARY
                ? AxisPosition.LEFT : AxisPosition.RIGHT;
        XDDFChartAxis category = scatter
                ? chart.createValueAxis(categoryPosition)
                : chart.createCategoryAxis(categoryPosition);
        XDDFValueAxis value = chart.createValueAxis(valuePosition);
        category.crossAxis(value);
        value.crossAxis(category);
        if (axis == ChartAxis.SECONDARY) {
            category.setVisible(false);
        }
        return new AxisPair(category, value);
    }

    private static XDDFChartData createData(
            XSSFChart chart, ChartType type, AxisPair axes) {
        ChartTypes poiType;
        switch (type) {
            case COLUMN:
            case STACKED_COLUMN:
            case PERCENT_STACKED_COLUMN:
            case BAR:
            case STACKED_BAR:
                poiType = ChartTypes.BAR;
                break;
            case LINE:
                poiType = ChartTypes.LINE;
                break;
            case AREA:
            case STACKED_AREA:
                poiType = ChartTypes.AREA;
                break;
            case PIE:
                poiType = ChartTypes.PIE;
                break;
            case DOUGHNUT:
                poiType = ChartTypes.DOUGHNUT;
                break;
            case SCATTER:
                poiType = ChartTypes.SCATTER;
                break;
            case RADAR:
                poiType = ChartTypes.RADAR;
                break;
            default:
                throw new UnsupportedChartTypeException(
                        type + " requires TEMPLATE_NATIVE");
        }
        return chart.createData(
                poiType,
                axes == null ? null : axes.category,
                axes == null ? null : axes.value);
    }

    private static void configureGroup(
            XDDFChartData data, ChartType type) {
        if (data instanceof XDDFBarChartData) {
            XDDFBarChartData bar = (XDDFBarChartData) data;
            bar.setBarDirection(type == ChartType.BAR
                    || type == ChartType.STACKED_BAR
                    ? BarDirection.BAR : BarDirection.COL);
            if (type == ChartType.STACKED_COLUMN
                    || type == ChartType.STACKED_BAR) {
                bar.setBarGrouping(BarGrouping.STACKED);
                bar.setOverlap((byte) 100);
            } else if (type
                    == ChartType.PERCENT_STACKED_COLUMN) {
                bar.setBarGrouping(BarGrouping.PERCENT_STACKED);
                bar.setOverlap((byte) 100);
            } else {
                bar.setBarGrouping(BarGrouping.CLUSTERED);
            }
        } else if (data instanceof XDDFLineChartData) {
            ((XDDFLineChartData) data).setGrouping(Grouping.STANDARD);
        } else if (data instanceof XDDFAreaChartData) {
            ((XDDFAreaChartData) data).setGrouping(
                    type == ChartType.STACKED_AREA
                            ? Grouping.STACKED : Grouping.STANDARD);
        }
    }

    private static XDDFDataSource<?> categorySource(
            ChartModel model,
            String categoryField,
            ChartFormulaRange range,
            ChartType type) {
        if (type == ChartType.SCATTER) {
            Double[] values = new Double[model.getCategories().size()];
            for (int index = 0; index < values.length; index++) {
                try {
                    values[index] = Double.valueOf(
                            model.getCategories().get(index));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "SCATTER category values must be numeric: "
                                    + model.getCategories().get(index),
                            exception);
                }
            }
            return XDDFDataSourcesFactory.fromArray(
                    values,
                    range.formula(categoryField),
                    range.column(categoryField));
        }
        String[] categories =
                model.getCategories().toArray(new String[0]);
        return XDDFDataSourcesFactory.fromArray(
                categories,
                range.formula(categoryField),
                range.column(categoryField));
    }

    private static XDDFNumericalDataSource<Double> valuesSource(
            ChartSeriesModel series, ChartFormulaRange range) {
        Double[] values = new Double[series.getValues().size()];
        for (int index = 0; index < values.length; index++) {
            BigDecimal value = series.getValues().get(index);
            if (value == null
                    && series.getNullHandling()
                    == ChartNullHandling.ZERO) {
                value = BigDecimal.ZERO;
            }
            values[index] = value == null
                    ? null : Double.valueOf(value.doubleValue());
        }
        XDDFNumericalDataSource<Double> source =
                XDDFDataSourcesFactory.fromArray(
                        values,
                        range.formula(series.getField()),
                        range.column(series.getField()));
        if (series.getFormat() != null) {
            source.setFormatCode(series.getFormat());
        }
        return source;
    }

    private static void configureSeries(
            XDDFChartData.Series created,
            ChartSeriesModel series) {
        if (series.getColor() != null) {
            XDDFSolidFillProperties fill =
                    new XDDFSolidFillProperties(
                            XDDFColor.from(rgb(series.getColor())));
            if (series.getType() == ChartType.LINE
                    || series.getType() == ChartType.AREA
                    || series.getType() == ChartType.STACKED_AREA
                    || series.getType() == ChartType.RADAR) {
                XDDFLineProperties line =
                        new XDDFLineProperties(fill);
                if (series.getLineWidth() != null) {
                    line.setWidth(series.getLineWidth().doubleValue());
                }
                line.setPresetDash(new XDDFPresetLineDash(
                        dash(series.getLineStyle())));
                created.setLineProperties(line);
            } else {
                created.setFillProperties(fill);
            }
        }
        if (created instanceof XDDFLineChartData.Series) {
            ((XDDFLineChartData.Series) created).setMarkerStyle(
                    series.isMarker()
                            ? MarkerStyle.CIRCLE : MarkerStyle.NONE);
        }
    }

    private static void configureDataLabels(
            XDDFChartData.Series series,
            ChartDataLabelMode mode,
            String format) {
        if (mode == null || mode == ChartDataLabelMode.NONE) {
            return;
        }
        org.openxmlformats.schemas.drawingml.x2006.chart.CTDLbls labels;
        if (series instanceof XDDFBarChartData.Series) {
            labels = ((XDDFBarChartData.Series) series)
                    .getCTBarSer().addNewDLbls();
        } else if (series instanceof XDDFLineChartData.Series) {
            labels = ((XDDFLineChartData.Series) series)
                    .getCTLineSer().addNewDLbls();
        } else if (series instanceof XDDFAreaChartData.Series) {
            labels = ((XDDFAreaChartData.Series) series)
                    .getCTAreaSer().addNewDLbls();
        } else if (series instanceof XDDFPieChartData.Series) {
            labels = ((XDDFPieChartData.Series) series)
                    .getCTPieSer().addNewDLbls();
        } else if (series instanceof XDDFDoughnutChartData.Series) {
            labels = ((XDDFDoughnutChartData.Series) series)
                    .getCTPieSer().addNewDLbls();
        } else if (series instanceof XDDFScatterChartData.Series) {
            labels = ((XDDFScatterChartData.Series) series)
                    .getCTScatterSer().addNewDLbls();
        } else if (series instanceof XDDFRadarChartData.Series) {
            labels = ((XDDFRadarChartData.Series) series)
                    .getCTRadarSer().addNewDLbls();
        } else {
            throw new UnsupportedChartTypeException(
                    "Data labels are unsupported for generated series "
                            + series.getClass().getSimpleName()
                            + "; use TEMPLATE_NATIVE");
        }
        boolean showValue = mode == ChartDataLabelMode.VALUE
                || mode == ChartDataLabelMode.COUNT
                || mode == ChartDataLabelMode.COUNT_AND_PERCENT;
        boolean showPercent = mode == ChartDataLabelMode.PERCENT
                || mode == ChartDataLabelMode.COUNT_AND_PERCENT;
        labels.addNewShowVal().setVal(showValue);
        labels.addNewShowPercent().setVal(showPercent);
        labels.addNewShowCatName().setVal(false);
        labels.addNewShowSerName().setVal(false);
        labels.addNewShowLegendKey().setVal(false);
        if (format != null) {
            labels.addNewNumFmt().setFormatCode(format);
            labels.getNumFmt().setSourceLinked(false);
        }
    }

    private static byte[] rgb(String value) {
        String rgb = value.startsWith("#")
                ? value.substring(1) : value;
        if (!rgb.matches("[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException(
                    "Invalid chart RGB color: " + value);
        }
        return new byte[] {
            (byte) Integer.parseInt(rgb.substring(0, 2), 16),
            (byte) Integer.parseInt(rgb.substring(2, 4), 16),
            (byte) Integer.parseInt(rgb.substring(4, 6), 16)
        };
    }

    private static PresetLineDash dash(ChartLineStyle style) {
        if (style == ChartLineStyle.DASHED) {
            return PresetLineDash.DASH;
        }
        if (style == ChartLineStyle.DOTTED) {
            return PresetLineDash.DOT;
        }
        return PresetLineDash.SOLID;
    }

    private static Map<SeriesGroup, List<ChartSeriesModel>> groups(
            List<ChartSeriesModel> series) {
        Map<SeriesGroup, List<ChartSeriesModel>> groups =
                new LinkedHashMap<SeriesGroup, List<ChartSeriesModel>>();
        for (ChartSeriesModel item : series) {
            SeriesGroup key = new SeriesGroup(
                    item.getType(), item.getAxis(),
                    item.getStackGroup());
            List<ChartSeriesModel> values = groups.get(key);
            if (values == null) {
                values = new ArrayList<ChartSeriesModel>();
                groups.put(key, values);
            }
            values.add(item);
        }
        return groups;
    }

    private static void applyAxisBounds(
            AxisPair pair, ChartModel model, boolean secondary) {
        if (pair == null) {
            return;
        }
        BigDecimal min = secondary
                ? model.getSecondaryAxisMin()
                : model.getPrimaryAxisMin();
        BigDecimal max = secondary
                ? model.getSecondaryAxisMax()
                : model.getPrimaryAxisMax();
        if (min != null) pair.value.setMinimum(min.doubleValue());
        if (max != null) pair.value.setMaximum(max.doubleValue());
    }

    private static void configureLegend(
            XSSFChart chart, LegendPosition position) {
        if (position == LegendPosition.NONE) {
            chart.deleteLegend();
            return;
        }
        org.apache.poi.xddf.usermodel.chart.LegendPosition poi;
        switch (position) {
            case TOP:
                poi = org.apache.poi.xddf.usermodel.chart.LegendPosition.TOP;
                break;
            case LEFT:
                poi = org.apache.poi.xddf.usermodel.chart.LegendPosition.LEFT;
                break;
            case RIGHT:
                poi = org.apache.poi.xddf.usermodel.chart.LegendPosition.RIGHT;
                break;
            default:
                poi = org.apache.poi.xddf.usermodel.chart.LegendPosition.BOTTOM;
        }
        chart.getOrAddLegend().setPosition(poi);
    }

    private static DisplayBlanks blankMode(ChartModel model) {
        boolean zero = false;
        for (ChartSeriesModel series : model.getSeries()) {
            zero |= series.getNullHandling() == ChartNullHandling.ZERO;
        }
        return zero ? DisplayBlanks.ZERO : DisplayBlanks.GAP;
    }

    private static boolean needsAxes(ChartModel model) {
        for (ChartSeriesModel series : model.getSeries()) {
            if (!series.getType().isPieLike()) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesAxis(
            ChartModel model, ChartAxis axis) {
        for (ChartSeriesModel series : model.getSeries()) {
            if (series.getAxis() == axis
                    && !series.getType().isPieLike()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTemplateOnlyType(
            ChartModel model) {
        for (ChartSeriesModel series : model.getSeries()) {
            if (series.getType() == ChartType.STOCK
                    || series.getType() == ChartType.BUBBLE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasScatter(ChartModel model) {
        for (ChartSeriesModel series : model.getSeries()) {
            if (series.getType() == ChartType.SCATTER) {
                return true;
            }
        }
        return false;
    }

    private static boolean isScatterOnly(ChartModel model) {
        if (model.getSeries().isEmpty()) {
            return false;
        }
        for (ChartSeriesModel series : model.getSeries()) {
            if (series.getType() != ChartType.SCATTER) {
                return false;
            }
        }
        return true;
    }

    private static int value(Integer configured, int fallback) {
        return configured == null ? fallback : configured.intValue();
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    name + " must not be null");
        }
        return value;
    }

    private static final class AxisPair {
        private final XDDFChartAxis category;
        private final XDDFValueAxis value;

        private AxisPair(
                XDDFChartAxis category, XDDFValueAxis value) {
            this.category = category;
            this.value = value;
        }
    }

    private static final class SeriesGroup {
        private final ChartType type;
        private final ChartAxis axis;
        private final String stackGroup;

        private SeriesGroup(
                ChartType type, ChartAxis axis, String stackGroup) {
            this.type = type;
            this.axis = axis == null ? ChartAxis.PRIMARY : axis;
            this.stackGroup = stackGroup;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SeriesGroup)) return false;
            SeriesGroup that = (SeriesGroup) other;
            return type == that.type && axis == that.axis
                    && java.util.Objects.equals(
                            stackGroup, that.stackGroup);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(type, axis, stackGroup);
        }
    }
}
