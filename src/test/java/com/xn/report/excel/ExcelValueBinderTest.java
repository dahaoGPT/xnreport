package com.xn.report.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.TimeZone;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelValueBinderTest {

    @Test
    void writesOnlyFiniteExcelSafeNumericValues() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ExcelValueBinder binder = new ExcelValueBinder(workbook);
            org.apache.poi.ss.usermodel.Row row =
                    workbook.createSheet("Data").createRow(0);

            binder.bind(
                    row.createCell(0),
                    new BigDecimal("123456789012345"));
            binder.bind(
                    row.createCell(1),
                    new BigInteger("123456789012345"));
            assertThat(row.getCell(0).getNumericCellValue())
                    .isEqualTo(123456789012345d);
            assertThat(row.getCell(1).getNumericCellValue())
                    .isEqualTo(123456789012345d);

            for (Number unsafe : Arrays.<Number>asList(
                    new BigDecimal("1234567890123456"),
                    new BigInteger("1234567890123456"),
                    new BigDecimal("1E+400"),
                    new BigDecimal("1E-400"),
                    Double.NaN,
                    Double.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY)) {
                Cell cell = row.createCell(
                        row.getLastCellNum());
                assertThatIllegalArgumentException().isThrownBy(() ->
                        binder.bind(cell, unsafe))
                        .withMessageContaining("Excel numeric value");
            }
        }
    }

    @Test
    void localDateAndDateTimeSerialsDoNotDependOnDefaultTimeZone()
            throws Exception {
        TimeZone original = TimeZone.getDefault();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ExcelValueBinder binder = new ExcelValueBinder(workbook);
            org.apache.poi.ss.usermodel.Row row =
                    workbook.createSheet("Data").createRow(0);
            LocalDate date = LocalDate.of(2026, 3, 8);
            LocalDateTime dateTime =
                    LocalDateTime.of(2026, 3, 8, 2, 30, 15);

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            binder.bind(row.createCell(0), date);
            binder.bind(row.createCell(1), dateTime);
            double utcDate = row.getCell(0).getNumericCellValue();
            double utcDateTime = row.getCell(1).getNumericCellValue();

            TimeZone.setDefault(
                    TimeZone.getTimeZone("America/Los_Angeles"));
            binder.bind(row.createCell(2), date);
            binder.bind(row.createCell(3), dateTime);

            assertThat(row.getCell(2).getNumericCellValue())
                    .isEqualTo(utcDate);
            assertThat(row.getCell(3).getNumericCellValue())
                    .isEqualTo(utcDateTime);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
