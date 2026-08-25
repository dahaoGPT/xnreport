package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Excel 标量单元格值绑定配置模型。
 * <p>
 * 定义将特定表达式、运行时参数或数据集标量值写入 Excel 指定单元格（如 {@code B2}）的规则。
 * </p>
 */
public class ExcelValueBinding {

    /** 目标工作表名称。 */
    private String sheet;

    /** 目标单元格坐标（如 "B2"、"Summary!C4"）。 */
    private String cell;

    /** 待写入的值表达式或占位符（如 "${runtime.reportPeriod}" 或 "${datasets.summary.totalCount}"）。 */
    private String value;

    /** 单元格格式化 pattern。 */
    private String format;

    @JsonIgnore
    private boolean formatPresent;

    public String getSheet() {
        return sheet;
    }

    public void setSheet(String sheet) {
        this.sheet = sheet;
    }

    public String getCell() {
        return cell;
    }

    public void setCell(String cell) {
        this.cell = cell;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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
