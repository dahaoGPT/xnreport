package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;
import com.xn.report.config.definition.TemplateChartLocatorDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.namespace.QName;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumData;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumRef;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumVal;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTStrData;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTStrRef;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTStrVal;

/**
 * Rebinds formulas and caches while retaining template plot/layout/style XML.
 */
public final class TemplateNativeChartUpdater {

    private static final String CHART_NS =
            "http://schemas.openxmlformats.org/"
                    + "drawingml/2006/chart";
    private static final String NS =
            "declare namespace c='" + CHART_NS + "'; ";

    private final ChartLocator locator;

    public TemplateNativeChartUpdater() {
        this(new ChartLocator());
    }

    public TemplateNativeChartUpdater(ChartLocator locator) {
        if (locator == null) {
            throw new IllegalArgumentException(
                    "locator must not be null");
        }
        this.locator = locator;
    }

    public XSSFChart update(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartFormulaRange range) {
        return update(workbook, definition, null, range);
    }

    public XSSFChart update(
            XSSFWorkbook workbook,
            ChartDefinition definition,
            ChartModel model,
            ChartFormulaRange range) {
        if (workbook == null || definition == null || range == null) {
            throw new IllegalArgumentException(
                    "workbook, definition and range must not be null");
        }
        validateUniqueTargets(workbook, definition);
        LocatorValue locatorValue =
                locatorValue(definition, model);
        String targetSheet = definition.getExcelSheet() == null
                ? range.getSheetName() : definition.getExcelSheet();
        XSSFChart chart = locator.findUnique(
                workbook, targetSheet,
                locatorValue.marker, locatorValue.index);
        XSSFSheet dataSheet =
                workbook.getSheet(range.getSheetName());
        if (dataSheet == null) {
            throw new IllegalArgumentException(
                    "Missing template chart data sheet: "
                            + range.getSheetName());
        }

        XmlObject[] physicalSeries = chart.getCTChart()
                .selectPath(NS + ".//c:ser");
        List<DesiredSeries> desired =
                desiredSeries(definition, model);
        if (physicalSeries.length != desired.size()) {
            throw new IllegalArgumentException(
                    "Template chart OOXML series count "
                            + physicalSeries.length
                            + " does not match configured/model series "
                            + desired.size());
        }
        bindSeries(
                chart, physicalSeries, desired, definition,
                range, dataSheet);
        workbook.setForceFormulaRecalculation(true);
        return chart;
    }

    public void validateUniqueTargets(
            XSSFWorkbook workbook,
            ChartDefinition definition) {
        if (workbook == null || definition == null) {
            throw new IllegalArgumentException(
                    "workbook and definition must not be null");
        }
        if (definition.getMode()
                != ChartDefinition.Mode.TEMPLATE_NATIVE
                || definition.getGroupByField() == null) {
            return;
        }
        Set<String> targets = new HashSet<String>();
        for (int index = 0;
                index < definition.getTemplateChartLocators().size();
                index++) {
            TemplateChartLocatorDefinition item =
                    definition.getTemplateChartLocators().get(index);
            XSSFChart chart = locator.findUnique(
                    workbook, definition.getExcelSheet(),
                    item.getMarker(), item.getIndex());
            String target = chart.getPackagePart()
                    .getPartName().getName();
            if (!targets.add(target)) {
                throw new IllegalArgumentException(
                        "templateChartLocators[" + index
                                + "] resolves to the same template chart "
                                + "as an earlier locator: " + target);
            }
        }
    }

    private static void bindSeries(
            XSSFChart chart,
            XmlObject[] physical,
            List<DesiredSeries> desired,
            ChartDefinition definition,
            ChartFormulaRange range,
            XSSFSheet dataSheet) {
        Set<Integer> used = new HashSet<Integer>();
        List<PhysicalBinding> bindings =
                new ArrayList<PhysicalBinding>();
        for (XmlObject series : physical) {
            int sourceIndex = compatibleConfigured(
                    chart, series, definition, used);
            int desiredIndex = desiredIndex(
                    desired, sourceIndex);
            used.add(Integer.valueOf(sourceIndex));
            bindings.add(new PhysicalBinding(
                    series, desiredIndex));
        }
        if (used.size() != definition.getSeries().size()) {
            throw new IllegalArgumentException(
                    "Template chart does not uniquely represent every "
                            + "configured series");
        }
        for (PhysicalBinding physicalBinding : bindings) {
            XmlObject series = physicalBinding.series;
            int desiredIndex = physicalBinding.desiredIndex;
            DesiredSeries binding = desired.get(desiredIndex);
            setUnsignedValue(
                    series, "./c:idx", desiredIndex);
            setUnsignedValue(
                    series, "./c:order", desiredIndex);
            String categoryFormula =
                    range.formula(definition.getCategoryField());
            String valueFormula =
                    range.seriesFormula(
                            desiredIndex,
                            binding.configured.getField());
            setFormula(series,
                    ".//c:cat//c:f | .//c:xVal//c:f",
                    categoryFormula);
            setFormula(series,
                    ".//c:val//c:f | .//c:yVal//c:f",
                    valueFormula);
            setFormula(series, ".//c:tx//c:f",
                    range.seriesTitleFormula(
                            desiredIndex,
                            binding.configured.getField()));
            if (binding.configured.getSizeField() != null) {
                setFormula(series,
                        ".//c:bubbleSize//c:f",
                        range.sizeFormula(
                                desiredIndex,
                                binding.configured.getSizeField()));
            }

            rebuildStringReferences(
                    series, ".//c:tx//c:strRef",
                    dataSheet, range.getHeaderRow(),
                    1, range.seriesColumn(
                            desiredIndex,
                            binding.configured.getField()));
            rebuildCategoryCaches(
                    series, dataSheet, range,
                    definition.getCategoryField());
            rebuildNumberReferences(
                    series,
                    ".//c:val//c:numRef | .//c:yVal//c:numRef",
                    dataSheet, range.getFirstDataRow(),
                    range.getPointCount(),
                    range.seriesColumn(
                            desiredIndex,
                            binding.configured.getField()));
            if (binding.configured.getSizeField() != null) {
                rebuildNumberReferences(
                        series, ".//c:bubbleSize//c:numRef",
                        dataSheet, range.getFirstDataRow(),
                        range.getPointCount(),
                        range.sizeColumn(
                                desiredIndex,
                                binding.configured.getSizeField()));
            }
        }
    }

    private static void rebuildCategoryCaches(
            XmlObject series,
            XSSFSheet dataSheet,
            ChartFormulaRange range,
            String categoryField) {
        int column = range.column(categoryField);
        rebuildStringReferences(
                series,
                ".//c:cat//c:strRef | .//c:xVal//c:strRef",
                dataSheet, range.getFirstDataRow(),
                range.getPointCount(), column);
        rebuildNumberReferences(
                series,
                ".//c:cat//c:numRef | .//c:xVal//c:numRef",
                dataSheet, range.getFirstDataRow(),
                range.getPointCount(), column);
    }

    private static void rebuildStringReferences(
            XmlObject parent,
            String path,
            XSSFSheet sheet,
            int firstRow,
            int pointCount,
            int column) {
        for (XmlObject value
                : parent.selectPath(NS + path)) {
            if (!(value instanceof CTStrRef)) {
                throw new IllegalArgumentException(
                        "Unexpected string chart reference type");
            }
            CTStrRef reference = (CTStrRef) value;
            CTStrData cache = reference.isSetStrCache()
                    ? reference.getStrCache()
                    : reference.addNewStrCache();
            cache.setPtArray(new CTStrVal[0]);
            if (!cache.isSetPtCount()) {
                cache.addNewPtCount();
            }
            cache.getPtCount().setVal(pointCount);
            DataFormatter formatter = new DataFormatter();
            for (int index = 0; index < pointCount; index++) {
                Cell cell = cell(sheet, firstRow + index, column);
                CTStrVal point = cache.addNewPt();
                point.setIdx(index);
                point.setV(cell == null
                        ? "" : formatter.formatCellValue(cell));
            }
        }
    }

    private static void rebuildNumberReferences(
            XmlObject parent,
            String path,
            XSSFSheet sheet,
            int firstRow,
            int pointCount,
            int column) {
        for (XmlObject value
                : parent.selectPath(NS + path)) {
            if (!(value instanceof CTNumRef)) {
                throw new IllegalArgumentException(
                        "Unexpected numeric chart reference type");
            }
            CTNumRef reference = (CTNumRef) value;
            CTNumData cache = reference.isSetNumCache()
                    ? reference.getNumCache()
                    : reference.addNewNumCache();
            cache.setPtArray(new CTNumVal[0]);
            if (!cache.isSetPtCount()) {
                cache.addNewPtCount();
            }
            cache.getPtCount().setVal(pointCount);
            if (!cache.isSetFormatCode()) {
                cache.setFormatCode("General");
            }
            for (int index = 0; index < pointCount; index++) {
                Cell cell = cell(sheet, firstRow + index, column);
                String numeric = numericValue(cell);
                if (numeric == null) {
                    continue;
                }
                CTNumVal point = cache.addNewPt();
                point.setIdx(index);
                point.setV(numeric);
            }
        }
    }

    private static String numericValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return Double.toString(cell.getNumericCellValue());
        }
        if (cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType()
                == CellType.NUMERIC) {
            return Double.toString(cell.getNumericCellValue());
        }
        throw new IllegalArgumentException(
                "Template numeric chart cache references non-numeric "
                        + cell.getSheet().getSheetName() + "!"
                        + cell.getAddress());
    }

    private static Cell cell(
            XSSFSheet sheet, int rowIndex, int column) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(column);
    }

    private static List<DesiredSeries> desiredSeries(
            ChartDefinition definition, ChartModel model) {
        if (model == null) {
            List<DesiredSeries> result =
                    new ArrayList<DesiredSeries>();
            for (int index = 0;
                    index < definition.getSeries().size(); index++) {
                result.add(new DesiredSeries(
                        definition.getSeries().get(index),
                        index));
            }
            return result;
        }
        List<DesiredSeries> result =
                new ArrayList<DesiredSeries>();
        for (ChartSeriesModel series : model.getSeries()) {
            int sourceIndex = series.getSourceIndex();
            if (sourceIndex < 0
                    || sourceIndex >= definition.getSeries().size()) {
                throw new IllegalArgumentException(
                        "ChartModel series has no stable source index: "
                                + series.getName());
            }
            ChartSeriesDefinition configured =
                    definition.getSeries().get(sourceIndex);
            result.add(new DesiredSeries(
                    configured, sourceIndex));
        }
        return Collections.unmodifiableList(result);
    }

    private static int compatibleConfigured(
            XSSFChart chart,
            XmlObject physical,
            ChartDefinition definition,
            Set<Integer> used) {
        ChartType plotType = physicalType(physical);
        ChartAxis plotAxis = physicalAxis(chart, physical, plotType);
        List<Integer> candidates = new ArrayList<Integer>();
        for (int index = 0;
                index < definition.getSeries().size(); index++) {
            ChartSeriesDefinition configured =
                    definition.getSeries().get(index);
            ChartAxis configuredAxis = configured.getAxis() == null
                    ? ChartAxis.PRIMARY : configured.getAxis();
            if (!used.contains(Integer.valueOf(index))
                    && configured.getType() == plotType
                    && configuredAxis == plotAxis) {
                candidates.add(Integer.valueOf(index));
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0).intValue();
        }
        String title = physicalTitle(physical);
        if (title != null) {
            Integer matched = null;
            for (Integer candidate : candidates) {
                ChartSeriesDefinition configured =
                        definition.getSeries().get(
                                candidate.intValue());
                if (title.equals(configured.getName())
                        || title.equals(configured.getField())) {
                    if (matched != null) {
                        throw ambiguousTemplateSeries(
                                plotType, plotAxis, title);
                    }
                    matched = candidate;
                }
            }
            if (matched != null) {
                return matched.intValue();
            }
        }
        int originalIndex = unsignedValue(physical, "./c:idx");
        if (candidates.contains(Integer.valueOf(originalIndex))) {
            return originalIndex;
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Template plot " + plotType + " on " + plotAxis
                            + " axis has no compatible configured series");
        }
        throw ambiguousTemplateSeries(plotType, plotAxis, title);
    }

    private static IllegalArgumentException ambiguousTemplateSeries(
            ChartType type, ChartAxis axis, String title) {
        return new IllegalArgumentException(
                "Template plot " + type + " on " + axis
                        + " axis is ambiguous"
                        + (title == null ? ""
                        : " for series title " + title)
                        + "; use unique series titles or preserve "
                        + "configured source indexes");
    }

    private static ChartType physicalType(XmlObject series) {
        XmlObject plot = parent(series);
        String plotType = plot.getDomNode().getLocalName();
        if ("barChart".equals(plotType)) {
            String direction = attributeValue(
                    plot, "./c:barDir", "val");
            String grouping = attributeValue(
                    plot, "./c:grouping", "val");
            if ("col".equals(direction)) {
                if ("stacked".equals(grouping)) {
                    return ChartType.STACKED_COLUMN;
                }
                if ("percentStacked".equals(grouping)) {
                    return ChartType.PERCENT_STACKED_COLUMN;
                }
                if ("clustered".equals(grouping)) {
                    return ChartType.COLUMN;
                }
            } else if ("bar".equals(direction)) {
                if ("stacked".equals(grouping)) {
                    return ChartType.STACKED_BAR;
                }
                if ("clustered".equals(grouping)) {
                    return ChartType.BAR;
                }
            }
            throw new IllegalArgumentException(
                    "Unsupported template bar contract: direction="
                            + direction + ", grouping=" + grouping);
        }
        if ("lineChart".equals(plotType)) {
            return ChartType.LINE;
        }
        if ("areaChart".equals(plotType)) {
            String grouping = attributeValue(
                    plot, "./c:grouping", "val");
            if ("stacked".equals(grouping)) {
                return ChartType.STACKED_AREA;
            }
            if ("standard".equals(grouping)) {
                return ChartType.AREA;
            }
            throw new IllegalArgumentException(
                    "Unsupported template area grouping: " + grouping);
        }
        if ("pieChart".equals(plotType)) {
            return ChartType.PIE;
        }
        if ("doughnutChart".equals(plotType)) {
            return ChartType.DOUGHNUT;
        }
        if ("scatterChart".equals(plotType)) {
            return ChartType.SCATTER;
        }
        if ("radarChart".equals(plotType)) {
            return ChartType.RADAR;
        }
        if ("bubbleChart".equals(plotType)) {
            return ChartType.BUBBLE;
        }
        if ("stockChart".equals(plotType)) {
            return ChartType.STOCK;
        }
        throw new IllegalArgumentException(
                "Unsupported template plot type " + plotType);
    }

    private static ChartAxis physicalAxis(
            XSSFChart chart, XmlObject series, ChartType type) {
        if (type.isPieLike()) {
            return ChartAxis.PRIMARY;
        }
        Set<Long> axisIds = new HashSet<Long>();
        for (XmlObject node
                : parent(series).selectPath(NS + "./c:axId")) {
            axisIds.add(Long.valueOf(
                    Long.parseLong(requiredAttribute(node, "val"))));
        }
        ChartAxis result = null;
        for (org.openxmlformats.schemas.drawingml.x2006.chart.CTValAx axis
                : chart.getCTChart().getPlotArea().getValAxList()) {
            if (!axisIds.contains(Long.valueOf(
                    axis.getAxId().getVal()))) {
                continue;
            }
            AxisPosition position = axisPosition(
                    axis.getAxPos().getVal().toString());
            if (position != AxisPosition.LEFT
                    && position != AxisPosition.RIGHT) {
                continue;
            }
            ChartAxis current = position == AxisPosition.RIGHT
                    ? ChartAxis.SECONDARY : ChartAxis.PRIMARY;
            if (result != null && result != current) {
                throw new IllegalArgumentException(
                        "Template plot references both primary and "
                                + "secondary value axes");
            }
            result = current;
        }
        if (result == null) {
            throw new IllegalArgumentException(
                    "Template plot " + type
                            + " has no unambiguous LEFT/RIGHT value axis");
        }
        return result;
    }

    private static AxisPosition axisPosition(String value) {
        if ("l".equals(value)) {
            return AxisPosition.LEFT;
        }
        if ("r".equals(value)) {
            return AxisPosition.RIGHT;
        }
        if ("b".equals(value)) {
            return AxisPosition.BOTTOM;
        }
        if ("t".equals(value)) {
            return AxisPosition.TOP;
        }
        throw new IllegalArgumentException(
                "Unsupported template axis position: " + value);
    }

    private static String physicalTitle(XmlObject series) {
        XmlObject[] literal = series.selectPath(
                NS + "./c:tx/c:v");
        if (literal.length == 1) {
            return textValue(literal[0]);
        }
        XmlObject[] cached = series.selectPath(
                NS + "./c:tx/c:strRef/c:strCache/c:pt/c:v");
        if (cached.length > 0) {
            return textValue(cached[0]);
        }
        return null;
    }

    private static String textValue(XmlObject value) {
        try (XmlCursor cursor = value.newCursor()) {
            return cursor.getTextValue();
        }
    }

    private static XmlObject parent(XmlObject value) {
        try (XmlCursor cursor = value.newCursor()) {
            if (!cursor.toParent()) {
                throw new IllegalArgumentException(
                        "Template series has no plot parent");
            }
            return cursor.getObject();
        }
    }

    private static String attributeValue(
            XmlObject parent, String path, String name) {
        XmlObject[] values = parent.selectPath(NS + path);
        if (values.length != 1) {
            throw new IllegalArgumentException(
                    "Template plot is missing " + path);
        }
        return requiredAttribute(values[0], name);
    }

    private static String requiredAttribute(
            XmlObject value, String name) {
        try (XmlCursor cursor = value.newCursor()) {
            String result = cursor.getAttributeText(new QName(name));
            if (result == null) {
                throw new IllegalArgumentException(
                        "Template chart node is missing @" + name);
            }
            return result;
        }
    }

    private static int unsignedValue(
            XmlObject parent, String path) {
        XmlObject[] values = parent.selectPath(NS + path);
        if (values.length != 1) {
            throw new IllegalArgumentException(
                    "Template series is missing " + path);
        }
        return Integer.parseInt(requiredAttribute(values[0], "val"));
    }

    private static int desiredIndex(
            List<DesiredSeries> desired,
            int sourceIndex) {
        for (int index = 0; index < desired.size(); index++) {
            if (desired.get(index).sourceIndex == sourceIndex) {
                return index;
            }
        }
        throw new IllegalArgumentException(
                "Template source series is absent from ChartModel: "
                        + sourceIndex);
    }

    private static LocatorValue locatorValue(
            ChartDefinition definition, ChartModel model) {
        if (definition.getGroupByField() != null) {
            if (model == null || model.getGroupKey() == null) {
                throw new IllegalArgumentException(
                        "Grouped template update requires ChartModel "
                                + "with groupKey");
            }
            for (TemplateChartLocatorDefinition item
                    : definition.getTemplateChartLocators()) {
                if (model.getGroupKey().equals(
                        item.getGroupKey())) {
                    return new LocatorValue(
                            item.getMarker(), item.getIndex());
                }
            }
            throw new IllegalArgumentException(
                    "Missing template chart locator for group "
                            + model.getGroupKey());
        }
        String marker = definition.getTemplateChartMarker();
        if (marker == null
                && definition.getTemplateChartIndex() == null) {
            marker = "REPORT_CHART:" + definition.getId();
        }
        return new LocatorValue(
                marker, definition.getTemplateChartIndex());
    }

    private static void setFormula(
            XmlObject parent, String path, String formula) {
        for (XmlObject item
                : parent.selectPath(NS + path)) {
            try (XmlCursor cursor = item.newCursor()) {
                cursor.setTextValue(formula);
            }
        }
    }

    private static void setUnsignedValue(
            XmlObject parent, String path, int value) {
        XmlObject[] nodes = parent.selectPath(NS + path);
        if (nodes.length != 1) {
            throw new IllegalArgumentException(
                    "Template chart series is missing " + path);
        }
        try (XmlCursor cursor = nodes[0].newCursor()) {
            cursor.setAttributeText(
                    new QName("val"),
                    Integer.toString(value));
        }
    }

    private static final class DesiredSeries {
        private final ChartSeriesDefinition configured;
        private final int sourceIndex;

        private DesiredSeries(
                ChartSeriesDefinition configured,
                int sourceIndex) {
            this.configured = configured;
            this.sourceIndex = sourceIndex;
        }
    }

    private static final class PhysicalBinding {
        private final XmlObject series;
        private final int desiredIndex;

        private PhysicalBinding(
                XmlObject series,
                int desiredIndex) {
            this.series = series;
            this.desiredIndex = desiredIndex;
        }
    }

    private static final class LocatorValue {
        private final String marker;
        private final Integer index;

        private LocatorValue(String marker, Integer index) {
            this.marker = marker;
            this.index = index;
        }
    }
}
