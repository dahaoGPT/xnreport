package com.xn.report.chart;

import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;

/**
 * Excel 模板图表物理定位查找器。
 * <p>
 * 通过读取 OpenXML 图形属性（descr 描述标识、name 名称、title 标题）或工作表内 0-based 图表索引，
 * 在目标工作表中准确定位唯一的 {@link XSSFChart} 对象。
 * </p>
 */
public final class ChartLocator {

    /**
     * 在工作表中查找唯一匹配的模板图表。
     *
     * @param workbook 工作簿
     * @param sheetName 工作表名
     * @param marker 标记字符串（可匹配 descr, name, title）
     * @param chartIndex 0-based 物理图表索引（与 marker 二选一）
     * @return 匹配的 XSSFChart 对象
     */
    public XSSFChart findUnique(
            XSSFWorkbook workbook,
            String sheetName,
            String marker,
            Integer chartIndex) {
        if (workbook == null) {
            throw new IllegalArgumentException(
                    "workbook must not be null");
        }
        XSSFSheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException(
                    "Template chart sheet does not exist: " + sheetName);
        }
        List<XSSFChart> charts = sheet.getDrawingPatriarch() == null
                ? java.util.Collections.<XSSFChart>emptyList()
                : sheet.getDrawingPatriarch().getCharts();
        if (marker != null && chartIndex != null) {
            throw new IllegalArgumentException(
                    "Template chart locator must use marker or index, not both");
        }
        if (chartIndex != null) {
            if (chartIndex.intValue() < 0
                    || chartIndex.intValue() >= charts.size()) {
                throw new IllegalArgumentException(
                        "Template chart index matched 0 charts: "
                                + chartIndex);
            }
            return charts.get(chartIndex.intValue());
        }
        if (marker == null || marker.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Template chart marker or index is required");
        }
        List<XSSFChart> matches = new ArrayList<XSSFChart>();
        for (XSSFChart chart : charts) {
            CTNonVisualDrawingProps properties =
                    properties(sheet.getDrawingPatriarch(), chart);
            if (marker.equals(properties.getDescr())
                    || marker.equals(properties.getName())
                    || marker.equals(properties.getTitle())) {
                matches.add(chart);
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "Template chart marker matched 0 charts: " + marker);
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException(
                    "Template chart marker matched multiple charts: "
                            + marker);
        }
        return matches.get(0);
    }

    /**
     * 为新生成的图表写入描述标记（descr），以便后续精准识别。
     */
    public static void setMarker(
            XSSFChart chart, String marker) {
        if (chart == null || marker == null
                || marker.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "chart and marker are required");
        }
        XSSFDrawing drawing =
                chart.getParent() instanceof XSSFDrawing
                        ? (XSSFDrawing) chart.getParent() : null;
        properties(drawing, chart).setDescr(marker);
    }

    private static CTNonVisualDrawingProps properties(
            XSSFDrawing drawing, XSSFChart chart) {
        if (chart.getGraphicFrame() != null
                && chart.getGraphicFrame()
                        .getCTGraphicalObjectFrame()
                        .getNvGraphicFramePr() != null
                && chart.getGraphicFrame()
                        .getCTGraphicalObjectFrame()
                        .getNvGraphicFramePr().getCNvPr() != null) {
            return chart.getGraphicFrame()
                    .getCTGraphicalObjectFrame()
                    .getNvGraphicFramePr().getCNvPr();
        }
        if (drawing == null) {
            throw new IllegalArgumentException(
                    "Chart has no non-visual properties");
        }
        String relationId = drawing.getRelationId(chart);
        String namespaces =
                "declare namespace c='http://schemas.openxmlformats.org/"
                + "drawingml/2006/chart'; "
                + "declare namespace xdr='http://schemas.openxmlformats.org/"
                + "drawingml/2006/spreadsheetDrawing'; ";
        XmlObject[] frames = drawing.getCTDrawing().selectPath(
                namespaces + ".//xdr:graphicFrame");
        for (XmlObject frame : frames) {
            XmlObject[] references = frame.selectPath(
                    namespaces + ".//c:chart");
            if (references.length != 1) {
                continue;
            }
            String candidate;
            try (XmlCursor cursor = references[0].newCursor()) {
                candidate = cursor.getAttributeText(
                        new javax.xml.namespace.QName(
                                "http://schemas.openxmlformats.org/"
                                + "officeDocument/2006/relationships",
                                "id"));
            }
            if (!relationId.equals(candidate)) {
                continue;
            }
            XmlObject[] properties = frame.selectPath(
                    namespaces + ".//xdr:cNvPr");
            if (properties.length == 1
                    && properties[0]
                    instanceof CTNonVisualDrawingProps) {
                return (CTNonVisualDrawingProps) properties[0];
            }
        }
        throw new IllegalArgumentException(
                "Chart has no non-visual properties");
    }
}
