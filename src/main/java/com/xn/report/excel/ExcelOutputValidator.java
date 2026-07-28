package com.xn.report.excel;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ExcelTableBinding;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelOutputValidator {

    public void validate(
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context) throws IOException {
        validate(
                output, definitions, context,
                Collections.<String, ExcelTableBinding>emptyMap());
    }

    public void validate(
            Path output,
            List<DatasetDefinition> definitions,
            DatasetContext context,
            Map<String, ExcelTableBinding> bindings) throws IOException {
        try (InputStream stream = Files.newInputStream(output);
                XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            validateUniqueTableNames(workbook);
            for (DatasetDefinition definition : definitions) {
                DatasetResult result = context.get(definition.getId());
                XSSFSheet sheet = workbook.getSheet(
                        definition.getSheetName());
                if (sheet == null) {
                    throw new IllegalStateException(
                            "Missing dataset sheet: "
                                    + definition.getSheetName());
                }
                if (workbook.getSheetVisibility(
                        workbook.getSheetIndex(sheet))
                        != SheetVisibility.VISIBLE) {
                    throw new IllegalStateException(
                            "Dataset sheet is not visible: "
                                    + definition.getSheetName());
                }
                ExcelTableBinding binding =
                        bindings.get(definition.getId());
                int startRow = binding == null
                        ? 0 : binding.getStartRow().intValue();
                XSSFTable table = targetTable(
                        sheet, binding, startRow,
                        definition.getSheetName());
                int expectedLastRow =
                        startRow
                                + ExcelDatasetSheetWriter.rows(result).size();
                int expectedLastColumn =
                        ExcelDatasetSheetWriter.fields(
                                definition, result, binding).size() - 1;
                if (table.getArea().getFirstCell().getRow()
                                != startRow
                        || table.getArea().getFirstCell().getCol() != 0
                        || table.getArea().getLastCell().getRow()
                                != expectedLastRow
                        || table.getArea().getLastCell().getCol()
                                != expectedLastColumn) {
                    throw new IllegalStateException(
                            "Dataset table range is invalid: "
                                    + definition.getSheetName());
                }
            }
        }
    }

    private static XSSFTable targetTable(
            XSSFSheet sheet,
            ExcelTableBinding binding,
            int startRow,
            String sheetName) {
        XSSFTable match = null;
        String configuredName = binding == null
                ? null : binding.getTable();
        for (XSSFTable candidate : sheet.getTables()) {
            boolean candidateMatches;
            if (configuredName != null
                    && !configuredName.trim().isEmpty()) {
                candidateMatches = configuredName.equalsIgnoreCase(
                        candidate.getName());
            } else {
                candidateMatches =
                        candidate.getArea().getFirstCell().getRow()
                                == startRow
                        && candidate.getArea().getFirstCell().getCol()
                                == 0;
            }
            if (!candidateMatches) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException(
                        "Multiple dataset tables match sheet: "
                                + sheetName);
            }
            match = candidate;
        }
        if (match == null) {
            throw new IllegalStateException(
                    "Missing dataset table: " + sheetName);
        }
        return match;
    }

    private static void validateUniqueTableNames(
            XSSFWorkbook workbook) {
        Set<String> names = new LinkedHashSet<String>();
        for (int sheetIndex = 0;
                sheetIndex < workbook.getNumberOfSheets();
                sheetIndex++) {
            XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
            for (XSSFTable table : sheet.getTables()) {
                String normalized =
                        ExcelTableNameRules.normalized(
                                table.getName());
                if (!names.add(normalized)) {
                    throw new IllegalStateException(
                            "Duplicate Excel table name: "
                                    + table.getName());
                }
            }
        }
    }
}
