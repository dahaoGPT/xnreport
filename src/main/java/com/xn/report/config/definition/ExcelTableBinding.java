package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 多行表格数据展开绑定配置模型。
 * <p>
 * 定义如何将数据集（LIST 形态）中的多行记录按列映射写入到指定的 Excel 工作表（sheet）或命名表格（table）中：
 * <ul>
 *   <li><b>目标位置</b>：工作表名（sheet）、起始行号（startRow）或 Excel 结构化表格名（table）。</li>
 *   <li><b>列绑定（{@link ColumnBinding}）</b>：字段名（field）、列标题名（header）及格式化样式（format）。</li>
 * </ul>
 * </p>
 */
public class ExcelTableBinding {

    /** 绑定的数据集 ID。 */
    private String dataset;

    /** 目标工作表名称。 */
    private String sheet;

    /** 目标 Excel 结构化表格（Table/ListObject）名称。 */
    private String table;

    /** 数据写入的起始行号（0-based，默认为 0）。 */
    private Integer startRow = Integer.valueOf(0);

    /** 列映射列表。 */
    private List<ColumnBinding> columns = new ArrayList<ColumnBinding>();

    /** 反序列化时 columns 节点是否显式配置了 null。 */
    @JsonIgnore
    private boolean columnsExplicitNull;

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getSheet() {
        return sheet;
    }

    public void setSheet(String sheet) {
        this.sheet = sheet;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Integer getStartRow() {
        return startRow;
    }

    public void setStartRow(Integer startRow) {
        this.startRow = startRow;
    }

    public List<ColumnBinding> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnBinding> columns) {
        this.columnsExplicitNull = columns == null;
        this.columns = columns == null
                ? new ArrayList<ColumnBinding>() : columns;
    }

    @JsonIgnore
    public boolean isColumnsExplicitNull() {
        return columnsExplicitNull;
    }

    /**
     * 单列绑定定义模型。
     */
    public static class ColumnBinding {

        /** 数据集中读取的字段名。 */
        private String field;

        /** 表头标题文本。 */
        private String header;

        /** 单元格数值格式化 pattern（如 "#,##0.00"、"yyyy-MM-dd"）。 */
        private String format;

        @JsonIgnore
        private boolean formatPresent;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.formatPresent = true;
            this.format = format;
        }

        @JsonIgnore
        public boolean isFormatPresent() {
            return formatPresent;
        }
    }
}
