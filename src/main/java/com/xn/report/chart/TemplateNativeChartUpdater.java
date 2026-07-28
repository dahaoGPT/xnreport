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

    private static void bindSeries(
            XmlObject[] physical,
            List<DesiredSeries> desired,
            ChartDefinition definition,
            ChartFormulaRange range,
            XSSFSheet dataSheet) {
        Set<Integer> used = new HashSet<Integer>();
        for (XmlObject series : physical) {
            int desiredIndex = compatibleDesired(
                    series, desired, used);
            DesiredSeries binding = desired.get(desiredIndex);
            used.add(Integer.valueOf(desiredIndex));
            setUnsignedValue(
                    series, "./c:idx", desiredIndex);
            setUnsignedValue(
                    series, "./c:order", desiredIndex);
            String categoryFormula =
                    range.formula(definition.getCategoryField());
            String valueFormula =
                    range.formula(binding.configured.getField());
            setFormula(series,
                    ".//c:cat//c:f | .//c:xVal//c:f",
                    categoryFormula);
            setFormula(series,
                    ".//c:val//c:f | .//c:yVal//c:f",
                    valueFormula);
            setFormula(series, ".//c:tx//c:f",
                    range.titleFormula(
                            binding.configured.getField()));
            if (binding.configured.getSizeField() != null) {
                setFormula(series,
                        ".//c:bubbleSize//c:f",
                        range.formula(
                                binding.configured.getSizeField()));
            }

            rebuildStringReferences(
                    series, ".//c:tx//c:strRef",
                    dataSheet, range.getHeaderRow(),
                    1, range.column(
                            binding.configured.getField()));
            rebuildCategoryCaches(
                    series, dataSheet, range,
                    definition.getCategoryField());
            rebuildNumberReferences(
                    series,
                    ".//c:val//c:numRef | .//c:yVal//c:numRef",
                    dataSheet, range.getFirstDataRow(),
                    range.getPointCount(),
                    range.column(binding.configured.getField()));
            if (binding.configured.getSizeField() != null) {
                rebuildNumberReferences(
                        series, ".//c:bubbleSize//c:numRef",
                        dataSheet, range.getFirstDataRow(),
                        range.getPointCount(),
                        range.column(
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
                        definition.getSeries().get(index), null));
            }
            return result;
        }
        List<DesiredSeries> result =
                new ArrayList<DesiredSeries>();
        Set<Integer> used = new HashSet<Integer>();
        for (ChartSeriesModel series : model.getSeries()) {
            int match = -1;
            for (int index = 0;
                    index < definition.getSeries().size(); index++) {
                ChartSeriesDefinition configured =
                        definition.getSeries().get(index);
                if (!used.contains(Integer.valueOf(index))
                        && configured.getField().equalsIgnoreCase(
                                series.getField())
                        && configured.getType() == series.getType()) {
                    match = index;
                    break;
                }
            }
            if (match < 0) {
                throw new IllegalArgumentException(
                        "ChartModel series is not present in template "
                                + "definition: " + series.getField());
            }
            used.add(Integer.valueOf(match));
            result.add(new DesiredSeries(
                    definition.getSeries().get(match), series));
        }
        return Collections.unmodifiableList(result);
    }

    private static int compatibleDesired(
            XmlObject physical,
            List<DesiredSeries> desired,
            Set<Integer> used) {
        String plotType = physical.getDomNode().getParentNode()
                .getLocalName();
        for (int index = 0; index < desired.size(); index++) {
            if (!used.contains(Integer.valueOf(index))
                    && compatible(
                            plotType,
                            desired.get(index).configured.getType())) {
                return index;
            }
        }
        throw new IllegalArgumentException(
                "Template plot type " + plotType
                        + " has no compatible ChartModel series");
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
        private final ChartSeriesModel model;

        private DesiredSeries(
                ChartSeriesDefinition configured,
                ChartSeriesModel model) {
            this.configured = configured;
            this.model = model;
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
