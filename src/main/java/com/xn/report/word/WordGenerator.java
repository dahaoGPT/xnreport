package com.xn.report.word;

import com.xn.report.analysis.AnalysisContext;
import com.xn.report.chart.RenderedChart;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.RootPathPolicy;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordCoverDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.config.definition.WordTocDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.execution.ExecutionContext;
import com.xn.report.text.NarrativeResult;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

public class WordGenerator {

    private final WordTemplateLoader templateLoader;
    private final WordCoverBinder coverBinder;
    private final WordTocManager tocManager;
    private final WordSectionRenderer sectionRenderer;
    private final WordOutputValidator outputValidator;
    private final WordRunTextReplacer textReplacer;
    private final WordTemplateChartBinder templateChartBinder;

    public WordGenerator() {
        this(
                new WordTemplateLoader(),
                new WordCoverBinder(new WordRunTextReplacer()),
                new WordTocManager(),
                new WordSectionRenderer(),
                new WordOutputValidator(),
                new WordRunTextReplacer(),
                new WordTemplateChartBinder());
    }

    WordGenerator(
            WordTemplateLoader templateLoader,
            WordCoverBinder coverBinder,
            WordTocManager tocManager,
            WordSectionRenderer sectionRenderer,
            WordOutputValidator outputValidator,
            WordRunTextReplacer textReplacer,
            WordTemplateChartBinder templateChartBinder) {
        this.templateLoader =
                Objects.requireNonNull(templateLoader, "templateLoader");
        this.coverBinder = Objects.requireNonNull(coverBinder, "coverBinder");
        this.tocManager = Objects.requireNonNull(tocManager, "tocManager");
        this.sectionRenderer =
                Objects.requireNonNull(sectionRenderer, "sectionRenderer");
        this.outputValidator =
                Objects.requireNonNull(outputValidator, "outputValidator");
        this.textReplacer =
                Objects.requireNonNull(textReplacer, "textReplacer");
        this.templateChartBinder =
                Objects.requireNonNull(templateChartBinder, "templateChartBinder");
    }

    public Path generate(
            ReportDefinition definition,
            AnalysisContext analysis,
            ExecutionContext execution) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(execution, "execution");
        String templateName = definition.getReport().getWordTemplate();
        if (templateName == null || templateName.trim().isEmpty()) {
            throw wordFailure(
                    definition, execution,
                    "Word template is required", null);
        }
        Path template = new RootPathPolicy(
                execution.getRequest().getTemplateRoot())
                .resolve(templateName);
        Path output = execution.getWorkspace().getWordDirectory()
                .resolve("report.docx").toAbsolutePath().normalize();
        execution.getWorkspace().assertOwned(output);
        try (XWPFDocument document = templateLoader.load(template)) {
            coverBinder.bind(document, definition.getWord().getCover());
            tocManager.configure(
                    document,
                    definition.getWord().getToc().getMaxLevel(),
                    definition.getWord().getToc().isUpdateOnOpen());
            bindTemplateValues(document, analysis);
            bindTemplateCharts(document, definition, analysis);
            sectionRenderer.render(
                    document,
                    definition.getWord(),
                    renderContext(analysis));
            WordOutputExpectation expectation =
                    captureExpectation(document, definition);
            Files.createDirectories(output.getParent());
            try (OutputStream stream = Files.newOutputStream(
                    output,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                document.write(stream);
            }
            outputValidator.validate(output, expectation);
            return output;
        } catch (ReportException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            throw wordFailure(
                    definition, execution,
                    "Unable to generate Word output", exception);
        }
    }

    private static WordRenderContext renderContext(AnalysisContext analysis) {
        WordRenderContext.Builder builder = WordRenderContext.builder()
                .datasets(analysis.getDatasetContext());
        for (Map.Entry<String, NarrativeResult> item
                : analysis.getNarratives().entrySet()) {
            builder.narrative(item.getKey(), item.getValue());
        }
        for (Map.Entry<String, RenderedChart> item
                : analysis.getRenderedCharts().entrySet()) {
            builder.chart(item.getKey(), item.getValue());
        }
        return builder.build();
    }

    private void bindTemplateValues(
            XWPFDocument document, AnalysisContext analysis) {
        for (Map.Entry<String, NarrativeResult> item
                : analysis.getNarratives().entrySet()) {
            textReplacer.replace(
                    document,
                    "{{text:" + item.getKey() + "}}",
                    item.getValue().text());
        }
        for (String datasetId
                : analysis.getDatasetContext().ids()) {
            DatasetResult dataset =
                    analysis.getDatasetContext().get(datasetId);
            if (dataset.type() == DatasetType.LIST) {
                continue;
            }
            DatasetRow row = dataset.type() == DatasetType.SINGLE
                    ? dataset.single() : scalarRow(dataset);
            if (row == null) {
                continue;
            }
            for (String field : row.fieldNames()) {
                Object value = row.getOrNull(field);
                textReplacer.replace(
                        document,
                        "{{value:" + datasetId + "." + field + "}}",
                        value == null ? "" : String.valueOf(value));
            }
        }
    }

    private static DatasetRow scalarRow(DatasetResult dataset) {
        Object value = dataset.scalar();
        if (value == null) {
            return null;
        }
        String field = dataset.schema().fieldNames().isEmpty()
                ? "value" : dataset.schema().fieldNames().get(0);
        return DatasetRow.of(field, value);
    }

    private void bindTemplateCharts(
            XWPFDocument document,
            ReportDefinition definition,
            AnalysisContext analysis) {
        for (Map.Entry<String, RenderedChart> item
                : analysis.getRenderedCharts().entrySet()) {
            String marker = "{{chart:" + item.getKey() + "}}";
            int count = WordPackageTextScanner.count(document, marker);
            if (count == 0) {
                continue;
            }
            WordComponentDefinition component =
                    findChartComponent(
                            definition.getWord().getSections(), item.getKey());
            if (component == null) {
                component = new WordComponentDefinition();
                component.setType("CHART");
                component.setChartId(item.getKey());
            }
            templateChartBinder.bind(
                    document, item.getKey(), item.getValue(), component);
        }
    }

    private static WordComponentDefinition findChartComponent(
            List<WordSectionDefinition> sections, String chartId) {
        for (WordSectionDefinition section : sections) {
            for (WordComponentDefinition component : section.getComponents()) {
                if ("CHART".equals(component.getType())
                        && chartId.equals(component.getChartId())) {
                    return component;
                }
            }
            WordComponentDefinition found =
                    findChartComponent(section.getChildren(), chartId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static WordOutputExpectation captureExpectation(
            XWPFDocument document, ReportDefinition definition) {
        WordCoverDefinition cover = definition.getWord().getCover();
        WordTocDefinition toc = definition.getWord().getToc();
        WordOutputExpectation.Builder expected =
                WordOutputExpectation.builder()
                        .cover(
                                cover.getTitle(),
                                cover.getOrganization(),
                                cover.getReportPeriod(),
                                cover.getPreparedBy(),
                                cover.getPreparedDate())
                        .tocMaxLevel(toc.getMaxLevel())
                        .requireUpdateFields(toc.isUpdateOnOpen());
        boolean dynamicStarted = false;
        int tableIndex = 0;
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                int level = dynamicHeadingLevel(paragraph);
                if (level > 0) {
                    dynamicStarted = true;
                    expected.heading(level, paragraph.getText());
                }
            } else if (dynamicStarted && element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                expected.table(
                        tableIndex++,
                        table.getNumberOfRows(),
                        tableValues(table));
            }
        }
        captureAttachments(definition.getWord().getSections(), document, expected);
        expected.pictureInstances(pictureOccurrences(document));
        return expected.build();
    }

    private static int dynamicHeadingLevel(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null || !style.matches("Heading[1-4]")
                || !paragraph.getCTP().isSetPPr()
                || !paragraph.getCTP().getPPr().isSetNumPr()
                || !paragraph.getCTP().getPPr().getNumPr().isSetIlvl()) {
            return 0;
        }
        BigInteger level = paragraph.getCTP().getPPr()
                .getNumPr().getIlvl().getVal();
        if (level == null || level.intValue() < 0
                || level.intValue() > 3) {
            return 0;
        }
        return level.intValue() + 1;
    }

    private static List<String> tableValues(XWPFTable table) {
        List<String> values = new ArrayList<String>();
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                String value = cell.getText();
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static void captureAttachments(
            List<WordSectionDefinition> sections,
            XWPFDocument document,
            WordOutputExpectation.Builder expectation) {
        String text = bodyText(document);
        for (WordSectionDefinition section : sections) {
            for (WordComponentDefinition component : section.getComponents()) {
                if ("ATTACHMENT".equals(component.getType())
                        && attachmentPresent(component, text)) {
                    expectation.attachment(
                            component.getTitle(),
                            component.getDescription(),
                            component.getItems());
                }
            }
            captureAttachments(section.getChildren(), document, expectation);
        }
    }

    private static boolean attachmentPresent(
            WordComponentDefinition component, String text) {
        if (component.getTitle() != null
                && text.contains(component.getTitle())) {
            return true;
        }
        if (component.getDescription() != null
                && text.contains(component.getDescription())) {
            return true;
        }
        return !component.getItems().isEmpty()
                && text.contains(component.getItems().get(0));
    }

    private static String bodyText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            text.append(paragraph.getText()).append('\n');
        }
        for (XWPFTable table : document.getTables()) {
            text.append(table.getText()).append('\n');
        }
        return text.toString();
    }

    private static int pictureOccurrences(IBody body) {
        int pictures = 0;
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                for (XWPFRun run : ((XWPFParagraph) element).getRuns()) {
                    pictures += run.getEmbeddedPictures().size();
                }
            } else if (element instanceof XWPFTable) {
                for (XWPFTableRow row
                        : ((XWPFTable) element).getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        pictures += pictureOccurrences(cell);
                    }
                }
            }
        }
        return pictures;
    }

    private static ReportException wordFailure(
            ReportDefinition definition,
            ExecutionContext execution,
            String message,
            Throwable cause) {
        return new ReportException(
                ReportErrorCode.DOCX_001,
                message,
                execution.getExecutionId(),
                execution.getStage().name(),
                definition.getReport().getCode(),
                null,
                cause);
    }
}
