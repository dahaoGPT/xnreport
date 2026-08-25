package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.dataset.DatasetResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel 表格与图表公式范围动态解析器。
 * <p>
 * 在已填充数据的工作表中，通过查找结构化表（{@link XSSFTable}）或字段列绑定关系，
 * 计算出各字段在物理工作表上的表头行、数据行及列序号，构建 {@link ChartFormulaRange}。
 * </p>
 */
public final class ChartRangeResolver {

    /**
     * 解析图表对应数据集在 Excel 中的物理单元格范围。
     *
     * @param workbook 工作簿
     * @param dataset 数据集定义
     * @param result 数据集查询结果
     * @param binding Excel 表格绑定定义
     * @param chart 图表配置定义
     * @return 解析完成的 ChartFormulaRange
     */
    public ChartFormulaRange resolve(
            XSSFWorkbook workbook,
            DatasetDefinition dataset,
            DatasetResult result,
            ExcelTableBinding binding,
            ChartDefinition chart) {
        if (workbook == null || dataset == null
                || result == null || chart == null) {
            throw new IllegalArgumentException(
                    "workbook, dataset, result and chart must not be null");
        }
        if (!dataset.getId().equals(chart.getDataset())) {
            throw new IllegalArgumentException(
                    "Chart/dataset mismatch: " + chart.getDataset());
        }
        XSSFSheet sheet = workbook.getSheet(dataset.getSheetName());
        if (sheet == null) {
            throw new IllegalArgumentException(
                    "Missing chart dataset sheet: "
                            + dataset.getSheetName());
        }
        if (workbook.isSheetHidden(workbook.getSheetIndex(sheet))
                || workbook.isSheetVeryHidden(
                        workbook.getSheetIndex(sheet))) {
            throw new IllegalArgumentException(
                    "Chart dataset sheet must be visible: "
                            + dataset.getSheetName());
        }
        XSSFTable table = findTable(
                sheet, binding, chart.getExcelTable());
        if (table == null) {
            throw new IllegalArgumentException(
                    "Missing dataset table on chart sheet: "
                            + dataset.getSheetName());
        }
        AreaReference area = table.getArea();
        int headerRow = area.getFirstCell().getRow();
        int firstDataRow = headerRow + 1;
        int pointCount = resultRowCount(result);
        Map<String, Integer> columns =
                resolveColumns(table, binding);
        requireField(columns, chart.getCategoryField());
        for (com.xn.report.config.definition.ChartSeriesDefinition series
                : chart.getSeries()) {
            if (series == null) {
                throw new IllegalArgumentException(
                        "Chart series must not be null");
            }
            requireField(columns, series.getField());
            if (series.getSizeField() != null) {
                requireField(columns, series.getSizeField());
            }
        }
        return new ChartFormulaRange(
                dataset.getSheetName(), headerRow,
                firstDataRow, pointCount, columns);
    }

    private static XSSFTable findTable(
            XSSFSheet sheet,
            ExcelTableBinding binding,
            String configuredTable) {
        String tableName = configuredTable != null
                ? configuredTable
                : binding == null ? null : binding.getTable();
        if (tableName != null) {
            for (XSSFTable table : sheet.getTables()) {
                if (tableName.equalsIgnoreCase(
                        table.getName())) {
                    return table;
                }
            }
            return null;
        }
        if (sheet.getTables().size() != 1) {
            throw new IllegalArgumentException(
                    "Expected exactly one dataset table on chart sheet "
                            + sheet.getSheetName() + " but found "
                            + sheet.getTables().size());
        }
        return sheet.getTables().get(0);
    }

    private static Map<String, Integer> resolveColumns(
            XSSFTable table, ExcelTableBinding binding) {
        Map<String, Integer> byHeader =
                new LinkedHashMap<String, Integer>();
        List<org.apache.poi.xssf.usermodel.XSSFTableColumn> tableColumns =
                table.getColumns();
        int firstColumn = table.getArea().getFirstCell().getCol();
        for (int index = 0; index < tableColumns.size(); index++) {
            String name = tableColumns.get(index).getName();
            byHeader.put(normalize(name), firstColumn + index);
        }
        Map<String, Integer> fields =
                new LinkedHashMap<String, Integer>();
        if (binding != null) {
            for (ExcelTableBinding.ColumnBinding column
                    : binding.getColumns()) {
                Integer index = byHeader.get(
                        normalize(column.getHeader()));
                if (index == null) {
                    throw new IllegalArgumentException(
                            "Missing configured chart table header: "
                                    + column.getHeader());
                }
                fields.put(normalize(column.getField()), index);
            }
        }
        for (Map.Entry<String, Integer> entry : byHeader.entrySet()) {
            if (!fields.containsKey(entry.getKey())) {
                fields.put(entry.getKey(), entry.getValue());
            }
        }
        return fields;
    }

    private static int resultRowCount(DatasetResult result) {
        if (result.type()
                == com.xn.report.dataset.DatasetType.LIST) {
            return result.list().size();
        }
        if (result.type()
                == com.xn.report.dataset.DatasetType.SINGLE) {
            return result.single() == null ? 0 : 1;
        }
        return result.scalar() == null ? 0 : 1;
    }

    private static void requireField(
            Map<String, Integer> columns, String field) {
        if (field == null
                || !columns.containsKey(normalize(field))) {
            throw new IllegalArgumentException(
                    "Chart field is missing from dataset table: "
                            + field);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Chart table field/header must not be blank");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
