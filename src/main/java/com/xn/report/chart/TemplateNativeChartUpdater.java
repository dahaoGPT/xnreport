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
                physicalSeries, desired, definition,
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
            XmlObject[] physical,
            List<DesiredSeries> desired,
            ChartDefinition definition,
            ChartFormulaRange range,
            XSSFSheet dataSheet) {
        Set<Integer> used = new HashSet<Integer>();
        for (XmlObject series : physical) {
            int sourceIndex = compatibleConfigured(
                    series, definition, used);
            int desiredIndex = desiredIndex(
                    desired, sourceIndex);
            DesiredSeries binding = desired.get(desiredIndex);
            used.add(Integer.valueOf(sourceIndex));
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
            XmlObject physical,
            ChartDefinition definition,
            Set<Integer> used) {
        String plotType = physical.getDomNode().getParentNode()
                .getLocalName();
        for (int index = 0;
                index < definition.getSeries().size(); index++) {
            if (!used.contains(Integer.valueOf(index))
                    && compatible(
                            plotType,
                            definition.getSeries().get(index)
                                    .getType())) {
                return index;
            }
        }
        throw new IllegalArgumentException(
                "Template plot type " + plotType
                        + " has no compatible ChartModel series");
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

    private static boolean compatible(
            String plotType, ChartType type) {
        if ("barChart".equals(plotType)) {
            return type == ChartType.COLUMN
                    || type == ChartType.STACKED_COLUMN
                    || type == ChartType.PERCENT_STACKED_COLUMN
                    || type == ChartType.BAR
                    || type == ChartType.STACKED_BAR;
        }
        if ("lineChart".equals(plotType)) {
            return type == ChartType.LINE;
        }
        if ("areaChart".equals(plotType)) {
            return type == ChartType.AREA
                    || type == ChartType.STACKED_AREA;
        }
        if ("pieChart".equals(plotType)) {
            return type == ChartType.PIE;
        }
        if ("doughnutChart".equals(plotType)) {
            return type == ChartType.DOUGHNUT;
        }
        if ("scatterChart".equals(plotType)) {
            return type == ChartType.SCATTER;
        }
        if ("radarChart".equals(plotType)) {
            return type == ChartType.RADAR;
        }
        if ("bubbleChart".equals(plotType)) {
            return type == ChartType.BUBBLE;
        }
        if ("stockChart".equals(plotType)) {
            return type == ChartType.STOCK;
        }
        return false;
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

    private static final class LocatorValue {
        private final String marker;
        private final Integer index;

        private LocatorValue(String marker, Integer index) {
            this.marker = marker;
            this.index = index;
        }
    }
}
