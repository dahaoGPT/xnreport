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
                if (sheet.getTables().size() != 1) {
                    throw new IllegalStateException(
                            "Dataset sheet must contain exactly one table: "
                                    + definition.getSheetName());
                }
                XSSFTable table = sheet.getTables().get(0);
                ExcelTableBinding binding =
                        bindings.get(definition.getId());
                int startRow = binding == null
                        ? 0 : binding.getStartRow().intValue();
                int expectedLastRow =
                        startRow
                                + ExcelDatasetSheetWriter.rows(result).size();
                int expectedLastColumn =
                        (binding == null
                                ? ExcelDatasetSheetWriter.fields(
                                        definition, result).size()
                                : binding.getColumns().size()) - 1;
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
}
