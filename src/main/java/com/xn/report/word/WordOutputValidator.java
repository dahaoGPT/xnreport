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
            validateResolved(document, text);
            validateCoverStructure(
                    document, expectation.getCoverValues());
            validateExactHeadings(document, expectation.getHeadings());
            validateTables(document, expectation.getTables(),
                    expectation.getHeadings());
            validateAttachmentStructure(
                    document, expectation.getAttachments());
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

    private static void validateResolved(
            XWPFDocument document, String text) {
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
        if (WordPackageTextScanner.contains(document, PLACEHOLDER)) {
            throw new WordTemplateException(
                    "Word output contains unresolved placeholders"
                            + " in a content control or package story");
        }
    }

    private static void validateCoverStructure(
            XWPFDocument document, List<String> expected) {
        if (expected.isEmpty()) {
            return;
        }
        if (expected.size() != 5) {
            throw new WordTemplateException(
                    "Word cover structure requires exactly five values");
        }
        List<String> coverBlocks = new ArrayList<String>();
        boolean boundaryFound = false;
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph
                    && isCoverBoundary((XWPFParagraph) element)) {
                boundaryFound = true;
                break;
            }
            if (element instanceof XWPFParagraph) {
                coverBlocks.add(((XWPFParagraph) element).getText());
            } else if (element instanceof XWPFTable) {
                appendTableParagraphs((XWPFTable) element, coverBlocks);
            }
        }
        if (!boundaryFound) {
            throw new WordTemplateException(
                    "Word cover structure cannot locate the TOC or"
                            + " first heading boundary");
        }
        int previous = -1;
        for (int field = 0; field < expected.size(); field++) {
            int found = -1;
            for (int index = previous + 1;
                    index < coverBlocks.size(); index++) {
                if (coverBlocks.get(index).contains(expected.get(field))) {
                    found = index;
                    break;
                }
            }
            if (found < 0) {
                throw new WordTemplateException(
                        "Word cover structure is missing, out of order,"
                                + " or outside the cover area");
            }
            previous = found;
        }
    }

    private static boolean isCoverBoundary(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        return (style != null && style.matches("Heading[1-4]"))
                || paragraph.getCTP().xmlText().matches("(?s).*TOC\\s+.*");
    }

    private static void appendTableParagraphs(
            XWPFTable table, List<String> values) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    values.add(paragraph.getText());
                }
            }
        }
    }

    private static void validateAttachmentStructure(
            XWPFDocument document,
            List<WordOutputExpectation.Attachment> expected) {
        validateAttachmentMarkers(document, expected.size());
        List<StyledText> actual = new ArrayList<StyledText>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int cursor = 0;
        for (WordOutputExpectation.Attachment attachment : expected) {
            List<StyledText> wanted = new ArrayList<StyledText>();
            if (attachment.getTitle() != null) {
                wanted.add(new StyledText(
                        "TITLE",
                        attachment.getTitle()));
            }
            if (attachment.getDescription() != null) {
                wanted.add(new StyledText(
                        "DESCRIPTION",
                        attachment.getDescription()));
            }
            for (String item : attachment.getItems()) {
                wanted.add(new StyledText(
                        "ITEM", item));
            }
            int start = findAttachmentStart(
                    paragraphs, cursor, wanted.get(0));
            if (start < 0 || start + wanted.size() > paragraphs.size()) {
                throw new WordTemplateException(
                        "Word attachment structure or configured order mismatch");
            }
            actual.clear();
            for (int offset = 0; offset < wanted.size(); offset++) {
                XWPFParagraph paragraph = paragraphs.get(start + offset);
                String role = offset == 0
                        && attachment.getTitle() != null
                        ? "TITLE"
                        : ("ListBullet".equals(paragraph.getStyle())
                        ? "ITEM" : "DESCRIPTION");
                actual.add(new StyledText(role, paragraph.getText()));
            }
            if (!actual.equals(wanted)) {
                throw new WordTemplateException(
                        "Word attachment structure or configured order mismatch");
            }
            cursor = start + wanted.size();
        }
    }

    private static void validateAttachmentMarkers(
            XWPFDocument document, int expectedCount) {
        int actualCount = 0;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark
                    start : paragraph.getCTP().getBookmarkStartList()) {
                if (start.getName() == null
                        || !start.getName().startsWith(
                        WordAttachmentWriter.BOOKMARK_PREFIX)) {
                    continue;
                }
                actualCount++;
                boolean closed = false;
                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange
                        end : paragraph.getCTP().getBookmarkEndList()) {
                    if (start.getId().equals(end.getId())) {
                        closed = true;
                        break;
                    }
                }
                if (!closed) {
                    throw new WordTemplateException(
                            "Word attachment bookmark is not closed");
                }
            }
        }
        if (actualCount != expectedCount) {
            throw new WordTemplateException(
                    "Word attachment structure marker count mismatch:"
                            + " extra or missing block");
        }
    }

    private static int findAttachmentStart(
            List<XWPFParagraph> paragraphs,
            int cursor,
            StyledText first) {
        for (int index = cursor; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            if (!first.text.equals(paragraph.getText())) {
                continue;
            }
            if ("TITLE".equals(first.style)) {
                boolean bold = !paragraph.getRuns().isEmpty()
                        && paragraph.getRuns().get(0).isBold();
                if (!bold) {
                    continue;
                }
            } else if ("ITEM".equals(first.style)
                    && !"ListBullet".equals(paragraph.getStyle())) {
                continue;
            }
            return index;
        }
        return -1;
    }

    private static final class StyledText {
        private final String style;
        private final String text;

        private StyledText(String style, String text) {
            this.style = style;
            this.text = text;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof StyledText)) {
                return false;
            }
            StyledText value = (StyledText) other;
            return style.equals(value.style) && text.equals(value.text);
        }

        @Override
        public int hashCode() {
            return 31 * style.hashCode() + text.hashCode();
        }
    }

    private static void validateExactHeadings(
            XWPFDocument document,
            List<WordOutputExpectation.Heading> expected) {
        List<XWPFParagraph> actual = new ArrayList<XWPFParagraph>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getStyle() != null
                    && paragraph.getStyle().matches("Heading[1-4]")
                    && hasDynamicNumbering(paragraph)) {
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
            List<WordOutputExpectation.Table> expected,
            List<WordOutputExpectation.Heading> headings) {
        List<XWPFTable> dynamicTables =
                dynamicTables(document, headings);
        if (dynamicTables.size() != expected.size()) {
            throw new WordTemplateException(
                    "Word dynamic table count does not match configuration");
        }
        for (WordOutputExpectation.Table table : expected) {
            if (table.getIndex() >= dynamicTables.size()) {
                throw new WordTemplateException(
                        "Word dynamic table index does not exist: "
                                + table.getIndex());
            }
            XWPFTable actual = dynamicTables.get(table.getIndex());
            if (table.getRowCount() >= 0
                    && actual.getNumberOfRows() != table.getRowCount()) {
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

    private static List<XWPFTable> dynamicTables(
            XWPFDocument document,
            List<WordOutputExpectation.Heading> headings) {
        List<XWPFTable> values = new ArrayList<XWPFTable>();
        if (headings.isEmpty()) {
            return values;
        }
        WordOutputExpectation.Heading first = headings.get(0);
        boolean insideDynamicSections = false;
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                if (!insideDynamicSections
                        && matchesDynamicHeading(paragraph, first)) {
                    insideDynamicSections = true;
                }
            } else if (insideDynamicSections
                    && element instanceof XWPFTable) {
                values.add((XWPFTable) element);
            }
        }
        return values;
    }

    private static boolean matchesDynamicHeading(
            XWPFParagraph paragraph,
            WordOutputExpectation.Heading heading) {
        if (!("Heading" + heading.getLevel())
                .equals(paragraph.getStyle())
                || !heading.getText().equals(paragraph.getText())
                || !hasDynamicNumbering(paragraph)) {
            return false;
        }
        BigInteger level = paragraph.getCTP().getPPr()
                .getNumPr().getIlvl().getVal();
        return BigInteger.valueOf(heading.getLevel() - 1L).equals(level);
    }

    private static boolean hasDynamicNumbering(
            XWPFParagraph paragraph) {
        return paragraph.getCTP().isSetPPr()
                && paragraph.getCTP().getPPr().isSetNumPr()
                && paragraph.getCTP().getPPr().getNumPr().isSetNumId()
                && paragraph.getCTP().getPPr().getNumPr().isSetIlvl();
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
