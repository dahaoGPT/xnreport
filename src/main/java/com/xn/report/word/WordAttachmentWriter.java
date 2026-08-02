package com.xn.report.word;

import com.xn.report.config.definition.WordComponentDefinition;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class WordAttachmentWriter {

    static final String BOOKMARK_PREFIX = "_XN_ATTACHMENT_";
    private static final AtomicLong BOOKMARK_SEQUENCE = new AtomicLong();

    public void append(
            XWPFDocument document, WordComponentDefinition definition) {
        if (document == null || definition == null) {
            throw new IllegalArgumentException(
                    "Word document and attachment definition are required");
        }
        List<XWPFParagraph> paragraphs = new ArrayList<XWPFParagraph>();
        add(paragraphs, appendText(document, title(definition), true));
        add(paragraphs, appendText(
                document, definition.getDescription(), false));
        for (String item : definition.getItems()) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("ListBullet");
            paragraph.createRun().setText(item);
            paragraphs.add(paragraph);
        }
        mark(paragraphs);
    }

    void append(
            WordBodyInserter inserter, WordComponentDefinition definition) {
        if (inserter == null || definition == null) {
            throw new IllegalArgumentException(
                    "Word inserter and attachment definition are required");
        }
        List<XWPFParagraph> paragraphs = new ArrayList<XWPFParagraph>();
        add(paragraphs, appendText(inserter, title(definition), true));
        add(paragraphs, appendText(
                inserter, definition.getDescription(), false));
        for (String item : definition.getItems()) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            XWPFParagraph paragraph = inserter.paragraph();
            paragraph.setStyle("ListBullet");
            paragraph.createRun().setText(item);
            paragraphs.add(paragraph);
        }
        mark(paragraphs);
    }

    static XWPFParagraph appendText(
            XWPFDocument document, String text, boolean bold) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setBold(bold);
        paragraph.getRuns().get(0).setText(text);
        return paragraph;
    }

    private static XWPFParagraph appendText(
            WordBodyInserter inserter, String text, boolean bold) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        XWPFParagraph paragraph = inserter.paragraph();
        paragraph.createRun().setBold(bold);
        paragraph.getRuns().get(0).setText(text);
        return paragraph;
    }

    private static void add(
            List<XWPFParagraph> paragraphs, XWPFParagraph paragraph) {
        if (paragraph != null) {
            paragraphs.add(paragraph);
        }
    }

    private static void mark(List<XWPFParagraph> paragraphs) {
        if (paragraphs.isEmpty()) {
            return;
        }
        long value = BOOKMARK_SEQUENCE.incrementAndGet();
        BigInteger id = BigInteger.valueOf(value);
        XWPFParagraph first = paragraphs.get(0);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark start =
                first.getCTP().addNewBookmarkStart();
        start.setId(id);
        start.setName(BOOKMARK_PREFIX + Long.toString(value, 36));
        first.getCTP().addNewBookmarkEnd().setId(id);
    }

    private static String title(WordComponentDefinition definition) {
        return definition.getTitle() == null
                || definition.getTitle().trim().isEmpty()
                ? definition.getText() : definition.getTitle();
    }
}
