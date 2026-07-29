package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

class WordTemplateLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRepositoryReportTemplateFixture() throws Exception {
        Path fixture = java.nio.file.Paths.get(
                "src/test/resources/fixtures/templates/report-template.docx");

        try (XWPFDocument loaded = new WordTemplateLoader().load(fixture)) {
            assertThat(loaded.getParagraphs())
                    .anyMatch(paragraph -> paragraph.getText()
                            .contains("{{chart:centerEventChart}}"));
            assertThat(loaded.getTables()).isNotEmpty();
            assertThat(loaded.getDocument().getBody().isSetSectPr()).isTrue();
            assertThat(loaded.getDocument().getBody().getSectPr().isSetPgSz())
                    .isTrue();
            assertThat(loaded.getDocument().getBody().getSectPr().isSetPgMar())
                    .isTrue();
        }
    }

    @Test
    void loadsValidTemplateAndPreservesUntouchedContent() throws Exception {
        Path template = tempDir.resolve("template.docx");
        try (XWPFDocument document = validTemplate()) {
            document.createParagraph().createRun().setText("template footer marker");
            try (java.io.OutputStream output = Files.newOutputStream(template)) {
                document.write(output);
            }
        }

        try (XWPFDocument loaded = new WordTemplateLoader().load(template)) {
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(loaded)) {
                assertThat(extractor.getText()).contains("template footer marker");
            }
        }
    }

    @Test
    void rejectsTemplateWithoutRequiredHeadingStyle() throws Exception {
        Path template = tempDir.resolve("invalid.docx");
        try (XWPFDocument document = new XWPFDocument();
             java.io.OutputStream output = Files.newOutputStream(template)) {
            document.createParagraph().createRun().setText("{{sections}}");
            WordTocManagerTest.addComplexToc(document);
            document.write(output);
        }

        assertThatThrownBy(() -> new WordTemplateLoader().load(template))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining("Heading 1");
    }

    @Test
    void rejectsSectionsAnchorInsideTableBeforeGeneration() throws Exception {
        try (XWPFDocument document = validTemplate()) {
            XWPFParagraph anchor = document.getParagraphs().stream()
                    .filter(paragraph ->
                            "{{sections}}".equals(paragraph.getText()))
                    .findFirst().get();
            document.removeBodyElement(document.getPosOfParagraph(anchor));
            document.createTable(1, 1).getRow(0).getCell(0)
                    .setText("{{sections}}");

            assertThatThrownBy(() -> new WordTemplateLoader()
                    .validate(document))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("standalone top-level body");
        }
    }

    static XWPFDocument validTemplate() {
        XWPFDocument document = new XWPFDocument();
        for (int level = 1; level <= 4; level++) {
            String styleId = "Heading" + level;
            CTStyle ctStyle = CTStyle.Factory.newInstance();
            ctStyle.setStyleId(styleId);
            ctStyle.setType(STStyleType.PARAGRAPH);
            ctStyle.addNewName().setVal("Heading " + level);
            document.createStyles().addStyle(new XWPFStyle(ctStyle));
        }
        WordTocManagerTest.addComplexToc(document);
        document.createParagraph().createRun().setText("{{sections}}");
        return document;
    }
}
