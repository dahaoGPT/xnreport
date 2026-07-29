package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.text.NarrativeResult;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

/**
 * Renders one configured component without changing the surrounding section
 * order. Section traversal and empty-section policy remain the responsibility
 * of {@link WordSectionRenderer}.
 */
public final class WordComponentRenderer {

    private final WordTableWriter tableWriter;
    private final WordImageWriter imageWriter;
    private final WordAttachmentWriter attachmentWriter;

    public WordComponentRenderer() {
        this(new WordTableWriter(), new WordImageWriter(),
                new WordAttachmentWriter());
    }

    WordComponentRenderer(
            WordTableWriter tableWriter,
            WordImageWriter imageWriter,
            WordAttachmentWriter attachmentWriter) {
        this.tableWriter = tableWriter;
        this.imageWriter = imageWriter;
        this.attachmentWriter = attachmentWriter;
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
            DatasetResult dataset = dataset(component, context);
            XWPFTable table = inserter.table();
            tableWriter.fillGenerated(
                    table, dataset, component.getColumns(),
                    component.getEmptyMessage());
            return;
        }
        if ("CHART".equals(type)) {
            RenderedChart chart = context.chart(component.getChartId());
            if (chart == null) {
                throw new WordTemplateException(
                        "Word chart is missing rendered image: "
                                + component.getChartId());
            }
            XWPFParagraph paragraph = inserter.paragraph();
            imageWriter.write(document, paragraph, chart, component);
            return;
        }
        if ("ATTACHMENT".equals(type)) {
            attachmentWriter.append(inserter, component);
            return;
        }
        throw new WordTemplateException(
                "Unsupported Word component type: " + type);
    }

    static DatasetResult dataset(
            WordComponentDefinition component, WordRenderContext context) {
        String datasetId = hasText(component.getDataset())
                ? component.getDataset() : component.getTableId();
        if (!hasText(datasetId) || !context.datasets().contains(datasetId)) {
            throw new WordTemplateException(
                    "Word table references missing dataset: " + datasetId);
        }
        return context.datasets().get(datasetId);
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
