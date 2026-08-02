package com.xn.report.word;

import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordNumberingDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.config.definition.WordDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.text.NarrativeResult;
import java.util.Collections;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public final class WordSectionRenderer {

    private final WordSectionAnchorLocator sectionAnchorLocator;

    public WordSectionRenderer() {
        this(new WordSectionAnchorLocator());
    }

    WordSectionRenderer(WordSectionAnchorLocator sectionAnchorLocator) {
        this.sectionAnchorLocator = sectionAnchorLocator;
    }

    public void render(
            XWPFDocument document,
            List<WordSectionDefinition> sections,
            WordRenderContext context) {
        render(document, sections, context, new WordComponentRenderer(),
                new WordNumberingDefinition());
    }

    public void render(
            XWPFDocument document,
            WordDefinition definition,
            WordRenderContext context) {
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Word definition is required");
        }
        render(document, definition.getSections(), context,
                new WordComponentRenderer(definition.getTableBindings()),
                definition.getNumbering());
    }

    private void render(
            XWPFDocument document,
            List<WordSectionDefinition> sections,
            WordRenderContext context,
            WordComponentRenderer componentRenderer,
            WordNumberingDefinition numberingDefinition) {
        if (document == null || context == null) {
            throw new IllegalArgumentException(
                    "Word document and render context are required");
        }
        XWPFParagraph anchor = sectionAnchorLocator.locate(document);
        WordBodyInserter inserter = new WordBodyInserter(document, anchor);
        WordNumberingManager numbering =
                new WordNumberingManager(document, numberingDefinition);
        for (WordSectionDefinition section : safe(sections)) {
            renderSection(document, inserter, numbering, section, context,
                    componentRenderer);
        }
        int position = document.getPosOfParagraph(anchor);
        if (position < 0 || !document.removeBodyElement(position)) {
            throw new WordTemplateException(
                    "Unable to remove {{sections}} anchor");
        }
    }

    private void renderSection(
            XWPFDocument document,
            WordBodyInserter inserter,
            WordNumberingManager numbering,
            WordSectionDefinition section,
            WordRenderContext context,
            WordComponentRenderer componentRenderer) {
        boolean empty = isEmpty(section, context, componentRenderer);
        String strategy = section.getEmptyStrategy() == null
                ? "KEEP" : section.getEmptyStrategy();
        if (empty && "SKIP".equals(strategy)) {
            return;
        }
        XWPFParagraph heading = inserter.paragraph();
        heading.setStyle("Heading" + section.getLevel());
        heading.createRun().setText(section.getTitle());
        numbering.apply(heading, section.getLevel());
        if (empty && "SHOW_EMPTY".equals(strategy)) {
            inserter.paragraph().createRun().setText(
                    section.getEmptyMessage() == null
                            ? "暂无数据" : section.getEmptyMessage());
        } else {
            for (WordComponentDefinition component :
                    safeComponents(section.getComponents())) {
                componentRenderer.render(
                        document, inserter, component, context);
            }
        }
        for (WordSectionDefinition child : safe(section.getChildren())) {
            renderSection(document, inserter, numbering, child, context,
                    componentRenderer);
        }
    }

    private boolean isEmpty(
            WordSectionDefinition section,
            WordRenderContext context,
            WordComponentRenderer componentRenderer) {
        for (WordComponentDefinition component :
                safeComponents(section.getComponents())) {
            String type = component.getType();
            if ("TABLE".equals(type)) {
                DatasetResult result =
                        componentRenderer.datasetFor(component, context);
                if (!datasetEmpty(result)) {
                    return false;
                }
            } else if ("RULE_TEXT".equals(type)) {
                NarrativeResult result =
                        context.narrative(component.getNarrativeId());
                if (result != null && !result.skipped()
                        && !result.text().trim().isEmpty()) {
                    return false;
                }
            } else if ("CHART".equals(type)) {
                if (!context.charts(component.getChartId()).isEmpty()) {
                    return false;
                }
            } else if (component.getText() != null
                    && !component.getText().trim().isEmpty()) {
                return false;
            } else if ("ATTACHMENT".equals(type)
                    && (hasText(component.getTitle())
                    || hasText(component.getDescription())
                    || !component.getItems().isEmpty())) {
                return false;
            }
        }
        return true;
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

    private static List<WordSectionDefinition> safe(
            List<WordSectionDefinition> values) {
        return values == null
                ? Collections.<WordSectionDefinition>emptyList() : values;
    }

    private static List<WordComponentDefinition> safeComponents(
            List<WordComponentDefinition> values) {
        return values == null
                ? Collections.<WordComponentDefinition>emptyList() : values;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
