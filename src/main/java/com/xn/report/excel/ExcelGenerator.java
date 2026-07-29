package com.xn.report.excel;

import com.xn.report.analysis.AnalysisContext;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.RootPathPolicy;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.execution.ExecutionContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class ExcelGenerator {

    private final ExcelWorkbookWriter workbookWriter;

    public ExcelGenerator() {
        this(new ExcelWorkbookWriter());
    }

    ExcelGenerator(ExcelWorkbookWriter workbookWriter) {
        this.workbookWriter =
                Objects.requireNonNull(workbookWriter, "workbookWriter");
    }

    public Path generate(
            ReportDefinition definition,
            AnalysisContext analysis,
            ExecutionContext execution) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(execution, "execution");
        String templateName = definition.getReport().getExcelTemplate();
        if (templateName == null || templateName.trim().isEmpty()) {
            throw new ReportException(
                    ReportErrorCode.XLSX_001,
                    "Excel template is required");
        }
        Path template = new RootPathPolicy(
                execution.getRequest().getTemplateRoot())
                .resolve(templateName);
        Path output = execution.getWorkspace().getExcelDirectory()
                .resolve("report.xlsx").toAbsolutePath().normalize();
        execution.getWorkspace().assertOwned(output);
        try {
            workbookWriter.write(
                    template,
                    output,
                    definition,
                    analysis.getDatasetContext(),
                    execution.getRequest().getRuntimeParameters());
            return output;
        } catch (IOException exception) {
            throw new ReportException(
                    ReportErrorCode.XLSX_001,
                    "Unable to generate Excel output",
                    execution.getExecutionId(),
                    execution.getStage().name(),
                    definition.getReport().getCode(),
                    null,
                    exception);
        } catch (ReportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReportException(
                    ReportErrorCode.XLSX_001,
                    "Unable to generate Excel output",
                    execution.getExecutionId(),
                    execution.getStage().name(),
                    definition.getReport().getCode(),
                    null,
                    exception);
        }
    }
}
