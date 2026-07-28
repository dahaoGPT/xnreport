package com.xn.report.excel;

import com.xn.report.text.FormulaInjectionGuard;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelValueBinder {

    private final FormulaInjectionGuard formulaGuard;
    private final XSSFWorkbook workbook;
    private final CreationHelper creationHelper;
    private final Map<String, CellStyle> formattedStyles =
            new LinkedHashMap<String, CellStyle>();

    public ExcelValueBinder(XSSFWorkbook workbook) {
        this(workbook, new FormulaInjectionGuard());
    }

    ExcelValueBinder(
            XSSFWorkbook workbook, FormulaInjectionGuard formulaGuard) {
        if (workbook == null) {
            throw new IllegalArgumentException("workbook must not be null");
        }
        this.formulaGuard = formulaGuard;
        this.workbook = workbook;
        this.creationHelper = workbook.getCreationHelper();
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
            cell.setCellValue(safeDecimal((BigDecimal) value));
        } else if (value instanceof BigInteger) {
            cell.setCellValue(safeDecimal(
                    new BigDecimal((BigInteger) value)));
        } else if (value instanceof Number) {
            cell.setCellValue(safeNumber((Number) value));
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue((LocalDateTime) value);
            applyNumberFormat(cell, "yyyy-mm-dd hh:mm:ss");
        } else if (value instanceof LocalDate) {
            cell.setCellValue((LocalDate) value);
            applyNumberFormat(cell, "yyyy-mm-dd");
        } else if (value instanceof LocalTime) {
            LocalTime time = (LocalTime) value;
            cell.setCellValue(time.toSecondOfDay() / 86400.0d
                    + time.getNano() / 86400000000000.0d);
            applyNumberFormat(cell, "hh:mm:ss");
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            applyNumberFormat(cell, "yyyy-mm-dd hh:mm:ss");
        } else if (value instanceof byte[]) {
            cell.setCellValue(Base64.getEncoder()
                    .encodeToString((byte[]) value));
        } else {
            cell.setCellValue(formulaGuard.asPlainText(String.valueOf(value)));
        }
    }

    private static double safeDecimal(BigDecimal value) {
        BigDecimal normalized = value.signum() == 0
                ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (normalized.precision() > 15) {
            throw new IllegalArgumentException(
                    "Excel numeric value exceeds 15 significant digits: "
                            + value.toString());
        }
        double numeric = finite(
                value.doubleValue(), value.toString());
        if (value.signum() != 0 && numeric == 0.0d) {
            throw new IllegalArgumentException(
                    "Excel numeric value underflows double range: "
                            + value.toString());
        }
        return numeric;
    }

    private static double safeNumber(Number value) {
        return finite(value.doubleValue(), String.valueOf(value));
    }

    private static double finite(double value, String source) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "Excel numeric value must be finite: " + source);
        }
        return value;
    }

    private void applyNumberFormat(Cell cell, String format) {
        CellStyle base = cell.getCellStyle();
        String key = base.getIndex() + "|" + format;
        CellStyle formatted = formattedStyles.get(key);
        if (formatted == null) {
            formatted = workbook.createCellStyle();
            formatted.cloneStyleFrom(base);
            formatted.setDataFormat(
                    creationHelper.createDataFormat().getFormat(format));
            formattedStyles.put(key, formatted);
        }
        cell.setCellStyle(formatted);
    }
}
