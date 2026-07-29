package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

class WordSectionAnchorLocatorTest {

    private final WordSectionAnchorLocator locator =
            new WordSectionAnchorLocator(new WordRunTextReplacer());

    @Test
    void locatesOneStandaloneTopLevelAnchorSplitAcrossRuns() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("{{sec");
            paragraph.createRun().setText("tions}}");

            assertThat(locator.locate(document)).isSameAs(paragraph);
        }
    }

    @Test
    void rejectsAnchorEmbeddedInTopLevelParagraph() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun()
                    .setText("prefix {{sections}} suffix");

            assertInvalid(document);
        }
    }

    @Test
    void rejectsAnchorInsideTable() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createTable(1, 1).getRow(0).getCell(0)
                    .setText("{{sections}}");

            assertInvalid(document);
        }
    }

    @Test
    void rejectsAnchorInsideHeader() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            new XWPFHeaderFooterPolicy(document).createHeader(
                    XWPFHeaderFooterPolicy.DEFAULT)
                    .createParagraph().createRun().setText("{{sections}}");

            assertInvalid(document);
        }
    }

    @Test
    void rejectsMultipleAnchorsEvenWhenOneIsValid() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("{{sections}}");
            document.createTable(1, 1).getRow(0).getCell(0)
                    .setText("{{sections}}");

            assertInvalid(document);
        }
    }

    private void assertInvalid(XWPFDocument document) {
        assertThatThrownBy(() -> locator.locate(document))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining(
                        "{{sections}} must appear exactly once as a standalone"
                                + " top-level body paragraph");
    }
}
