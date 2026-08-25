package com.xn.report.word;

import com.xn.report.analysis.AnalysisContext;
import com.xn.report.chart.RenderedChart;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.RootPathPolicy;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordCoverDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.config.definition.WordTocDefinition;
import com.xn.report.config.definition.WordTableBinding;
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

/**
 * Word 报表文档生成顶层执行器。
 * <p>
 * 串联报表上下文、模板文件与所有组件渲染管道：
 * <ol>
 *   <li>通过 {@link WordTemplateLoader} 加载并校验模板（标题样式 Heading1~4、TOC、<code>{{sections}}</code> 锚点）。</li>
 *   <li>求值并绑定文档封面占位符（{@link WordCoverValueResolver} / {@link WordCoverBinder}）。</li>
 *   <li>配置目录层级与打开时自动更新字段标记（{@link WordTocManager}）。</li>
 *   <li>替换模板全局标量值与分析叙述文本（<code>{{text:id}}</code>、<code>{{value:dataset.field}}</code>）。</li>
 *   <li>绑定模板静态图表占位符（<code>{{chart:id}}</code>）。</li>
 *   <li>在 <code>{{sections}}</code> 锚点处深度优先展开章节与各类多态组件（{@link WordSectionRenderer}）。</li>
 *   <li>写出 report.docx 并通过 {@link WordOutputValidator} 执行全方位质检校验。</li>
 * </ol>
 * </p>
 */
public class WordGenerator {

    private final WordTemplateLoader templateLoader;
    private final WordCoverBinder coverBinder;
    private final WordTocManager tocManager;
    private final WordSectionRenderer sectionRenderer;
    private final WordOutputValidator outputValidator;
    private final WordRunTextReplacer textReplacer;
    private final WordTemplateChartBinder templateChartBinder;
    private final WordCoverValueResolver coverValueResolver;

    public WordGenerator() {
        this(
                new WordTemplateLoader(),
                new WordCoverBinder(new WordRunTextReplacer()),
                new WordTocManager(),
                new WordSectionRenderer(),
                new WordOutputValidator(),
                new WordRunTextReplacer(),
                new WordTemplateChartBinder(),
                new WordCoverValueResolver());
    }

    WordGenerator(
            WordTemplateLoader templateLoader,
            WordCoverBinder coverBinder,
            WordTocManager tocManager,
            WordSectionRenderer sectionRenderer,
            WordOutputValidator outputValidator,
            WordRunTextReplacer textReplacer,
            WordTemplateChartBinder templateChartBinder,
            WordCoverValueResolver coverValueResolver) {
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
        this.coverValueResolver =
                Objects.requireNonNull(coverValueResolver, "coverValueResolver");
    }

    /**
     * 生成 Word 报表产物文件。
     *
     * @param definition 报表配置定义
     * @param analysis 分析计算上下文
     * @param execution 任务执行上下文
     * @return 生成的 report.docx 文件绝对路径
     */
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
            int templateChartInstances = expectedTemplateChartInstances(
                    document, analysis);
            WordCoverDefinition resolvedCover = coverValueResolver.resolve(
                    definition.getWord().getCover(),
                    execution.getRequest().getRuntimeParameters(),
                    definition.getPolicies());
            coverBinder.bind(document, resolvedCover);
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
            WordOutputExpectation expectation = expectationFromConfiguration(
                    definition, resolvedCover, analysis,
                    templateChartInstances);
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
        java.util.Set<String> consumed = new java.util.LinkedHashSet<String>();
        for (Map.Entry<String, RenderedChart> item
                : analysis.getRenderedCharts().entrySet()) {
            if (consumed.contains(item.getKey())) {
                continue;
            }
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
            List<RenderedChart> charts = new ArrayList<RenderedChart>();
            String prefix = item.getKey() + "::";
            for (Map.Entry<String, RenderedChart> candidate
                    : analysis.getRenderedCharts().entrySet()) {
                if (candidate.getKey().equals(item.getKey())
                        || candidate.getKey().startsWith(prefix)) {
                    charts.add(candidate.getValue());
                    consumed.add(candidate.getKey());
                }
            }
            templateChartBinder.bindAll(
                    document, item.getKey(), charts, component);
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

    private static WordOutputExpectation expectationFromConfiguration(
            ReportDefinition definition,
            WordCoverDefinition cover,
            AnalysisContext analysis,
            int templateChartInstances) {
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
        ExpectedStructure structure = new ExpectedStructure(
                expected, renderContext(analysis), templateChartInstances,
                definition.getWord().getTableBindings());
        structure.sections(definition.getWord().getSections());
        expected.pictureInstances(structure.pictureInstances);
        return expected.build();
    }

    private static int expectedTemplateChartInstances(
            XWPFDocument document, AnalysisContext analysis) {
        int count = 0;
        for (String id : analysis.getRenderedCharts().keySet()) {
            int markers = WordPackageTextScanner.count(
                    document, "{{chart:" + id + "}}");
            if (markers == 0) continue;
            int logicalInstances = 0;
            String prefix = id + "::";
            for (String candidate : analysis.getRenderedCharts().keySet()) {
                if (candidate.equals(id) || candidate.startsWith(prefix)) {
                    logicalInstances++;
                }
            }
            count += markers * logicalInstances;
        }
        return count;
    }

    private static final class ExpectedStructure {
        private final WordOutputExpectation.Builder expectation;
        private final WordRenderContext context;
        private final Map<String, WordTableBinding> tableBindings =
                new java.util.LinkedHashMap<String, WordTableBinding>();
        private int pictureInstances;
        private int tableIndex;

        private ExpectedStructure(
                WordOutputExpectation.Builder expectation,
                WordRenderContext context,
                int pictureInstances,
                List<WordTableBinding> bindings) {
            this.expectation = expectation;
            this.context = context;
            this.pictureInstances = pictureInstances;
            for (WordTableBinding binding : bindings) {
                tableBindings.put(binding.getId(), binding);
            }
        }

        private void sections(List<WordSectionDefinition> sections) {
            for (WordSectionDefinition section : sections) {
                boolean empty = sectionEmpty(
                        section, context, tableBindings);
                String strategy = section.getEmptyStrategy() == null
                        ? "KEEP" : section.getEmptyStrategy();
                if (empty && "SKIP".equals(strategy)) {
                    continue;
                }
                expectation.heading(section.getLevel(), section.getTitle());
                if (!(empty && "SHOW_EMPTY".equals(strategy))) {
                    for (WordComponentDefinition component
                            : section.getComponents()) {
                        if ("CHART".equals(component.getType())) {
                            pictureInstances += context.charts(
                                    component.getChartId()).size();
                        } else if ("ATTACHMENT".equals(component.getType())) {
                            expectation.attachment(component.getTitle(),
                                    component.getDescription(),
                                    component.getItems());
                        } else if ("TABLE".equals(component.getType())) {
                            DatasetResult dataset = tableDataset(
                                    component, context, tableBindings);
                            expectation.tablePresence(tableIndex++,
                                    java.util.Collections.<String>emptyList());
                        }
                    }
                }
                sections(section.getChildren());
            }
        }
    }

    private static DatasetResult tableDataset(
            WordComponentDefinition component,
            WordRenderContext context,
            Map<String, WordTableBinding> bindings) {
        WordTableBinding binding = bindings.get(component.getTableId());
        String datasetId = binding == null
                ? component.getDataset() : binding.getDataset();
        return context.datasets().get(datasetId);
    }


    private static boolean sectionEmpty(
            WordSectionDefinition section,
            WordRenderContext context,
            Map<String, WordTableBinding> bindings) {
        for (WordComponentDefinition component : section.getComponents()) {
            if ("CHART".equals(component.getType())) {
                if (!context.charts(component.getChartId()).isEmpty()) {
                    return false;
                }
            } else if ("RULE_TEXT".equals(component.getType())) {
                NarrativeResult result = context.narrative(
                        component.getNarrativeId());
                if (result != null && !result.skipped()
                        && !result.text().trim().isEmpty()) {
                    return false;
                }
            } else if ("ATTACHMENT".equals(component.getType())) {
                if (hasText(component.getTitle())
                        || hasText(component.getDescription())
                        || !component.getItems().isEmpty()) {
                    return false;
                }
            } else if ("TABLE".equals(component.getType())) {
                WordTableBinding binding = bindings.get(
                        component.getTableId());
                String id = binding == null
                        ? component.getDataset() : binding.getDataset();
                if (hasText(id) && context.datasets().contains(id)
                        && !datasetEmpty(context.datasets().get(id))) {
                    return false;
                }
            } else if (hasText(component.getText())) {
                return false;
            }
        }
        return true;
    }

    private static boolean datasetEmpty(DatasetResult result) {
        if (result.type() == DatasetType.LIST) return result.list().isEmpty();
        if (result.type() == DatasetType.SINGLE) return result.single() == null;
        return result.scalar() == null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
