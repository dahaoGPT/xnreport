package com.xn.report.word;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.math.BigInteger;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

public final class WordOutputValidator {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{[^{}]+}}");
    private static final String[] COVER_TOKENS = {
        WordCoverBinder.REPORT_TITLE,
        WordCoverBinder.ORGANIZATION,
        WordCoverBinder.REPORT_PERIOD,
        WordCoverBinder.PREPARED_BY,
        WordCoverBinder.PREPARED_DATE
    };

    public void validate(
            Path output, WordOutputExpectation expectation) {
        if (expectation == null) {
            throw new IllegalArgumentException(
                    "Word output expectation is required");
        }
        if (output == null || !Files.isRegularFile(output)) {
            throw new WordTemplateException(
                    "Word output does not exist: " + output);
        }
        try (InputStream input = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(input)) {
            validateStyles(document);
            validateToc(document, expectation);
            String text = documentText(document);
            validateResolved(text);
            for (String value : expectation.getCoverValues()) {
                if (!text.contains(value)) {
                    throw new WordTemplateException(
                            "Word cover is missing expected value: " + value);
                }
            }
            validateExactHeadings(document, expectation.getHeadings());
            validateTables(document, expectation.getTables());
            validateSequence(
                    text, expectation.getAttachmentSequence(), "attachment");
            int pictures = validatePictures(document);
            if (pictures != expectation.getPictureInstances()) {
                throw new WordTemplateException(
                        "Word picture instance count does not match expected count");
            }
        } catch (IOException ex) {
            throw new WordTemplateException(
                    "Unable to reopen Word output " + output, ex);
        }
    }

    public void validate(
            Path output,
            int tocMaxLevel,
            List<String> expectedHeadings,
            int expectedPictures,
            int expectedTables) {
        if (output == null || !Files.isRegularFile(output)) {
            throw new WordTemplateException(
                    "Word output does not exist: " + output);
        }
        try (InputStream input = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(input)) {
            validateStyles(document);
            new WordTocManager().validate(document);
            if (!document.getSettings().getCTSettings().isSetUpdateFields()) {
                throw new WordTemplateException(
                        "Word output is missing updateFields=true");
            }
            if (!document.getDocument().xmlText()
                    .contains("1-" + tocMaxLevel)) {
                throw new WordTemplateException(
                        "Word TOC does not use configured max level "
                                + tocMaxLevel);
            }
            try (XWPFWordExtractor extractor =
                         new XWPFWordExtractor(document)) {
                if (PLACEHOLDER.matcher(extractor.getText()).find()) {
                    throw new WordTemplateException(
                            "Word output contains unresolved placeholders");
                }
            }
            validateHeadingOrder(document, expectedHeadings);
            if (pictureOccurrences(document) != expectedPictures) {
                throw new WordTemplateException(
                        "Word picture count does not match expected count");
            }
            if (document.getTables().size() != expectedTables) {
                throw new WordTemplateException(
                        "Word table count does not match expected count");
            }
        } catch (IOException ex) {
            throw new WordTemplateException(
                    "Unable to reopen Word output " + output, ex);
        }
    }

    private static int pictureOccurrences(IBody body) {
        int pictures = 0;
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                for (XWPFRun run : ((XWPFParagraph) element).getRuns()) {
                    pictures += run.getEmbeddedPictures().size();
                }
            } else if (element instanceof XWPFTable) {
                for (XWPFTableRow row : ((XWPFTable) element).getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        pictures += pictureOccurrences(cell);
                    }
                }
            }
        }
        return pictures;
    }

    private static void validateStyles(XWPFDocument document) {
        for (int level = 1; level <= 4; level++) {
            if (document.getStyles() == null
                    || document.getStyles().getStyle(
                    "Heading" + level) == null) {
                throw new WordTemplateException(
                        "Word output is missing Heading " + level);
            }
        }
    }

    private static void validateToc(
            XWPFDocument document, WordOutputExpectation expectation) {
        WordTocManager manager = new WordTocManager();
        String expected =
                WordTocManager.instruction(expectation.getTocMaxLevel()).trim();
        String actual = manager.configuredInstruction(document).trim();
        if (!expected.equals(actual)) {
            throw new WordTemplateException(
                    "Word TOC instruction does not match configured max level");
        }
        if (expectation.isRequireUpdateFields()) {
            String settings =
                    document.getSettings().getCTSettings().xmlText();
            if (!document.getSettings().getCTSettings().isSetUpdateFields()
                    || !settings.matches(
                    "(?s).*updateFields[^>]*val=\"(?:true|1|on)\".*")) {
                throw new WordTemplateException(
                        "Word output requires updateFields val=true");
            }
        }
    }

    private static String documentText(XWPFDocument document)
            throws IOException {
        // Closing the extractor also closes the document's shared OPCPackage.
        // The validator still needs it afterwards to inspect picture parts.
        return new XWPFWordExtractor(document).getText();
    }

    private static void validateResolved(String text) {
        for (String token : COVER_TOKENS) {
            if (text.contains(token)) {
                throw new WordTemplateException(
                        "Word cover contains unresolved token " + token);
            }
        }
        if (PLACEHOLDER.matcher(text).find()) {
            throw new WordTemplateException(
                    "Word output contains unresolved placeholders");
        }
    }

    private static void validateExactHeadings(
            XWPFDocument document,
            List<WordOutputExpectation.Heading> expected) {
        List<XWPFParagraph> actual = new ArrayList<XWPFParagraph>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getStyle() != null
                    && paragraph.getStyle().matches("Heading[1-4]")) {
                actual.add(paragraph);
            }
        }
        if (actual.size() != expected.size()) {
            throw new WordTemplateException(
                    "Word heading count does not match DFS expectation");
        }
        BigInteger commonNumId = null;
        for (int index = 0; index < expected.size(); index++) {
            WordOutputExpectation.Heading wanted = expected.get(index);
            XWPFParagraph paragraph = actual.get(index);
            if (!("Heading" + wanted.getLevel())
                    .equals(paragraph.getStyle())
                    || !wanted.getText().equals(paragraph.getText())) {
                throw new WordTemplateException(
                        "Word heading level or DFS order mismatch at index "
                                + index);
            }
            if (!paragraph.getCTP().isSetPPr()
                    || !paragraph.getCTP().getPPr().isSetNumPr()
                    || !paragraph.getCTP().getPPr().getNumPr().isSetNumId()
                    || !paragraph.getCTP().getPPr().getNumPr().isSetIlvl()) {
                throw new WordTemplateException(
                        "Word heading is missing multilevel numbering");
            }
            BigInteger numId = paragraph.getCTP().getPPr()
                    .getNumPr().getNumId().getVal();
            BigInteger level = paragraph.getCTP().getPPr()
                    .getNumPr().getIlvl().getVal();
            if (!BigInteger.valueOf(wanted.getLevel() - 1L).equals(level)) {
                throw new WordTemplateException(
                        "Word heading numbering ilvl does not match level");
            }
            if (commonNumId == null) {
                commonNumId = numId;
            } else if (!commonNumId.equals(numId)) {
                throw new WordTemplateException(
                        "Word headings do not reuse one multilevel numId");
            }
        }
    }

    private static void validateTables(
            XWPFDocument document,
            List<WordOutputExpectation.Table> expected) {
        for (WordOutputExpectation.Table table : expected) {
            if (table.getIndex() >= document.getTables().size()) {
                throw new WordTemplateException(
                        "Word dynamic table index does not exist: "
                                + table.getIndex());
            }
            XWPFTable actual = document.getTables().get(table.getIndex());
            if (actual.getNumberOfRows() != table.getRowCount()) {
                throw new WordTemplateException(
                        "Word dynamic table row count mismatch at index "
                                + table.getIndex());
            }
            for (String value : table.getExpectedValues()) {
                if (!actual.getText().contains(value)) {
                    throw new WordTemplateException(
                            "Word dynamic table is missing value: " + value);
                }
            }
        }
    }

    private static void validateSequence(
            String text, List<String> expected, String kind) {
        int cursor = -1;
        for (String value : expected) {
            int next = text.indexOf(value, cursor + 1);
            if (next < 0) {
                throw new WordTemplateException(
                        "Word " + kind + " sequence is missing: " + value);
            }
            cursor = next;
        }
    }

    private static int validatePictures(IBody body) throws IOException {
        int pictures = 0;
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                for (XWPFRun run : ((XWPFParagraph) element).getRuns()) {
                    for (XWPFPicture picture : run.getEmbeddedPictures()) {
                        if (picture.getPictureData() == null
                                || picture.getPictureData().getData().length == 0) {
                            throw new WordTemplateException(
                                    "Word drawing relation is unreadable or empty");
                        }
                        pictures++;
                    }
                }
            } else if (element instanceof XWPFTable) {
                for (XWPFTableRow row : ((XWPFTable) element).getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        pictures += validatePictures(cell);
                    }
                }
            }
        }
        return pictures;
    }

    private static void validateHeadingOrder(
            XWPFDocument document, List<String> expected) {
        List<String> actual = new ArrayList<String>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getStyle() != null
                    && paragraph.getStyle().startsWith("Heading")) {
                actual.add(paragraph.getText());
            }
        }
        int cursor = 0;
        for (String heading : expected == null
                ? java.util.Collections.<String>emptyList() : expected) {
            while (cursor < actual.size()
                    && !actual.get(cursor).contains(heading)) {
                cursor++;
            }
            if (cursor >= actual.size()) {
                throw new WordTemplateException(
                        "Word heading order is missing: " + heading);
            }
            cursor++;
        }
    }
}
