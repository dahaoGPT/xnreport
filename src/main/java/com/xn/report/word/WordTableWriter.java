package com.xn.report.word;

import com.xn.report.config.definition.WordTableColumnDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import com.xn.report.text.FormatterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;

public final class WordTableWriter {

    private static final int DEFAULT_TABLE_WIDTH_DXA = 9360;
    private static final Pattern ROW_TOKEN = Pattern.compile(
            "\\{\\{row:([^}|]+)(?:\\|([^}:|]+)(?::([^}]*))?)?}}");

    private final WordRunTextReplacer replacer = new WordRunTextReplacer();
    private final FormatterRegistry formatters = FormatterRegistry.defaults();

    public int bindPrototype(
            XWPFTable table, DatasetResult dataset, String emptyMessage) {
        if (table == null || dataset == null) {
            throw new IllegalArgumentException(
                    "Word table and dataset are required");
        }
        int prototypeIndex = findPrototype(table);
        if (prototypeIndex < 0) {
            throw new WordTemplateException(
                    "Word table does not contain a {{row:...}} prototype row");
        }
        XWPFTableRow prototype = table.getRow(prototypeIndex);
        CTRow prototypeCopy = CTRow.Factory.newInstance();
        prototypeCopy.set(prototype.getCtRow());
        List<DatasetRow> rows = rows(dataset);
        markHeader(table);
        applyGeometry(table, null);
        if (rows.isEmpty()) {
            clearRowForMessage(prototype,
                    emptyMessage == null ? "暂无数据" : emptyMessage);
            return 0;
        }
        for (int index = 0; index < rows.size(); index++) {
            XWPFTableRow target;
            if (index == 0) {
                target = prototype;
            } else {
                target = cloneAttachedRow(
                        table, prototypeCopy, prototypeIndex + index);
            }
            bindRow(target, rows.get(index));
        }
        return rows.size();
    }

    public void fillGenerated(
            XWPFTable table,
            DatasetResult dataset,
            List<WordTableColumnDefinition> configuredColumns,
            String emptyMessage) {
        List<WordTableColumnDefinition> columns = columns(
                dataset, configuredColumns);
        ensureDimensions(table, 1, columns.size());
        for (int index = 0; index < columns.size(); index++) {
            setCell(table.getRow(0).getCell(index),
                    value(columns.get(index).getHeader(),
                            columns.get(index).getField()));
        }
        markHeader(table);
        applyGeometry(table, columns);
        List<DatasetRow> rows = rows(dataset);
        if (rows.isEmpty()) {
            XWPFTableRow row = table.createRow();
            setCell(row.getCell(0),
                    emptyMessage == null ? "暂无数据" : emptyMessage);
            for (int index = 1; index < columns.size(); index++) {
                setCell(row.getCell(index), "");
            }
            return;
        }
        for (DatasetRow source : rows) {
            XWPFTableRow row = table.createRow();
            for (int index = 0; index < columns.size(); index++) {
                WordTableColumnDefinition column = columns.get(index);
                setCell(row.getCell(index),
                        format(source.getOrNull(column.getField()),
                                column.getFormat()));
            }
        }
    }

    private void bindRow(XWPFTableRow row, DatasetRow source) {
        for (XWPFTableCell cell : row.getTableCells()) {
            String text = cell.getText();
            Matcher matcher = ROW_TOKEN.matcher(text);
            Map<String, String> values = new LinkedHashMap<String, String>();
            while (matcher.find()) {
                Object raw = source.getOrNull(matcher.group(1));
                String formatter = matcher.group(2);
                String argument = matcher.group(3);
                values.put(matcher.group(),
                        formatter == null
                                ? (raw == null ? "" : String.valueOf(raw))
                                : formatters.format(formatter, raw, argument));
            }
            replacer.replaceInBody(cell, values);
        }
    }

    private static XWPFTableRow cloneAttachedRow(
            XWPFTable table, CTRow prototype, int index) {
        XWPFTableRow target = table.insertNewTableRow(index);
        if (target == null) {
            throw new WordTemplateException(
                    "Unable to insert cloned Word table row at " + index);
        }
        if (prototype.isSetTrPr()) {
            CTTrPr properties = target.getCtRow().addNewTrPr();
            properties.set(prototype.getTrPr());
        }
        for (CTTc cell : prototype.getTcList()) {
            XWPFTableCell added = target.addNewTableCell();
            added.getCTTc().set(cell);
            XWPFTableCell refreshed = new XWPFTableCell(
                    added.getCTTc(), target, table.getBody());
            target.getTableCells().set(
                    target.getTableCells().size() - 1, refreshed);
        }
        return target;
    }

    private int findPrototype(XWPFTable table) {
        for (int index = 0; index < table.getNumberOfRows(); index++) {
            for (XWPFTableCell cell : table.getRow(index).getTableCells()) {
                if (cell.getText().contains("{{row:")) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static List<DatasetRow> rows(DatasetResult dataset) {
        if (dataset.type() == DatasetType.LIST) {
            return dataset.list();
        }
        if (dataset.type() == DatasetType.SINGLE) {
            DatasetRow row = dataset.single();
            return row == null
                    ? Collections.<DatasetRow>emptyList()
                    : Collections.singletonList(row);
        }
        Object scalar = dataset.scalar();
        if (scalar == null) {
            return Collections.emptyList();
        }
        String field = dataset.schema().fieldNames().isEmpty()
                ? "value" : dataset.schema().fieldNames().get(0);
        return Collections.singletonList(DatasetRow.of(field, scalar));
    }

    private static List<WordTableColumnDefinition> columns(
            DatasetResult dataset,
            List<WordTableColumnDefinition> configured) {
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        List<WordTableColumnDefinition> inferred =
                new ArrayList<WordTableColumnDefinition>();
        for (String field : dataset.schema().fieldNames()) {
            WordTableColumnDefinition column = new WordTableColumnDefinition();
            column.setField(field);
            column.setHeader(field);
            inferred.add(column);
        }
        if (inferred.isEmpty()) {
            WordTableColumnDefinition column = new WordTableColumnDefinition();
            column.setField("value");
            column.setHeader("value");
            inferred.add(column);
        }
        return inferred;
    }

    private String format(Object value, String format) {
        if (format == null || format.trim().isEmpty()) {
            return value == null ? "" : String.valueOf(value);
        }
        int separator = format.indexOf(':');
        String name = separator < 0 ? format : format.substring(0, separator);
        String argument = separator < 0 ? null : format.substring(separator + 1);
        return formatters.format(name, value, argument);
    }

    private static void clearRowForMessage(
            XWPFTableRow row, String message) {
        for (int index = 0; index < row.getTableCells().size(); index++) {
            setCell(row.getCell(index), index == 0 ? message : "");
        }
    }

    private static void setCell(XWPFTableCell cell, String text) {
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph =
                cell.getParagraphs().get(0);
        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }
        paragraph.createRun().setText(text == null ? "" : text);
    }

    private static void ensureDimensions(
            XWPFTable table, int rows, int columns) {
        while (table.getNumberOfRows() < rows) {
            table.createRow();
        }
        XWPFTableRow first = table.getRow(0);
        while (first.getTableCells().size() < columns) {
            first.addNewTableCell();
        }
    }

    private static void markHeader(XWPFTable table) {
        if (table.getNumberOfRows() == 0) {
            return;
        }
        CTTrPr properties = table.getRow(0).getCtRow().isSetTrPr()
                ? table.getRow(0).getCtRow().getTrPr()
                : table.getRow(0).getCtRow().addNewTrPr();
        CTOnOff header = properties.sizeOfTblHeaderArray() == 0
                ? properties.addNewTblHeader()
                : properties.getTblHeaderArray(0);
        header.setVal(Boolean.TRUE);
    }

    private static void applyGeometry(
            XWPFTable table, List<WordTableColumnDefinition> columns) {
        table.setTableAlignment(TableRowAlign.CENTER);
        table.setWidth(String.valueOf(DEFAULT_TABLE_WIDTH_DXA));
        table.setCellMargins(80, 120, 80, 120);
        if (table.getNumberOfRows() == 0) {
            return;
        }
        int columnCount = table.getRow(0).getTableCells().size();
        int[] widths = columnWidths(columns, columnCount);
        for (XWPFTableRow row : table.getRows()) {
            for (int index = 0;
                    index < row.getTableCells().size() && index < widths.length;
                    index++) {
                row.getCell(index).setWidth(String.valueOf(widths[index]));
            }
        }
    }

    private static int[] columnWidths(
            List<WordTableColumnDefinition> columns, int columnCount) {
        int[] widths = new int[Math.max(1, columnCount)];
        int configuredTotal = 0;
        boolean complete = columns != null && columns.size() >= columnCount;
        for (int index = 0; index < columnCount && complete; index++) {
            Integer width = columns.get(index).getWidthDxa();
            complete = width != null && width.intValue() > 0;
            if (complete) {
                configuredTotal += width.intValue();
            }
        }
        if (complete && configuredTotal > 0) {
            for (int index = 0; index < columnCount; index++) {
                widths[index] = Math.max(1, (int) Math.round(
                        columns.get(index).getWidthDxa().doubleValue()
                                / configuredTotal * DEFAULT_TABLE_WIDTH_DXA));
            }
        } else {
            int each = DEFAULT_TABLE_WIDTH_DXA / Math.max(1, columnCount);
            java.util.Arrays.fill(widths, each);
        }
        return widths;
    }

    private static String value(String candidate, String fallback) {
        return candidate == null || candidate.trim().isEmpty()
                ? fallback : candidate;
    }
}
