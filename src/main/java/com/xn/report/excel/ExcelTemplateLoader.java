package com.xn.report.excel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelTemplateLoader {

    public XSSFWorkbook load(Path template) throws IOException {
        if (template == null || !Files.isRegularFile(template)) {
            throw new IllegalArgumentException(
                    "Excel template must be a readable file: " + template);
        }
        try (InputStream stream = Files.newInputStream(template)) {
            return new XSSFWorkbook(stream);
        }
    }
}
