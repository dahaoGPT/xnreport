package com.xn.report.word;

import com.xn.report.config.definition.WordComponentDefinition;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public final class WordAttachmentWriter {

    public void append(
            XWPFDocument document, WordComponentDefinition definition) {
        if (document == null || definition == null) {
            throw new IllegalArgumentException(
                    "Word document and attachment definition are required");
        }
        appendText(document, title(definition), true);
        appendText(document, definition.getDescription(), false);
        for (String item : definition.getItems()) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("ListBullet");
            paragraph.createRun().setText(item);
        }
    }

    void append(
            WordBodyInserter inserter, WordComponentDefinition definition) {
        if (inserter == null || definition == null) {
            throw new IllegalArgumentException(
                    "Word inserter and attachment definition are required");
        }
        appendText(inserter, title(definition), true);
        appendText(inserter, definition.getDescription(), false);
        for (String item : definition.getItems()) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            XWPFParagraph paragraph = inserter.paragraph();
            paragraph.setStyle("ListBullet");
            paragraph.createRun().setText(item);
        }
    }

    static void appendText(
            XWPFDocument document, String text, boolean bold) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setBold(bold);
        paragraph.getRuns().get(0).setText(text);
    }

    private static void appendText(
            WordBodyInserter inserter, String text, boolean bold) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        XWPFParagraph paragraph = inserter.paragraph();
        paragraph.createRun().setBold(bold);
        paragraph.getRuns().get(0).setText(text);
    }

    private static String title(WordComponentDefinition definition) {
        return definition.getTitle() == null
                || definition.getTitle().trim().isEmpty()
                ? definition.getText() : definition.getTitle();
    }
}
