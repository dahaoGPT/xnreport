package com.xn.report.excel;

import com.xn.report.text.FormulaInjectionGuard;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelValueBinder {

    private final FormulaInjectionGuard formulaGuard;
    private final CellStyle dateStyle;
    private final CellStyle dateTimeStyle;
    private final CellStyle timeStyle;

    public ExcelValueBinder(XSSFWorkbook workbook) {
        this(workbook, new FormulaInjectionGuard());
    }

    ExcelValueBinder(
            XSSFWorkbook workbook, FormulaInjectionGuard formulaGuard) {
        if (workbook == null) {
            throw new IllegalArgumentException("workbook must not be null");
        }
        this.formulaGuard = formulaGuard;
        CreationHelper helper = workbook.getCreationHelper();
        this.dateStyle = style(workbook, helper, "yyyy-mm-dd");
        this.dateTimeStyle = style(
                workbook, helper, "yyyy-mm-dd hh:mm:ss");
        this.timeStyle = style(workbook, helper, "hh:mm:ss");
    }

    public void bind(Cell cell, Object value) {
        if (cell == null) {
            throw new IllegalArgumentException("cell must not be null");
        }
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Boolean) {
            cell.setCellValue(((Boolean) value).booleanValue());
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof BigInteger) {
            cell.setCellValue(((BigInteger) value).doubleValue());
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof LocalDateTime) {
            LocalDateTime dateTime = (LocalDateTime) value;
            cell.setCellValue(Date.from(dateTime.atZone(
                    ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof LocalDate) {
            LocalDate date = (LocalDate) value;
            cell.setCellValue(Date.from(date.atStartOfDay(
                    ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalTime) {
            LocalTime time = (LocalTime) value;
            cell.setCellValue(time.toSecondOfDay() / 86400.0d
                    + time.getNano() / 86400000000000.0d);
            cell.setCellStyle(timeStyle);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof byte[]) {
            cell.setCellValue(Base64.getEncoder()
                    .encodeToString((byte[]) value));
        } else {
            cell.setCellValue(formulaGuard.asPlainText(String.valueOf(value)));
        }
    }

    private static CellStyle style(
            XSSFWorkbook workbook, CreationHelper helper, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(
                helper.createDataFormat().getFormat(format));
        return style;
    }
}
