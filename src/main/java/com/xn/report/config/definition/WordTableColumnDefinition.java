package com.xn.report.config.definition;

/**
 * Word 表格列定义模型。
 * <p>
 * 定义 Word 表格中单列绑定的字段名（field）、表头文本（header）、单元格格式化样式（format）以及列宽（widthDxa，单位为二十分之一点）。
 * </p>
 */
public class WordTableColumnDefinition {

    /** 数据集中的字段名称。 */
    private String field;

    /** 列标题文本。 */
    private String header;

    /** 单元格数据格式化 pattern。 */
    private String format;

    /** 列宽（以 dxa 为单位，1/20 pt）。 */
    private Integer widthDxa;

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
        this.format = format;
    }

    public Integer getWidthDxa() {
        return widthDxa;
    }

    public void setWidthDxa(Integer widthDxa) {
        this.widthDxa = widthDxa;
    }
}
