package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordTableBinding;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.text.NarrativeResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;

/**
 * Renders one configured component without changing the surrounding section
 * order. Section traversal and empty-section policy remain the responsibility
 * of {@link WordSectionRenderer}.
 */
public final class WordComponentRenderer {

    private final WordTableWriter tableWriter;
    private final WordImageWriter imageWriter;
    private final WordAttachmentWriter attachmentWriter;
    private final Map<String, WordTableBinding> tableBindings;
    private final Map<String, CTTbl> bindingTemplates;

    public WordComponentRenderer() {
        this(new WordTableWriter(), new WordImageWriter(),
                new WordAttachmentWriter(),
                Collections.<WordTableBinding>emptyList());
    }

    public WordComponentRenderer(List<WordTableBinding> bindings) {
        this(new WordTableWriter(), new WordImageWriter(),
                new WordAttachmentWriter(), bindings);
    }

    WordComponentRenderer(
            WordTableWriter tableWriter,
            WordImageWriter imageWriter,
            WordAttachmentWriter attachmentWriter,
            List<WordTableBinding> bindings) {
        this.tableWriter = tableWriter;
        this.imageWriter = imageWriter;
        this.attachmentWriter = attachmentWriter;
        this.tableBindings =
                new LinkedHashMap<String, WordTableBinding>();
        this.bindingTemplates = new LinkedHashMap<String, CTTbl>();
        for (WordTableBinding binding : bindings == null
                ? Collections.<WordTableBinding>emptyList() : bindings) {
            if (binding != null && binding.getId() != null) {
                this.tableBindings.put(binding.getId(), binding);
            }
        }
    }

    void render(
            XWPFDocument document,
            WordBodyInserter inserter,
            WordComponentDefinition component,
            WordRenderContext context) {
        String type = component.getType();
        if ("SCENARIO".equals(type)
                || "KEY_FACTORS".equals(type)
                || "FIXED_TEXT".equals(type)
                || "UNIT".equals(type)) {
            addText(inserter, component.getText());
            return;
        }
        if ("RULE_TEXT".equals(type)) {
            NarrativeResult narrative =
                    context.narrative(component.getNarrativeId());
            if (narrative != null && !narrative.skipped()) {
                addText(inserter, narrative.text());
            }
            return;
        }
        if ("TABLE".equals(type)) {
            WordTableBinding binding =
                    tableBindings.get(component.getTableId());
            if (binding != null) {
                renderBinding(document, inserter, binding, context);
                return;
            }
            if (hasText(component.getTableId())) {
                throw new WordTemplateException(
                        "Word table references missing binding: "
                                + component.getTableId());
            }
            DatasetResult dataset = datasetFor(component, context);
            XWPFTable table = inserter.table();
            tableWriter.fillGenerated(
                    table, dataset, component.getColumns(),
                    component.getEmptyMessage());
            return;
        }
        if ("CHART".equals(type)) {
            List<RenderedChart> charts = context.charts(component.getChartId());
            if (charts.isEmpty()) {
                throw new WordTemplateException(
                        "Word chart is missing rendered image: "
                                + component.getChartId());
            }
            for (RenderedChart chart : charts) {
                XWPFParagraph paragraph = inserter.paragraph();
                imageWriter.write(document, paragraph, chart, component);
            }
            return;
        }
        if ("ATTACHMENT".equals(type)) {
            attachmentWriter.append(inserter, component);
            return;
        }
        throw new WordTemplateException(
                "Unsupported Word component type: " + type);
    }

    DatasetResult datasetFor(
            WordComponentDefinition component, WordRenderContext context) {
        WordTableBinding binding = tableBindings.get(component.getTableId());
        String datasetId = binding == null
                ? component.getDataset() : binding.getDataset();
        if (!hasText(datasetId) || !context.datasets().contains(datasetId)) {
            throw new WordTemplateException(
                    "Word table references missing dataset: " + datasetId);
        }
        return context.datasets().get(datasetId);
    }

    private void renderBinding(
            XWPFDocument document,
            WordBodyInserter inserter,
            WordTableBinding binding,
            WordRenderContext context) {
        if (!context.datasets().contains(binding.getDataset())) {
            throw new WordTemplateException(
                    "Word table binding references missing dataset: "
                            + binding.getDataset());
        }
        DatasetResult dataset = context.datasets().get(binding.getDataset());
        CTTbl template = bindingTemplates.get(binding.getId());
        String marker = hasText(binding.getMarker())
                ? binding.getMarker()
                : "{{table:" + binding.getTableId() + "}}";
        if (template == null) {
            LocatedTable located = locateTable(document, marker,
                    "PROTOTYPE".equals(binding.getStrategy()));
            template = CTTbl.Factory.newInstance();
            template.set(located.table.getCTTbl());
            bindingTemplates.put(binding.getId(), template);
            removeLocated(document, located);
        }
        if (datasetEmpty(dataset)
                && "SKIP".equals(binding.getEmptyStrategy())) {
            return;
        }
        XWPFTable table = inserter.table();
        if ("PROTOTYPE".equals(binding.getStrategy())) {
            CTTbl workingXml = CTTbl.Factory.newInstance();
            workingXml.set(template);
            XWPFTable working = new XWPFTable(workingXml, document);
            removeMarkerFromTable(working, marker);
            tableWriter.bindPrototype(
                    working, dataset, binding.getEmptyMessage());
            table.getCTTbl().set(working.getCTTbl());
        } else if ("GENERATED".equals(binding.getStrategy())) {
            tableWriter.fillGenerated(
                    table, dataset, binding.getColumns(),
                    binding.getEmptyMessage());
        } else {
            throw new WordTemplateException(
                    "Unsupported Word table binding strategy: "
                            + binding.getStrategy());
        }
    }

    private static LocatedTable locateTable(
            XWPFDocument document,
            String marker,
            boolean prototype) {
        LocatedTable found = null;
        List<IBodyElement> elements = document.getBodyElements();
        for (int index = 0; index < elements.size(); index++) {
            IBodyElement element = elements.get(index);
            if (element instanceof XWPFParagraph
                    && marker.equals(((XWPFParagraph) element)
                    .getText().trim())) {
                XWPFTable table = null;
                if (index + 1 < elements.size()
                        && elements.get(index + 1) instanceof XWPFTable) {
                    table = (XWPFTable) elements.get(index + 1);
                } else if (!prototype) {
                    WordBodyInserter inserter = new WordBodyInserter(
                            document, (XWPFParagraph) element);
                    table = inserter.table();
                }
                if (table == null) {
                    throw new WordTemplateException(
                            "Word table marker is not followed by a table: "
                                    + marker);
                }
                found = unique(found,
                        new LocatedTable(table, (XWPFParagraph) element),
                        marker);
            } else if (element instanceof XWPFTable
                    && ((XWPFTable) element).getText().contains(marker)) {
                found = unique(found,
                        new LocatedTable((XWPFTable) element, null), marker);
            }
        }
        if (found == null) {
            throw new WordTemplateException(
                    "Word template is missing table marker " + marker);
        }
        return found;
    }

    private static LocatedTable unique(
            LocatedTable current, LocatedTable candidate, String marker) {
        if (current != null && current.table != candidate.table) {
            throw new WordTemplateException(
                    "Word template contains multiple table markers " + marker);
        }
        return candidate;
    }

    private static void removeMarkerFromTable(
            XWPFTable table, String marker) {
        WordRunTextReplacer replacer = new WordRunTextReplacer();
        for (org.apache.poi.xwpf.usermodel.XWPFTableRow row
                : table.getRows()) {
            for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell
                    : row.getTableCells()) {
                replacer.replaceInBody(
                        cell, Collections.singletonMap(marker, ""));
            }
        }
    }

    private static void removeLocated(
            XWPFDocument document, LocatedTable located) {
        if (located.markerParagraph != null) {
            int marker =
                    document.getPosOfParagraph(located.markerParagraph);
            document.removeBodyElement(marker);
        }
        int table = document.getPosOfTable(located.table);
        if (table >= 0) {
            document.removeBodyElement(table);
        }
    }

    private static boolean datasetEmpty(DatasetResult result) {
        switch (result.type()) {
            case LIST:
                return result.list().isEmpty();
            case SINGLE:
                return result.single() == null;
            case SCALAR:
                return result.scalar() == null;
            default:
                return true;
        }
    }

    private static final class LocatedTable {
        private final XWPFTable table;
        private final XWPFParagraph markerParagraph;

        private LocatedTable(
                XWPFTable table, XWPFParagraph markerParagraph) {
            this.table = table;
            this.markerParagraph = markerParagraph;
        }
    }

    private static void addText(
            WordBodyInserter inserter, String value) {
        if (value != null && !value.trim().isEmpty()) {
            inserter.paragraph().createRun().setText(value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
