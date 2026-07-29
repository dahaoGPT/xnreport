package com.xn.report.word;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
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
